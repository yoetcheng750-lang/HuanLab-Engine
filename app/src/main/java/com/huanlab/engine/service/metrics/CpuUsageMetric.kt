package com.huanlab.engine.service.metrics

enum class CpuUsageSource(val label: String) {
    DUMPSYS_CPUINFO("dumpsys cpuinfo"),
    TOYBOX_TOP("Toybox top"),
    PROC_LOADAVG("/proc/loadavg"),
    SYSTEM_HEALTH_API("SystemHealthManager"),
    HARDWARE_PROPERTIES_API("HardwarePropertiesManager"),
    UNKNOWN("未知来源"),
}

/**
 * CPU 使用率模型。
 *
 * valuePercent = user + kernel（执行态占用），与系统总比例 systemTotalPercent 分开保存：
 * AOSP 的 TOTAL 前置总比例已包含 iowait/irq/softirq，不能与分类值再次相加。
 * valuePercent 为 null 表示没有可靠使用率，绝不能用 0 代替。
 */
data class CpuUsageMetric(
    val valuePercent: Double?,
    val userPercent: Double?,
    val kernelPercent: Double?,
    val systemTotalPercent: Double?,
    val iowaitPercent: Double?,
    val irqPercent: Double?,
    val softirqPercent: Double?,
    val loadAverage1m: Double?,
    val loadAverage5m: Double?,
    val loadAverage15m: Double?,
    val source: CpuUsageSource,
    val status: ProbeStatus,
    val reason: String?,
    val sampledAtMillis: Long,
    val sampleWindowMillis: Long?,
    val sampleAgeMillis: Long?,
) {
    val sourceLabel: String get() = source.label
}
