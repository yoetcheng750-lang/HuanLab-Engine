package com.huanlab.engine.service.metrics

/** CPU 使用率唯一合法来源。 */
enum class CpuUsageSource(val label: String) {
    DUMPSYS_CPUINFO("dumpsys cpuinfo"),
}

/**
 * CPU 使用率模型。
 *
 * valuePercent = null 且 status = UNKNOWN 表示没有可靠数据，UI 必须显示「—」；
 * 绝不允许用 0.0 代替读取失败。
 */
data class CpuUsageMetric(
    val valuePercent: Double?,
    val source: CpuUsageSource?,
    val status: ProbeStatus,
    val reason: String?,
    val sampleTimestamp: Long?,
    val sampleAgeMs: Long?,
) {
    companion object {
        fun valid(percent: Double, sampleAgeMs: Long?, now: Long): CpuUsageMetric =
            CpuUsageMetric(
                valuePercent = percent,
                source = CpuUsageSource.DUMPSYS_CPUINFO,
                status = ProbeStatus.PASS,
                reason = null,
                sampleTimestamp = now,
                sampleAgeMs = sampleAgeMs,
            )

        fun unknown(reason: String, now: Long = System.currentTimeMillis()): CpuUsageMetric =
            CpuUsageMetric(
                valuePercent = null,
                source = null,
                status = ProbeStatus.UNKNOWN,
                reason = reason,
                sampleTimestamp = now,
                sampleAgeMs = null,
            )
    }
}
