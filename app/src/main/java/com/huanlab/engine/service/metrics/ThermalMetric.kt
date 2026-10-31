package com.huanlab.engine.service.metrics

enum class SensorCategory(val label: String) {
    CPU("CPU"),
    GPU("GPU"),
    BATTERY("Battery"),
    SOC("SoC"),
    SKIN("Skin"),
    PMIC("PMIC"),
    BOARD("Board"),
    OTHER("Other"),
}

enum class ThermalSource(val label: String) {
    THERMAL_ZONE("thermal_zone"),
    HWMON("hwmon"),
    ANDROID_BATTERY_API("BatteryManager"),
    ANDROID_THERMAL_API("ThermalStatus（仅状态等级）"),
    UNKNOWN("未知来源"),
}

data class ThermalMetric(
    val zoneId: String,
    val rawType: String,
    val category: SensorCategory,
    val rawValue: Long?,
    val temperatureCelsius: Double?,
    val source: ThermalSource,
    val status: ProbeStatus,
    val reason: String?,
    val sampledAtMillis: Long,
)

data class ThermalSnapshot(
    val sensors: List<ThermalMetric>,
    val status: ProbeStatus,
    val reason: String?,
    val sampledAtMillis: Long,
)
