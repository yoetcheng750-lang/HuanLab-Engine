package com.huanlab.engine.service.metrics

/**
 * 温度解析：type 分类 + 毫摄氏度转换 + 合理性校验。
 * 只做纯文本处理，不接触系统调用；单位不明确时一律拒绝输出摄氏度。
 */
object ThermalParser {

    const val MIN_PLAUSIBLE_CELSIUS = -40.0
    const val MAX_PLAUSIBLE_CELSIUS = 150.0

    data class TempResult(val celsius: Double?, val status: ProbeStatus, val reason: String?)

    fun classify(rawType: String): SensorCategory {
        val normalized = rawType.trim().lowercase()
        if (normalized.isEmpty()) return SensorCategory.OTHER
        return when {
            containsToken(normalized, "gpu") -> SensorCategory.GPU
            containsToken(normalized, "batt") -> SensorCategory.BATTERY
            containsToken(normalized, "soc") -> SensorCategory.SOC
            containsToken(normalized, "cpu") -> SensorCategory.CPU
            containsToken(normalized, "skin") -> SensorCategory.SKIN
            containsToken(normalized, "pmic") -> SensorCategory.PMIC
            containsToken(normalized, "board") || containsToken(normalized, "pcb") -> SensorCategory.BOARD
            else -> SensorCategory.OTHER
        }
    }

    /**
     * 厂商前缀（如 mtktscpu）允许出现在 token 之前，因此只校验 token 之后是否为边界，
     * 避免把 "cpufreq"/"gpu_cpu" 误判的同时仍能识别 mtktscpu / mtktsgpu。
     */
    private fun containsToken(normalized: String, token: String): Boolean {
        var index = normalized.indexOf(token)
        while (index >= 0) {
            val after = normalized.getOrNull(index + token.length)
            val boundaryAfter = after == null || !after.isLetterOrDigit() || after == '_' || after == '-'
            if (boundaryAfter) return true
            index = normalized.indexOf(token, index + 1)
        }
        return false
    }

    fun parseMilliCelsius(raw: String?): TempResult {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return TempResult(null, ProbeStatus.UNKNOWN, "空温度数据")
        }
        val value = trimmed.toLongOrNull()
            ?: return TempResult(null, ProbeStatus.FAILED, "非数字温度: ${trimmed.take(40)}")
        if (value <= 0L) {
            return TempResult(null, ProbeStatus.UNKNOWN, "无效温度值: $value")
        }
        val celsius = value / 1000.0
        if (!isPlausible(celsius)) {
            return TempResult(null, ProbeStatus.UNKNOWN, "温度超出合理范围: ${"%.1f".format(celsius)}℃")
        }
        return TempResult(celsius, ProbeStatus.PASS, null)
    }

    fun isPlausible(celsius: Double): Boolean =
        celsius.isFinite() &&
            celsius >= MIN_PLAUSIBLE_CELSIUS &&
            celsius <= MAX_PLAUSIBLE_CELSIUS

    fun parseZoneList(raw: String?): List<String> =
        raw?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.startsWith("thermal_zone") }
            ?.distinct()
            ?.sortedBy { it.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            ?.toList()
            ?: emptyList()
}
