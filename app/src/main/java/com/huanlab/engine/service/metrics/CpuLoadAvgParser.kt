package com.huanlab.engine.service.metrics

/**
 * /proc/loadavg 解析。
 *
 * Load Average 不是 CPU 使用率：它统计运行队列 + 不可中断 I/O 等待任务。
 * 本解析器只产出独立的负载指标，绝不换算成 CPU 百分比。
 */
object CpuLoadAvgParser {

    data class LoadAvg(
        val oneMinute: Double?,
        val fiveMinutes: Double?,
        val fifteenMinutes: Double?,
        val runningTasks: Int?,
        val totalTasks: Int?,
    )

    fun parse(output: String?): LoadAvg? {
        val parts = output?.trim()?.split(Regex("\\s+")).orEmpty()
        if (parts.size < 3) return null
        val one = parts[0].toDoubleOrNull()
        val five = parts[1].toDoubleOrNull()
        val fifteen = parts[2].toDoubleOrNull()
        if (one == null || five == null || fifteen == null) return null
        if (!one.isFinite() || !five.isFinite() || !fifteen.isFinite()) return null
        if (one < 0 || five < 0 || fifteen < 0) return null

        var running: Int? = null
        var total: Int? = null
        parts.getOrNull(3)?.split("/")?.let { ratio ->
            if (ratio.size == 2) {
                running = ratio[0].toIntOrNull()
                total = ratio[1].toIntOrNull()
            }
        }
        return LoadAvg(one, five, fifteen, running, total)
    }
}
