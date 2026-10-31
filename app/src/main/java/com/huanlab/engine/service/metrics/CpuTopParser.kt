package com.huanlab.engine.service.metrics

/**
 * Toybox top 系统汇总行解析（备用数据源）。
 *
 * 只解析整机汇总行（包含 %cpu 与 %idle 的行），绝不把进程 %CPU 相加冒充系统使用率。
 * 由于目标设备通道无法执行 top（策略拦截），格式兼容性标记 UNVERIFIED。
 */
object CpuTopParser {

    data class ParseResult(
        val userPercent: Double?,
        val systemPercent: Double?,
        val idlePercent: Double?,
        val ioPercent: Double?,
        val irqPercent: Double?,
        val totalCapacityPercent: Double?,
        val status: ProbeStatus,
        val reason: String?,
    )

    private val summaryLine = Regex(""".*(\d+(?:\.\d+)?)%\s*cpu.*""")
    private val fieldPattern = Regex("""(\d+(?:\.\d+)?)%\s*([a-zA-Z]+)""")

    fun parse(output: String?): ParseResult {
        val text = output?.trim().orEmpty()
        if (text.isEmpty()) {
            return ParseResult(null, null, null, null, null, null, ProbeStatus.UNKNOWN, "top 无输出")
        }
        val lines = text.lineSequence().filter { it.contains("%cpu") || it.contains("% idle") }.toList()
        if (lines.isEmpty()) {
            return ParseResult(
                null, null, null, null, null, null,
                ProbeStatus.UNSUPPORTED,
                "top 输出中没有整机 CPU 汇总行（可能只有进程列表）",
            )
        }
        val line = lines.last()
        val fields = HashMap<String, Double>()
        fieldPattern.findAll(line).forEach { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            fields[match.groupValues[2].lowercase()] = value
        }
        val user = fields["user"] ?: fields["usr"]
        val system = fields["sys"] ?: fields["system"] ?: fields["kernel"]
        val idle = fields["idle"]
        val io = fields["iow"] ?: fields["iowait"] ?: fields["io"]
        val irq = fields["irq"]
        val capacity = fields["cpu"]

        if (user == null || system == null) {
            return ParseResult(
                user, system, idle, io, irq, capacity,
                ProbeStatus.UNKNOWN,
                "CPU 汇总字段缺失（需要 user 与 sys）",
            )
        }
        if (!user.isFinite() || !system.isFinite() || user < 0 || system < 0) {
            return ParseResult(null, null, idle, io, irq, capacity, ProbeStatus.FAILED, "百分比数值非法")
        }
        return ParseResult(
            userPercent = user,
            systemPercent = system,
            idlePercent = idle,
            ioPercent = io,
            irqPercent = irq,
            totalCapacityPercent = capacity,
            status = ProbeStatus.DEGRADED,
            reason = "Toybox top 输出（格式未在目标设备验证）",
        )
    }

    /** 按总容量归一化为 0-100；无法确定容量时返回 null，不做猜测。 */
    fun normalize(user: Double?, system: Double?, capacity: Double?): Double? {
        if (user == null || system == null) return null
        val sum = user + system
        if (!sum.isFinite() || sum < 0) return null
        return when {
            capacity != null && capacity > 0 && sum <= capacity -> (sum / capacity) * 100.0
            sum <= 100.0 -> sum
            else -> null
        }
    }
}
