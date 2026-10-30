package com.huanlab.engine.capability

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * 依赖前置条件 U3：调用方必须在 manifest 中配置 ShizukuProvider 与包可见性 queries。
 * 在该前置条件成立时，NameNotFoundException 才被视为“未安装”。
 */
class ShizukuInstallProbe(private val context: Context) {
    suspend fun probe(): ProbeOutcome = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                CapabilityConstants.SHIZUKU_PACKAGE_NAME,
                0,
            )
            ProbeOutcome.Satisfied(
                ProbeId.SHIZUKU_INSTALLED,
                ProbeEvidence(
                    ProbeId.SHIZUKU_INSTALLED,
                    "PackageManager.getPackageInfo",
                    null,
                    "installed=true versionName=${info.versionName ?: "UNKNOWN"}",
                ),
            )
        } catch (notFound: PackageManager.NameNotFoundException) {
            ProbeOutcome.NotSatisfied(
                ProbeId.SHIZUKU_INSTALLED,
                "SHIZUKU_MANAGER_NOT_INSTALLED（前置条件 U3：queries 已配置；否则可能是可见性误判）",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            ProbeOutcome.Unreadable(
                ProbeId.SHIZUKU_INSTALLED,
                "PACKAGE_LOOKUP_FAILED: ${throwable.javaClass.simpleName}",
            )
        }
    }
}

class ShizukuBinderProbe(
    private val eventSource: ShizukuEventSource = DefaultShizukuEventSource,
) {
    suspend fun probe(): ProbeOutcome = withContext(Dispatchers.IO) {
        try {
            val alive = withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                eventSource.pingBinder()
            }
            if (alive) {
                ProbeOutcome.Satisfied(
                    ProbeId.SHIZUKU_BINDER,
                    ProbeEvidence(
                        ProbeId.SHIZUKU_BINDER,
                        "Shizuku.pingBinder()",
                        null,
                        "binder=alive",
                    ),
                )
            } else {
                ProbeOutcome.NotSatisfied(
                    ProbeId.SHIZUKU_BINDER,
                    "SHIZUKU_BINDER_ABSENT",
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            ProbeOutcome.Unreadable(
                ProbeId.SHIZUKU_BINDER,
                "BINDER_PING_TIMEOUT(${CapabilityConstants.BINDER_PING_TIMEOUT_MS}ms)",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            ProbeOutcome.Unreadable(
                ProbeId.SHIZUKU_BINDER,
                "BINDER_PING_FAILED: ${throwable.javaClass.simpleName}",
            )
        }
    }
}

sealed interface ShizukuVersionResult {
    data class Supported(
        val apiVersion: Int,
        val evidence: ProbeEvidence,
    ) : ShizukuVersionResult

    data class TooOld(
        val requiredVersion: Int,
        val actualVersion: Int,
        val evidence: ProbeEvidence,
    ) : ShizukuVersionResult

    data class Unreadable(val reason: String) : ShizukuVersionResult {
        init {
            require(reason.isNotBlank()) { "Unreadable.reason must not be blank" }
        }
    }
}

class ShizukuVersionProbe {
    suspend fun probe(): ShizukuVersionResult = withContext(Dispatchers.IO) {
        try {
            val version = withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                Shizuku.getVersion()
            }

            // P0 修复：getVersion() 返回 -1 不是“版本过低”，而是未授权/服务不可用，
            // 必须按 UNKNOWN 处理，禁止误判为 Incompatible。
            if (version < 0) {
                return@withContext ShizukuVersionResult.Unreadable(
                    "SHIZUKU_VERSION_UNAVAILABLE_OR_UNAUTHORIZED: Shizuku.getVersion()=$version",
                )
            }

            val evidence = ProbeEvidence(
                ProbeId.SHIZUKU_VERSION,
                "Shizuku.getVersion()",
                null,
                "actual=$version required>=${CapabilityConstants.SHIZUKU_MIN_API_VERSION}",
            )
            if (version >= CapabilityConstants.SHIZUKU_MIN_API_VERSION) {
                ShizukuVersionResult.Supported(version, evidence)
            } else {
                ShizukuVersionResult.TooOld(
                    CapabilityConstants.SHIZUKU_MIN_API_VERSION,
                    version,
                    evidence,
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            ShizukuVersionResult.Unreadable(
                "SHIZUKU_VERSION_TIMEOUT(${CapabilityConstants.BINDER_PING_TIMEOUT_MS}ms)",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            ShizukuVersionResult.Unreadable(
                "SHIZUKU_VERSION_UNREADABLE: ${throwable.javaClass.simpleName}",
            )
        }
    }
}

class ShizukuAuthorizationProbe {
    suspend fun probe(): ProbeOutcome = withContext(Dispatchers.IO) {
        try {
            val granted = withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                Shizuku.checkSelfPermission()
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                ProbeOutcome.Satisfied(
                    ProbeId.SHIZUKU_AUTHORIZATION,
                    ProbeEvidence(
                        ProbeId.SHIZUKU_AUTHORIZATION,
                        "Shizuku.checkSelfPermission()",
                        null,
                        "granted=true",
                    ),
                )
            } else {
                ProbeOutcome.NotSatisfied(
                    ProbeId.SHIZUKU_AUTHORIZATION,
                    "SHIZUKU_AUTHORIZATION_NOT_GRANTED（U12 UNVERIFIED：无法区分未申请/已拒绝）",
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            ProbeOutcome.Unreadable(
                ProbeId.SHIZUKU_AUTHORIZATION,
                "SHIZUKU_AUTHORIZATION_TIMEOUT(${CapabilityConstants.BINDER_PING_TIMEOUT_MS}ms)",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            ProbeOutcome.Unreadable(
                ProbeId.SHIZUKU_AUTHORIZATION,
                "SHIZUKU_AUTHORIZATION_UNREADABLE: ${throwable.javaClass.simpleName}",
            )
        }
    }
}
