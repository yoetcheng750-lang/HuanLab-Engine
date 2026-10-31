package com.huanlab.engine.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport

data class ManagedApp(
    val packageName: String,
    val label: String,
    val protection: ProtectionReason?,
)

enum class AppLockState {
    UNLOCKED,
    LOCKED,
    PENDING,
    RESTORING,
    FAILED,
    DRIFTED,
}

data class LockOutcome(
    val packageName: String,
    val state: AppLockState,
    val message: String,
)

/**
 * 后台应用管控：独立的系统执行模块，不修改 capability/ 或 shared/ 中的核心实现。
 *
 * 安全网关顺序：自身 -> Shizuku -> 系统标记 -> 动态系统角色（桌面/拨号/输入法/无障碍/VPN/闹钟）。
 * 任一命中即拒绝出现在可控列表，也拒绝执行锁定。
 */
class AppControlManager(
    context: Context,
    private val engine: Engine,
    private val protectedApps: ProtectedApps = ProtectedApps(context),
) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val store = AppControlStore(appContext)

    fun listUserApps(): List<ManagedApp> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .mapNotNull { info ->
                val reason = protectedApps.reasonFor(info.packageName, info)
                if (reason != null) return@mapNotNull null
                ManagedApp(
                    packageName = info.packageName,
                    label = runCatching {
                        packageManager.getApplicationLabel(info).toString()
                    }.getOrDefault(info.packageName),
                    protection = null,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun stateOf(packageName: String): AppLockState {
        val locked = store.lockedPackages()
        if (packageName !in locked) return AppLockState.UNLOCKED
        return if (packageName in store.pendingPackages()) AppLockState.PENDING else AppLockState.LOCKED
    }

    fun lockedCount(): Int = store.lockedPackages().size

    fun pendingCount(): Int = store.pendingPackages().size

    fun originalState(packageName: String): OriginalState? = store.originalState(packageName)

    suspend fun setLocked(packageName: String, locked: Boolean): LockOutcome {
        if (!PACKAGE_PATTERN.matches(packageName)) {
            return LockOutcome(packageName, AppLockState.FAILED, "包名非法，拒绝执行")
        }
        val info = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()
        if (info != null) {
            val reason = protectedApps.reasonFor(packageName, info)
            if (reason != null) {
                return LockOutcome(packageName, AppLockState.FAILED, "受保护应用（${reason.label}），拒绝执行")
            }
        }
        return if (locked) lock(packageName) else unlock(packageName)
    }

    private suspend fun lock(packageName: String): LockOutcome {
        val original = readOriginalState(packageName)
        val report = engine.execute(lockCommands(packageName))
        return when (report) {
            is ExecutionReport.Executed -> {
                if (report.allOk) {
                    store.recordLocked(packageName, original)
                    LockOutcome(packageName, AppLockState.LOCKED, "已锁定")
                } else {
                    store.recordPending(packageName)
                    LockOutcome(
                        packageName,
                        AppLockState.FAILED,
                        "部分命令失败：" + report.results.filter { !it.ok }
                            .joinToString("；") { it.command + " -> " + it.shortMessage() },
                    )
                }
            }
            is ExecutionReport.Rejected -> {
                store.recordPending(packageName)
                LockOutcome(packageName, AppLockState.PENDING, "等待 Shizuku：${report.summary}")
            }
        }
    }

    private suspend fun unlock(packageName: String): LockOutcome {
        val original = store.originalState(packageName)
        val runInBackground = original?.runInBackground ?: DEFAULT_APPOP
        val wakeLock = original?.wakeLock ?: DEFAULT_APPOP
        val bucket = original?.standbyBucket ?: DEFAULT_STANDBY_BUCKET
        val report = engine.execute(
            listOf(
                listOf("appops", "set", packageName, "RUN_IN_BACKGROUND", runInBackground),
                listOf("appops", "set", packageName, "WAKE_LOCK", wakeLock),
                listOf("am", "set-standby-bucket", packageName, bucket.toString()),
            ),
        )
        return when (report) {
            is ExecutionReport.Executed -> {
                if (report.allOk) {
                    store.recordUnlocked(packageName)
                    LockOutcome(packageName, AppLockState.UNLOCKED, "已恢复")
                } else {
                    val note = if (original == null) "（未记录到原值，已按默认值恢复）" else ""
                    LockOutcome(
                        packageName,
                        AppLockState.LOCKED,
                        "恢复部分失败$note：" + report.results.filter { !it.ok }
                            .joinToString("；") { it.command + " -> " + it.shortMessage() },
                    )
                }
            }
            is ExecutionReport.Rejected ->
                LockOutcome(packageName, AppLockState.PENDING, "等待 Shizuku：${report.summary}")
        }
    }

    /** 读取实际 AppOps 值，判断记录状态与系统状态是否一致。 */
    suspend fun verify(packageName: String): AppLockState {
        val intended = stateOf(packageName)
        if (intended == AppLockState.UNLOCKED) return AppLockState.UNLOCKED
        val report = engine.execute(
            listOf(
                listOf("appops", "get", packageName, "RUN_IN_BACKGROUND"),
                listOf("appops", "get", packageName, "WAKE_LOCK"),
                listOf("am", "get-standby-bucket", packageName),
            ),
        )
        if (report !is ExecutionReport.Executed) return AppLockState.PENDING
        val runInBackground = parseAppOp(report.results.getOrNull(0)?.stdout)
        val wakeLock = parseAppOp(report.results.getOrNull(1)?.stdout)
        val bucket = report.results.getOrNull(2)?.stdout?.trim()?.toIntOrNull()
        val consistent = runInBackground == "ignore" && wakeLock == "ignore" && bucket == RESTRICTED_BUCKET
        return when {
            consistent -> AppLockState.LOCKED
            intended == AppLockState.PENDING -> AppLockState.PENDING
            else -> AppLockState.DRIFTED
        }
    }

    /** 启动 / Shizuku 重连后，对处于待恢复与已锁定的应用重放锁定命令。 */
    suspend fun reconcilePending(): List<LockOutcome> {
        val targets = store.pendingPackages() + store.lockedPackages()
        if (targets.isEmpty()) return emptyList()
        return targets.sorted().map { packageName ->
            val report = engine.execute(lockCommands(packageName))
            when (report) {
                is ExecutionReport.Executed -> if (report.allOk) {
                    store.recordLocked(packageName, store.originalState(packageName))
                    LockOutcome(packageName, AppLockState.LOCKED, "已恢复锁定")
                } else {
                    LockOutcome(packageName, AppLockState.FAILED, "重放失败")
                }
                is ExecutionReport.Rejected ->
                    LockOutcome(packageName, AppLockState.PENDING, "仍等待 Shizuku")
            }
        }
    }

    /** 一键恢复：解除所有记录在案的锁定，并按原始 AppOps 还原。 */
    suspend fun restoreAll(): List<LockOutcome> {
        val targets = store.lockedPackages().sorted()
        if (targets.isEmpty()) return emptyList()
        return targets.map { packageName ->
            store.recordPending(packageName)
            unlock(packageName)
        }
    }

    private fun lockCommands(packageName: String): List<List<String>> = listOf(
        listOf("appops", "set", packageName, "RUN_IN_BACKGROUND", "ignore"),
        listOf("appops", "set", packageName, "WAKE_LOCK", "ignore"),
        listOf("am", "set-standby-bucket", packageName, "restricted"),
    )

    private suspend fun readOriginalState(packageName: String): OriginalState? {
        val report = engine.execute(
            listOf(
                listOf("appops", "get", packageName, "RUN_IN_BACKGROUND"),
                listOf("appops", "get", packageName, "WAKE_LOCK"),
                listOf("am", "get-standby-bucket", packageName),
            ),
        )
        if (report !is ExecutionReport.Executed) return null
        val results = report.results
        return OriginalState(
            runInBackground = parseAppOp(results.getOrNull(0)?.stdout),
            wakeLock = parseAppOp(results.getOrNull(1)?.stdout),
            standbyBucket = results.getOrNull(2)?.stdout?.trim()?.toIntOrNull(),
        )
    }

    private fun parseAppOp(output: String?): String? {
        if (output.isNullOrBlank()) return null
        return APPOP_PATTERN.find(output)?.groupValues?.getOrNull(1)
    }

    private companion object {
        val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        val APPOP_PATTERN = Regex(":\\s*(allow|ignore|deny|default|foreground|ask)\\s*;")
        const val DEFAULT_APPOP = "default"
        const val DEFAULT_STANDBY_BUCKET = 10
        const val RESTRICTED_BUCKET = 45
    }
}
