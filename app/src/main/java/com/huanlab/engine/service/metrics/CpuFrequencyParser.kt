package com.huanlab.engine.service.metrics

/**
 * CPU 频率解析：只做纯文本 → 数值转换与合理性判断，不接触系统调用。
 *
 * 规则：
 *  - 只在数值为正且在合理范围内时返回 PASS；
 *  - 空 / 非数字 / 负数 / 零 / 超范围 → null + 具体原因；
 *  - 绝不把解析失败转成 0。
 */
object CpuFrequencyParser {

    const val MIN_VALID_KHZ = 50_000L
    const val MAX_VALID_KHZ = 10_000_000L

    data class KhzResult(val khz: Long?, val status: ProbeStatus, val reason: String?)

    fun parseKhz(raw: String?): KhzResult {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return KhzResult(null, ProbeStatus.UNKNOWN, "空输出")
        }
        val value = trimmed.toLongOrNull()
            ?: return KhzResult(null, ProbeStatus.FAILED, "非数字输出: ${trimmed.take(40)}")
        if (value <= 0L) {
            return KhzResult(null, ProbeStatus.UNKNOWN, "无效数值: $value")
        }
        if (value < MIN_VALID_KHZ || value > MAX_VALID_KHZ) {
            return KhzResult(null, ProbeStatus.UNKNOWN, "超出合理频率范围: ${value}kHz")
        }
        return KhzResult(value, ProbeStatus.PASS, null)
    }

    fun toMhz(khz: Long): Double = khz / 1000.0

    fun toGhz(mhz: Double): Double = mhz / 1000.0

    fun parseCpuList(raw: String?): List<Int> =
        raw?.trim()
            ?.split(Regex("[\\s,]+"))
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 0..255 }
            ?.distinct()
            ?.sorted()
            ?: emptyList()

    fun parsePolicyList(raw: String?): List<String> =
        raw?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.startsWith("policy") && it.removePrefix("policy").toIntOrNull() != null }
            ?.distinct()
            ?.sortedBy { it.removePrefix("policy").toIntOrNull() ?: 0 }
            ?.toList()
            ?: emptyList()

    /** 读取结果分类：区分不存在、权限拒绝与其他失败。 */
    fun classifyRead(ok: Boolean, stderr: String?): ProbeStatus? {
        if (ok) return null
        val message = stderr.orEmpty().lowercase()
        return when {
            message.contains("permission denied") -> ProbeStatus.DEGRADED
            message.contains("no such file") -> ProbeStatus.UNSUPPORTED
            message.contains("timed out") || message.contains("timeout") -> ProbeStatus.UNKNOWN
            else -> ProbeStatus.FAILED
        }
    }

    fun reasonFor(status: ProbeStatus, detail: String): String = when (status) {
        ProbeStatus.DEGRADED -> "系统权限限制：$detail"
        ProbeStatus.UNSUPPORTED -> "设备未提供该节点：$detail"
        ProbeStatus.UNKNOWN -> "数据不可用：$detail"
        ProbeStatus.FAILED -> "读取失败：$detail"
        ProbeStatus.PASS -> detail
    }
}
