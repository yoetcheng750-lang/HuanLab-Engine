package com.huanlab.engine.service.metrics

enum class CpuFreqSource(val label: String) {
    CPUINFO_CUR_FREQ("cpuinfo_cur_freq（硬件反馈）"),
    CPUINFO_AVG_FREQ("cpuinfo_avg_freq（硬件平均）"),
    SCALING_CUR_FREQ("scaling_cur_freq（内核报告）"),
    UNKNOWN("未知来源"),
}

data class CpuFrequencyMetric(
    val policyId: String,
    val affectedCpus: List<Int>,
    val frequencyKhz: Long?,
    val frequencyMhz: Double?,
    val source: CpuFreqSource,
    val status: ProbeStatus,
    val reason: String?,
    val sampledAtMillis: Long,
) {
    val frequencyGhz: Double? get() = frequencyMhz?.div(1000.0)

    val label: String
        get() = when (source) {
            CpuFreqSource.CPUINFO_CUR_FREQ, CpuFreqSource.CPUINFO_AVG_FREQ -> "CPU 当前频率（硬件反馈）"
            CpuFreqSource.SCALING_CUR_FREQ -> "CPU 频率（内核报告）"
            CpuFreqSource.UNKNOWN -> "CPU 频率"
        }
}
