package com.huanlab.engine.capability

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * P0 修复：弃用 newProcess("id")，改用官方 API Shizuku.getUid()。
 * 0 -> ROOT_UID_0；2000 -> SHELL_UID_2000；其余值一律 Unresolved，绝不猜测。
 * 读取失败/超时/API 异常同样返回 Unresolved。
 *
 * UNVERIFIED：Shizuku.getUid() 的具体返回值域与授权前提未在本机核对。
 */
class IdentityProbe {
    suspend fun resolve(): IdentityResolution = withContext(Dispatchers.IO) {
        try {
            val uid = withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                Shizuku.getUid()
            }
            when (uid) {
                0 -> IdentityResolution.Resolved(
                    kind = IdentityKind.ROOT_UID_0,
                    uid = uid,
                    idOutput = "Shizuku.getUid()=$uid",
                )
                2000 -> IdentityResolution.Resolved(
                    kind = IdentityKind.SHELL_UID_2000,
                    uid = uid,
                    idOutput = "Shizuku.getUid()=$uid",
                )
                else -> IdentityResolution.Unresolved(
                    "IDENTITY_UID_UNRECOGNIZED: Shizuku.getUid()=$uid",
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            IdentityResolution.Unresolved(
                "IDENTITY_GET_UID_TIMEOUT(${CapabilityConstants.BINDER_PING_TIMEOUT_MS}ms)",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            IdentityResolution.Unresolved(
                "IDENTITY_GET_UID_FAILED: ${throwable.javaClass.simpleName}",
            )
        }
    }
}
