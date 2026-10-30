package com.huanlab.engine.shared

enum class ThermalLevel {
    NORMAL,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    UNKNOWN;

    companion object {
        /** status 为 getCurrentThermalStatus() 原始值；null 表示读取失败；0..6 之外一律 UNKNOWN。 */
        fun fromStatus(status: Int?): ThermalLevel = when (status) {
            0 -> NORMAL
            1 -> LIGHT
            2 -> MODERATE
            3 -> SEVERE
            4, 5, 6 -> CRITICAL
            else -> UNKNOWN
        }
    }
}
