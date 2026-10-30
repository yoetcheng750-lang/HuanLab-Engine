package com.huanlab.engine.capability

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.huanlab.engine.shared.Capability
import com.huanlab.engine.shared.CheckPoint
import com.huanlab.engine.shared.SkipReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.UUID

/**
 * 阶段 B 门面：只做只读探测与判定，不执行动作、不写 TransactionState。
 * ENQUEUE 与 PRE_EXECUTE 各自构建独立快照（Q9），PRE_EXECUTE 全部新鲜读取。
 */
class PermissionCapabilityManager(
    private val probes: CapabilityProbes,
    private val monitor: ShizukuBinderMonitor,
    private val attestationStore: ManualCheckAttestationStore,
    private val timeSource: TimeSource,
) {
    private val manualProjectionCodes = setOf(
        DecisionCode.MANUAL_NOT_ALLOWED,
        DecisionCode.MANUAL_ATTESTATION_MISSING,
    )

    fun start() {
        monitor.start()
    }

    fun close() {
        monitor.close()
    }

    fun addEpochObserver(observer: EpochObserver) {
        monitor.addEpochObserver(observer)
    }

    fun removeEpochObserver(observer: EpochObserver) {
        monitor.removeEpochObserver(observer)
    }

    suspend fun retryShizukuDetection() {
        monitor.retry()
    }

    suspend fun attestManualCheck(itemId: ManualCheckItemId): ManualCheckAttestation {
        ManualCheckRegistry.requireAttestable(itemId)
        return attestationStore.record(itemId, timeSource.elapsedRealtimeMillis())
    }

    suspend fun revokeManualCheck(itemId: ManualCheckItemId) {
        attestationStore.revoke(itemId)
    }

    suspend fun isValidManualCheck(itemId: ManualCheckItemId): Boolean =
        attestationStore.isValid(itemId, timeSource.elapsedRealtimeMillis())

    suspend fun checkForEnqueue(requirement: CapabilityRequirement): EnqueueResult {
        val createdAt = timeSource.elapsedRealtimeMillis()
        val monitorSnapshot = monitor.currentSnapshot()
        val outcomes = linkedMapOf<ProbeId, ProbeOutcome>()
        val pendingManual = linkedSetOf<ManualCheckItemId>()
        var failure: GateFailure? = null

        if (requirement.needsPrivilegedChannel) {
            failure = evaluateMonitorForEnqueue(requirement, monitorSnapshot.state)
        }

        if (failure == null) {
            for (probeId in requirement.requiredProbes) {
                val outcome = probes.runConditionProbe(probeId)
                outcomes[probeId] = outcome
                failure = when (outcome) {
                    is ProbeOutcome.Satisfied -> null
                    is ProbeOutcome.NotSatisfied -> GateFailure(
                        DecisionCode.PROBE_NOT_SATISFIED,
                        "${probeId.name}: ${outcome.reason}",
                        null,
                    )
                    is ProbeOutcome.Unreadable -> GateFailure(
                        DecisionCode.PROBE_UNREADABLE,
                        "${probeId.name}: ${outcome.reason}",
                        null,
                    )
                    is ProbeOutcome.ManualRequired -> {
                        val descriptor = ProbeRegistry.descriptor(probeId)
                        when {
                            descriptor.readability == ProbeReadability.DIRECT -> GateFailure(
                                DecisionCode.PROBE_MISCONFIGURED,
                                "$probeId 返回人工核对但声明为 DIRECT",
                                outcome.itemId,
                            )
                            !requirement.allowManualAttestation -> GateFailure(
                                DecisionCode.MANUAL_NOT_ALLOWED,
                                "$probeId 需要人工核对，但动作未允许人工自证",
                                outcome.itemId,
                            )
                            else -> {
                                pendingManual += outcome.itemId
                                null
                            }
                        }
                    }
                }
                if (failure != null) break
            }
        }

        if (failure != null) {
            return EnqueueResult.Rejected(buildRejection(CheckPoint.ENQUEUE, failure))
        }

        val snapshot = CapabilitySnapshot(
            snapshotId = UUID.randomUUID().toString(),
            checkPoint = CheckPoint.ENQUEUE,
            takenAtElapsedMillis = createdAt,
            epoch = monitorSnapshot.epoch,
            monitorState = monitorSnapshot.state,
            identity = (monitorSnapshot.state as? ShizukuMonitorState.Ready)?.identity,
            probeOutcomes = outcomes.toMap(),
            attestedManualItems = emptySet(),
        )
        return EnqueueResult.Allowed(requirement, pendingManual.toSet(), snapshot)
    }

    suspend fun checkForPreExecute(requirement: CapabilityRequirement): PreExecuteResult {
        val startedAtEpoch = monitor.currentEpoch()
        val createdAt = timeSource.elapsedRealtimeMillis()
        val outcomes = linkedMapOf<ProbeId, ProbeOutcome>()
        val attested = linkedSetOf<ManualCheckItemId>()
        var failure: GateFailure? = null
        var identity: IdentityResolution.Resolved? = null

        if (requirement.needsPrivilegedChannel) {
            when (val baseline = runFreshBaseline(requirement)) {
                is BaselineOutcome.Pass -> {
                    identity = baseline.identity
                    if (
                        requirement.expectedIdentity.isNotEmpty() &&
                        baseline.identity.kind !in requirement.expectedIdentity
                    ) {
                        failure = GateFailure(
                            DecisionCode.IDENTITY_MISMATCH,
                            "期望 ${requirement.expectedIdentity.map { it.name }}，" +
                                "实际 ${baseline.identity.kind.name}",
                            null,
                        )
                    }
                }
                is BaselineOutcome.Fail -> failure = baseline.failure
            }
        }

        if (failure == null) {
            for (probeId in requirement.requiredProbes) {
                val outcome = probes.runConditionProbe(probeId)
                outcomes[probeId] = outcome
                failure = when (outcome) {
                    is ProbeOutcome.Satisfied -> null
                    is ProbeOutcome.NotSatisfied -> GateFailure(
                        DecisionCode.PROBE_NOT_SATISFIED,
                        "${probeId.name}: ${outcome.reason}",
                        null,
                    )
                    is ProbeOutcome.Unreadable -> GateFailure(
                        DecisionCode.PROBE_UNREADABLE,
                        "${probeId.name}: ${outcome.reason}",
                        null,
                    )
                    is ProbeOutcome.ManualRequired -> {
                        val descriptor = ProbeRegistry.descriptor(probeId)
                        val now = timeSource.elapsedRealtimeMillis()
                        when {
                            descriptor.readability == ProbeReadability.DIRECT -> GateFailure(
                                DecisionCode.PROBE_MISCONFIGURED,
                                "$probeId 返回人工核对但声明为 DIRECT",
                                outcome.itemId,
                            )
                            !requirement.allowManualAttestation -> GateFailure(
                                DecisionCode.MANUAL_NOT_ALLOWED,
                                "$probeId 需要人工核对，但动作未允许人工自证",
                                outcome.itemId,
                            )
                            !attestationStore.isValid(outcome.itemId, now) -> GateFailure(
                                DecisionCode.MANUAL_ATTESTATION_MISSING,
                                "人工核对项 ${outcome.itemId.name} 没有有效自证",
                                outcome.itemId,
                            )
                            else -> {
                                attested += outcome.itemId
                                null
                            }
                        }
                    }
                }
                if (failure != null) break
            }
        }

        val endedAtEpoch = monitor.currentEpoch()
        if (
            failure == null &&
            requirement.needsPrivilegedChannel &&
            endedAtEpoch != startedAtEpoch
        ) {
            failure = GateFailure(
                DecisionCode.EPOCH_CHANGED,
                "检查期间 Shizuku 世代从 $startedAtEpoch 变为 $endedAtEpoch",
                null,
            )
        }

        if (failure != null) {
            return PreExecuteResult.Rejected(
                buildRejection(CheckPoint.PRE_EXECUTE, failure),
            )
        }

        val monitorSnapshot = monitor.currentSnapshot()
        val snapshot = CapabilitySnapshot(
            snapshotId = UUID.randomUUID().toString(),
            checkPoint = CheckPoint.PRE_EXECUTE,
            takenAtElapsedMillis = createdAt,
            epoch = endedAtEpoch,
            monitorState = monitorSnapshot.state,
            identity = identity,
            probeOutcomes = outcomes.toMap(),
            attestedManualItems = attested.toSet(),
        )
        val grant = ValidatedGrant(
            requirement = requirement,
            epoch = endedAtEpoch,
            identity = identity,
            snapshotId = snapshot.snapshotId,
            issuedAtElapsedMillis = timeSource.elapsedRealtimeMillis(),
            ttlMillis = CapabilityConstants.GRANT_TTL_MS,
            nonce = UUID.randomUUID().toString(),
            snapshot = snapshot,
        )
        return PreExecuteResult.Allowed(grant)
    }

    private suspend fun evaluateMonitorForEnqueue(
        requirement: CapabilityRequirement,
        state: ShizukuMonitorState,
    ): GateFailure? = when (state) {
        is ShizukuMonitorState.Ready -> {
            if (
                requirement.expectedIdentity.isNotEmpty() &&
                state.identity.kind !in requirement.expectedIdentity
            ) {
                GateFailure(
                    DecisionCode.IDENTITY_MISMATCH,
                    "期望 ${requirement.expectedIdentity.map { it.name }}，" +
                        "实际 ${state.identity.kind.name}",
                    null,
                )
            } else {
                null
            }
        }
        is ShizukuMonitorState.BinderAbsent -> refineBinderAbsence("Shizuku binder 未出现")
        is ShizukuMonitorState.NotAuthorized -> GateFailure(
            DecisionCode.SHIZUKU_NOT_AUTHORIZED,
            state.reason,
            null,
        )
        is ShizukuMonitorState.Incompatible -> GateFailure(
            DecisionCode.SHIZUKU_INCOMPATIBLE,
            "Shizuku API ${state.actualVersion} < required ${state.requiredVersion}",
            null,
        )
        is ShizukuMonitorState.Dead -> GateFailure(
            DecisionCode.MONITOR_NOT_READY,
            "Shizuku binder 已断开",
            null,
        )
        is ShizukuMonitorState.WaitingBinder -> GateFailure(
            DecisionCode.MONITOR_NOT_READY,
            "等待 Shizuku binder 恢复中",
            null,
        )
        is ShizukuMonitorState.NeedsManual -> GateFailure(
            DecisionCode.MONITOR_NOT_READY,
            state.reason,
            null,
        )
        is ShizukuMonitorState.Unknown -> GateFailure(
            DecisionCode.SHIZUKU_UNAVAILABLE,
            state.reason,
            null,
        )
        is ShizukuMonitorState.Uninitialized,
        is ShizukuMonitorState.ConnectedUnverified,
        is ShizukuMonitorState.Revalidating -> GateFailure(
            DecisionCode.MONITOR_NOT_READY,
            "Shizuku 状态尚未验证完成（入队阶段 fail-closed）",
            null,
        )
    }

    private suspend fun refineBinderAbsence(detail: String): GateFailure {
        return when (val install = probes.probeInstallation()) {
            is ProbeOutcome.Satisfied -> GateFailure(
                DecisionCode.SHIZUKU_NOT_RUNNING,
                "$detail；管理器已安装但服务未运行",
                null,
            )
            is ProbeOutcome.NotSatisfied -> GateFailure(
                DecisionCode.SHIZUKU_NOT_INSTALLED,
                "Shizuku 管理器未安装",
                null,
            )
            is ProbeOutcome.Unreadable -> GateFailure(
                DecisionCode.SHIZUKU_UNAVAILABLE,
                "$detail；安装状态不可判定: ${install.reason}",
                null,
            )
            is ProbeOutcome.ManualRequired -> GateFailure(
                DecisionCode.SHIZUKU_UNAVAILABLE,
                "$detail；安装状态返回人工核对项",
                install.itemId,
            )
        }
    }

    private suspend fun runFreshBaseline(
        requirement: CapabilityRequirement,
    ): BaselineOutcome {
        when (val binder = probes.probeBinder()) {
            is ProbeOutcome.Satisfied -> Unit
            is ProbeOutcome.NotSatisfied -> {
                val install = probes.probeInstallation()
                return BaselineOutcome.Fail(
                    when (install) {
                        is ProbeOutcome.Satisfied -> GateFailure(
                            DecisionCode.SHIZUKU_NOT_RUNNING,
                            "binder 不可达，但 Shizuku 管理器已安装",
                            null,
                        )
                        is ProbeOutcome.NotSatisfied -> GateFailure(
                            DecisionCode.SHIZUKU_NOT_INSTALLED,
                            "Shizuku 管理器未安装",
                            null,
                        )
                        is ProbeOutcome.Unreadable -> GateFailure(
                            DecisionCode.SHIZUKU_UNAVAILABLE,
                            "binder 不可达且安装状态不可判定: ${install.reason}",
                            null,
                        )
                        is ProbeOutcome.ManualRequired -> GateFailure(
                            DecisionCode.SHIZUKU_UNAVAILABLE,
                            "安装状态返回人工核对项",
                            install.itemId,
                        )
                    },
                )
            }
            is ProbeOutcome.Unreadable -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "binder 探测失败: ${binder.reason}",
                    null,
                ),
            )
            is ProbeOutcome.ManualRequired -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "binder 探测返回人工核对项: ${binder.reason}",
                    binder.itemId,
                ),
            )
        }

        when (val version = probes.probeVersion()) {
            is ShizukuVersionResult.Supported -> Unit
            is ShizukuVersionResult.TooOld -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_INCOMPATIBLE,
                    "Shizuku API ${version.actualVersion} < required ${version.requiredVersion}",
                    null,
                ),
            )
            is ShizukuVersionResult.Unreadable -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "Shizuku 版本不可读: ${version.reason}",
                    null,
                ),
            )
        }

        when (val authorization = probes.probeAuthorization()) {
            is ProbeOutcome.Satisfied -> Unit
            is ProbeOutcome.NotSatisfied -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_NOT_AUTHORIZED,
                    authorization.reason,
                    null,
                ),
            )
            is ProbeOutcome.Unreadable -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "授权状态不可读: ${authorization.reason}",
                    null,
                ),
            )
            is ProbeOutcome.ManualRequired -> return BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "授权状态返回人工核对项: ${authorization.reason}",
                    null,
                ),
            )
        }

        return when (val identity = probes.resolveIdentity()) {
            is IdentityResolution.Unresolved -> BaselineOutcome.Fail(
                GateFailure(
                    DecisionCode.IDENTITY_UNRESOLVED,
                    identity.reason,
                    null,
                ),
            )
            is IdentityResolution.Resolved -> {
                val permissionFailure = checkRemotePermissions(requirement)
                if (permissionFailure != null) {
                    BaselineOutcome.Fail(permissionFailure)
                } else {
                    BaselineOutcome.Pass(identity)
                }
            }
        }
    }

    /**
     * P0 修复：Shizuku 远端权限不足时不能继续执行，
     * 以 ROOT_ONLY 拒绝，提示该能力只能通过 root 通道完成。
     */
    private suspend fun checkRemotePermissions(
        requirement: CapabilityRequirement,
    ): GateFailure? {
        for (permission in requirement.requiredRemotePermissions) {
            val granted = try {
                withContext(Dispatchers.IO) {
                    withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                        Shizuku.checkRemotePermission(permission)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                return GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "REMOTE_PERMISSION_TIMEOUT: $permission",
                    null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                return GateFailure(
                    DecisionCode.SHIZUKU_UNAVAILABLE,
                    "REMOTE_PERMISSION_CHECK_FAILED($permission): " +
                        throwable.javaClass.simpleName,
                    null,
                )
            }

            if (granted != PackageManager.PERMISSION_GRANTED) {
                return GateFailure(
                    DecisionCode.ROOT_ONLY,
                    "Shizuku 远端权限不足: $permission；该能力只能通过 ROOT 通道执行",
                    null,
                )
            }
        }
        return null
    }

    private fun buildRejection(
        checkPoint: CheckPoint,
        failure: GateFailure,
    ): Rejection {
        val reason = if (failure.code in manualProjectionCodes) {
            "UNKNOWN_MANUAL_CHECK_REQUIRED: " +
                "${failure.manualItem?.name ?: "UNKNOWN_ITEM"}: ${failure.detail}"
        } else {
            "${failure.code.name}: ${failure.detail}"
        }
        val capability = Capability.Unavailable(reason)
        return Rejection(
            capability = capability,
            code = failure.code,
            skipReason = SkipReason(checkPoint, reason),
            manualItem = failure.manualItem,
        )
    }

    private sealed interface BaselineOutcome {
        data class Pass(val identity: IdentityResolution.Resolved) : BaselineOutcome
        data class Fail(val failure: GateFailure) : BaselineOutcome
    }

    private data class GateFailure(
        val code: DecisionCode,
        val detail: String,
        val manualItem: ManualCheckItemId?,
    ) {
        init {
            require(detail.isNotBlank()) { "GateFailure.detail must not be blank" }
        }
    }

    companion object {
        /**
         * 默认装配。create 返回后调用方必须显式调用 start()。
         * 测试可注入 ProbeChannel / ShizukuEventSource / TimeSource 的伪造实现。
         */
        fun create(
            context: Context,
            scope: CoroutineScope,
            probeChannel: ProbeChannel = ShizukuProcessProbeChannel(),
            eventSource: ShizukuEventSource = DefaultShizukuEventSource,
            timeSource: TimeSource = SystemTimeSource,
        ): PermissionCapabilityManager {
            val probes = CapabilityProbes(
                context.applicationContext,
                probeChannel,
                eventSource,
            )
            val monitor = ShizukuBinderMonitor(
                scope = scope,
                revalidator = DefaultShizukuStateRevalidator(probes),
                timeSource = timeSource,
                eventSource = eventSource,
            )
            val store = InMemoryManualCheckAttestationStore { Build.FINGERPRINT }
            return PermissionCapabilityManager(probes, monitor, store, timeSource)
        }
    }
}
