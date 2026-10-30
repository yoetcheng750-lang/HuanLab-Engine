package com.huanlab.engine.capability

import com.huanlab.engine.shared.Capability
import com.huanlab.engine.shared.CheckPoint
import com.huanlab.engine.shared.SkipReason
import java.util.concurrent.atomic.AtomicBoolean

enum class DecisionCode {
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_NOT_AUTHORIZED,
    SHIZUKU_INCOMPATIBLE,
    SHIZUKU_UNAVAILABLE,
    MONITOR_NOT_READY,
    IDENTITY_MISMATCH,
    IDENTITY_UNRESOLVED,
    PROBE_NOT_SATISFIED,
    PROBE_UNREADABLE,
    PROBE_MISCONFIGURED,
    MANUAL_NOT_ALLOWED,
    MANUAL_ATTESTATION_MISSING,
    EPOCH_CHANGED,
    ROOT_ONLY,
}

data class Rejection(
    val capability: Capability.Unavailable,
    val code: DecisionCode,
    val skipReason: SkipReason,
    val manualItem: ManualCheckItemId? = null,
)

sealed interface EnqueueResult {
    data class Allowed(
        val requirement: CapabilityRequirement,
        val pendingManualItems: Set<ManualCheckItemId>,
        val snapshot: CapabilitySnapshot,
    ) : EnqueueResult

    data class Rejected(val rejection: Rejection) : EnqueueResult
}

sealed interface PreExecuteResult {
    data class Allowed(val grant: ValidatedGrant) : PreExecuteResult
    data class Rejected(val rejection: Rejection) : PreExecuteResult
}

enum class GrantConsumption {
    CONSUMED,
    ALREADY_CONSUMED,
    EXPIRED,
}

/**
 * P0 修复：一次性消费机制。
 * 通过 AtomicBoolean 保证同一个 grant 实例只能成功消费一次；过期返回 EXPIRED。
 * 注意：刻意不使用 data class，避免 copy() 重置消费状态。
 */
class ValidatedGrant(
    val requirement: CapabilityRequirement,
    val epoch: Long,
    val identity: IdentityResolution.Resolved?,
    val snapshotId: String,
    val issuedAtElapsedMillis: Long,
    val ttlMillis: Long,
    val nonce: String,
    val snapshot: CapabilitySnapshot,
) {
    private val consumed = AtomicBoolean(false)

    init {
        require(ttlMillis > 0L) { "ttlMillis must be > 0" }
        require(nonce.isNotBlank()) { "nonce must not be blank" }
        if (requirement.needsPrivilegedChannel) {
            require(identity != null) { "privileged grant requires resolved identity" }
        }
    }

    fun isWithinTtl(nowElapsedMillis: Long): Boolean =
        nowElapsedMillis >= issuedAtElapsedMillis &&
            nowElapsedMillis - issuedAtElapsedMillis <= ttlMillis

    fun isConsumed(): Boolean = consumed.get()

    /**
     * 执行前必须调用。只有返回 CONSUMED 的那一次才允许产生副作用。
     */
    fun tryConsume(nowElapsedMillis: Long): GrantConsumption {
        if (!isWithinTtl(nowElapsedMillis)) {
            return GrantConsumption.EXPIRED
        }
        return if (consumed.compareAndSet(false, true)) {
            GrantConsumption.CONSUMED
        } else {
            GrantConsumption.ALREADY_CONSUMED
        }
    }
}
