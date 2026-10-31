package com.huanlab.engine.action

/**
 * 结构化命令构造器：每条命令都是固定 argv，参数只允许数值或已知枚举，
 * 调用方无法拼接任意 shell 文本。
 */
object EngineActions {

    /** 游戏性能模式：固定性能模式 + 关闭省电 + 压缩动画时长。 */
    fun performanceMode(enabled: Boolean): List<List<String>> = if (enabled) {
        listOf(
            listOf("cmd", "power", "set-fixed-performance-mode-enabled", "true"),
            listOf("settings", "put", "global", "low_power", "0"),
            listOf("settings", "put", "global", "window_animation_scale", "0.5"),
            listOf("settings", "put", "global", "transition_animation_scale", "0.5"),
            listOf("settings", "put", "global", "animator_duration_scale", "0.5"),
        )
    } else {
        listOf(
            listOf("cmd", "power", "set-fixed-performance-mode-enabled", "false"),
            listOf("settings", "put", "global", "window_animation_scale", "1.0"),
            listOf("settings", "put", "global", "transition_animation_scale", "1.0"),
            listOf("settings", "put", "global", "animator_duration_scale", "1.0"),
        )
    }

    /** 高刷：peak 与 min 同时下发，保证系统不会回落。 */
    fun refreshRate(hz: Int): List<List<String>> {
        val clamped = hz.coerceIn(MIN_HZ, MAX_HZ)
        return listOf(
            listOf("settings", "put", "system", "peak_refresh_rate", clamped.toString()),
            listOf("settings", "put", "system", "min_refresh_rate", clamped.toString()),
        )
    }

    /** 把本应用加入 Doze 白名单，避免后台被冻结。 */
    fun deviceIdleWhitelist(packageName: String): List<List<String>> = listOf(
        listOf("dumpsys", "deviceidle", "whitelist", "+$packageName"),
    )

    /** 只读查询当前刷新率设置，用于 UI 回显。 */
    fun readRefreshRate(): List<List<String>> = listOf(
        listOf("settings", "get", "system", "peak_refresh_rate"),
    )

    const val MIN_HZ = 60
    const val MAX_HZ = 165
}
