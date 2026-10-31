package com.huanlab.engine.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport

data class ManagedApp(
    val packageName: String,
    val label: String,
)

enum class AppLockState {
    UNLOCKED,
    LOCKED,
    PENDING,
    FAILED,
}

data class LockOutcome(
    val packageName: String,
    val state: AppLockState,
    val message: String,
)

/**
 * 后台应用管控：独立的系统执行模块，不修改 capability/ 或 shared/ 中的核心实现。
 *
 * 设计要点：
 * 1. 只处理普通用户应用（排除 FLAG_SYSTEM 与 FLAG_UPDATED_SYSTEM_APP）；
 * 2. 锁定前先读取 AppOps / standby bucket 原值并持久化，解锁时按原值恢复；
 * 3. Shizuku 不可用时记录为 PENDING，不伪装成功；
 * 4. 所有命令走 Engine 的 ENQUEUE -> PRE_EXECUTE -> 一次性授权链路。
 */
class AppControlManager(
    context: Context,
    private val engine: Engine,
) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val store = AppControlStore(appContext)

    fun listUserApps(): List<ManagedApp> {
        val self = appContext.packageName
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { info ->
                val flags = info.flags
                val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                !isSystem && !isUpdatedSystem && info.packageName != self
            }
            .map { info ->
                ManagedApp(
                    packageName = info.packageName,
                    label = runCatching {
                        packageManager.getApplicationLabel(info).toString()
                    }.getOrDefault(info.packageName),
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

    fun originalState(packageName: String): OriginalState? = store.originalState(packageName)

    suspend fun setLocked(packageName: String, locked: Boolean): LockOutcome {
        if (!PACKAGE_PATTERN.matches(packageName)) {
            return LockOutcome(packageName, AppLockState.FAILED, "包名非法，拒绝执行")
        }
        return if (locked) lock(packageName) else unlock(packageName)
    }

    private suspend fun lock(packageName: String): LockOutcome {
        val original = readOriginalState(packageName)
        val report = engine.execute(
            listOf(
                listOf("appops", "set", packageName, "RUN_IN_BACKGROUND", "ignore"),
                listOf("appops", "set", packageName, "WAKE_LOCK", "ignore"),
                listOf("am", "set-standby-bucket", packageName, "restricted"),
            ),
        )
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
                    val restoreNote = if (original == null) "（未记录到原值，已按默认值恢复）" else ""
                    LockOutcome(
                        packageName,
                        AppLockState.LOCKED,
                        "恢复部分失败$restoreNote：" + report.results.filter { !it.ok }
                            .joinToString("；") { it.command + " -> " + it.shortMessage() },
                    )
                }
            }
            is ExecutionReport.Rejected ->
                LockOutcome(packageName, AppLockState.PENDING, "等待 Shizuku：${report.summary}")
        }
    }

    /** 启动 / Shizuku 重连后，对处于待恢复与已锁定的应用重放锁定命令。 */
    suspend fun reconcilePending(): List<LockOutcome> {
        val targets = store.pendingPackages() + store.lockedPackages()
        if (targets.isEmpty()) return emptyList()
        return targets.sorted().map { packageName ->
            val report = engine.execute(
                listOf(
                    listOf("appops", "set", packageName, "RUN_IN_BACKGROUND", "ignore"),
                    listOf("appops", "set", packageName, "WAKE_LOCK", "ignore"),
                    listOf("am", "set-standby-bucket", packageName, "restricted"),
                ),
            )
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

    private suspend fun readOriginalState(packageName: String): OriginalState? {
        val appOpsReport = engine.execute(
            listOf(
                listOf("appops", "get", packageName, "RUN_IN_BACKGROUND"),
                listOf("appops", "get", packageName, "WAKE_LOCK"),
                listOf("am", "get-standby-bucket", packageName),
            ),
        )
        if (appOpsReport !is ExecutionReport.Executed) return null
        val results = appOpsReport.results
        val runInBackground = parseAppOp(results.getOrNull(0)?.stdout)
        val wakeLock = parseAppOp(results.getOrNull(1)?.stdout)
        val bucket = results.getOrNull(2)?.stdout?.trim()?.toIntOrNull()
        return OriginalState(runInBackground, wakeLock, bucket)
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
    }
}
