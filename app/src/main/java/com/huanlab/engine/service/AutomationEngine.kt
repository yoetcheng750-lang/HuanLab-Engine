package com.huanlab.engine.service

import android.os.SystemClock
import com.huanlab.engine.action.EngineActions
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import com.huanlab.engine.shared.ThermalLevel

data class AutomationOutcome(
    val active: Boolean,
    val foreground: Metric<String>,
    val profileLabel: String?,
    val targetPerformance: Boolean?,
    val targetRefreshRate: Int?,
    val reason: String,
    val executed: Boolean,
    val verified: Boolean?,
    val message: String,
)

/**
 * 自动调度状态机。
 *
 * 优先级：热安全保护 > 用户总开关 > 应用专属方案 > 默认均衡。
 * 防抖：同一目标 30 秒内不重复下发；只有目标变化时才产生命令。
 * 验证：刷新率下发后回读系统设置，不一致则标记 verified=false。
 */
class AutomationEngine(
    private val engine: Engine,
    private val monitor: DeviceMonitor,
    private val profiles: AppProfileStore,
    private val foreground: ForegroundAppDetector,
    private val settings: AutomationSettingsStore,
    private val log: StrategyLogStore,
) {

    private var lastTarget: Target? = null
    private var lastExecutedAt = 0L

    suspend fun tick(): AutomationOutcome {
        if (!settings.enabled) {
            return AutomationOutcome(
                active = false,
                foreground = Metric.Unknown("自动化已关闭"),
                profileLabel = null,
                targetPerformance = null,
                targetRefreshRate = null,
                reason = "USER_DISABLED",
                executed = false,
                verified = null,
                message = "自动调度已关闭，所有策略需手动触发",
            )
        }

        val snapshot = monitor.sample()
        val thermal = snapshot.thermal.valueOrNull()
        if (thermal == ThermalLevel.CRITICAL || thermal == ThermalLevel.SEVERE) {
            val target = Target(performance = false, refreshRate = SAFE_REFRESH_RATE)
            return applyTarget(
                target = target,
                reason = "THERMAL_GUARD(${thermal.name})",
                foreground = foreground.currentForeground(),
                profileLabel = null,
                force = true,
            )
        }

        val fg = foreground.currentForeground()
        if (fg is Metric.Unknown) {
            return AutomationOutcome(
                active = true,
                foreground = fg,
                profileLabel = null,
                targetPerformance = null,
                targetRefreshRate = null,
                reason = "NO_FOREGROUND_INFO",
                executed = false,
                verified = null,
                message = "无法识别前台应用：${fg.reason}",
            )
        }

        val packageName = fg.valueOrNull() ?: return AutomationOutcome(
            active = true,
            foreground = fg,
            profileLabel = null,
            targetPerformance = null,
            targetRefreshRate = null,
            reason = "NO_FOREGROUND_INFO",
            executed = false,
            verified = null,
            message = "无法识别前台应用",
        )
        val profile = profiles.get(packageName)?.takeIf { it.enabled }
        val target = if (profile != null) {
            Target(profile.performanceMode, profile.refreshRate)
        } else {
            Target(performance = false, refreshRate = null)
        }
        return applyTarget(
            target = target,
            reason = if (profile != null) "PROFILE_MATCH(${profile.label})" else "FALLBACK_BALANCED",
            foreground = fg,
            profileLabel = profile?.label,
            force = false,
        )
    }

    fun resetDebounce() {
        lastTarget = null
        lastExecutedAt = 0L
    }

    private suspend fun applyTarget(
        target: Target,
        reason: String,
        foreground: Metric<String>,
        profileLabel: String?,
        force: Boolean,
    ): AutomationOutcome {
        val now = SystemClock.elapsedRealtime()
        if (!force && target == lastTarget && now - lastExecutedAt < DEBOUNCE_MS) {
            return AutomationOutcome(
                active = true,
                foreground = foreground,
                profileLabel = profileLabel,
                targetPerformance = target.performance,
                targetRefreshRate = target.refreshRate,
                reason = "DEBOUNCED($reason)",
                executed = false,
                verified = null,
                message = "目标未变化，30 秒内不重复下发",
            )
        }

        val commands = ArrayList<List<String>>()
        val previous = lastTarget
        if (previous == null || previous.performance != target.performance) {
            commands += EngineActions.performanceMode(target.performance)
        }
        if (target.refreshRate != null &&
            (previous == null || previous.refreshRate != target.refreshRate)
        ) {
            commands += EngineActions.refreshRate(target.refreshRate)
        }

        if (commands.isEmpty()) {
            return AutomationOutcome(
                active = true,
                foreground = foreground,
                profileLabel = profileLabel,
                targetPerformance = target.performance,
                targetRefreshRate = target.refreshRate,
                reason = "NO_CHANGE($reason)",
                executed = false,
                verified = null,
                message = "目标与上次一致，未产生新命令",
            )
        }

        log.record("AUTO", "目标：性能=${target.performance} 刷新率=${target.refreshRate ?: "不变"}，原因：$reason")
        val report = engine.execute(commands)
        val executed = report is ExecutionReport.Executed
        val allOk = executed && (report as ExecutionReport.Executed).allOk
        val verified = if (executed && target.refreshRate != null) {
            verifyRefreshRate(target.refreshRate)
        } else {
            null
        }

        val message = when (report) {
            is ExecutionReport.Executed ->
                if (report.allOk) "已下发 ${report.results.size} 条命令" else "部分命令失败：" + report.summary
            is ExecutionReport.Rejected -> "未执行：${report.summary}"
        }

        lastTarget = target
        lastExecutedAt = now
        log.record(
            "AUTO",
            "结果：$message" + if (verified != null) "，刷新率回读=${if (verified) "一致" else "不一致"}" else "",
            success = allOk && (verified != false),
        )
        return AutomationOutcome(
            active = true,
            foreground = foreground,
            profileLabel = profileLabel,
            targetPerformance = target.performance,
            targetRefreshRate = target.refreshRate,
            reason = reason,
            executed = true,
            verified = verified,
            message = message,
        )
    }

    private suspend fun verifyRefreshRate(expected: Int): Boolean? {
        val report = engine.execute(
            listOf(listOf("settings", "get", "system", "peak_refresh_rate")),
        )
        if (report !is ExecutionReport.Executed || report.results.firstOrNull()?.ok != true) {
            return null
        }
        val actual = report.results.first().stdout.trim().toFloatOrNull() ?: return null
        return kotlin.math.abs(actual - expected.toFloat()) < 0.5f
    }

    private data class Target(val performance: Boolean, val refreshRate: Int?)

    private companion object {
        const val DEBOUNCE_MS = 30_000L
        const val SAFE_REFRESH_RATE = 60
    }
}
