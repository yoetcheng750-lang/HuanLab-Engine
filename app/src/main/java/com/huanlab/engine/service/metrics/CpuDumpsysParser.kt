package com.huanlab.engine.service.metrics

/**
 * dumpsys cpuinfo 解析：只识别唯一 TOTAL 汇总行与采样时间，不解析任何进程行。
 *
 * AOSP 格式：
 *   CPU usage from 13477ms to 6413ms ago (2026-10-31 18:08:24.661 to 2026-10-31 18:08:31.726) with 99% awake:
 *   ...
 *   65% TOTAL: 31% user + 26% kernel + 1% iowait + 5.3% irq + 0.6% softirq
 */
object CpuDumpsysParser {

    const val FRESH_AGE_MS = 15_000L

    data class ParseResult(
        val metric: CpuUsageMetric?,
        val status: ProbeStatus,
        val reason: String?,
    )

    private val totalPattern = Regex("""(\d+(?:\.\d+)?)%\s+TOTAL:\s*(.+)$""")
    private val usageFromPattern = Regex("""CPU usage from (\d+)ms to (\d+)ms ago""")
    private val absoluteTimePattern = Regex(
        """\((\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?) to (\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?)\)""",
    )
    private val categoryPattern = Regex("""(\d+(?:\.\d+)?)%\s+(user|kernel|iowait|irq|softirq)""")

    fun parse(output: String?, nowMillis: Long = System.currentTimeMillis()): ParseResult {
        val text = output?.trim().orEmpty()
        if (text.isEmpty()) {
            return ParseResult(null, ProbeStatus.UNKNOWN, "dumpsys cpuinfo 无输出")
        }
        val totalLines = text.lineSequence().filter { it.contains("TOTAL:") }.toList()
        if (totalLines.isEmpty()) {
            return ParseResult(null, ProbeStatus.UNSUPPORTED, "输出中没有 TOTAL 汇总行")
        }
        if (totalLines.size > 1) {
            return ParseResult(null, ProbeStatus.UNKNOWN, "存在 ${totalLines.size} 个 TOTAL 行，结果冲突")
        }

        val totalLine = totalLines.first().trim()
        val totalMatch = totalPattern.find(totalLine)
            ?: return ParseResult(null, ProbeStatus.FAILED, "TOTAL 行格式不支持: ${totalLine.take(80)}")
        val systemTotal = totalMatch.groupValues[1].toDoubleOrNull()
            ?: return ParseResult(null, ProbeStatus.FAILED, "系统总比例解析失败")
        val categoriesRaw = totalMatch.groupValues[2]

        val categories = HashMap<String, Double>()
        categoryPattern.findAll(categoriesRaw).forEach { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            categories[match.groupValues[2]] = value
        }
        val user = categories["user"]
        val kernel = categories["kernel"]
        if (user == null || kernel == null) {
            return ParseResult(null, ProbeStatus.UNKNOWN, "TOTAL 行缺少 user 或 kernel 字段")
        }
        if (!isFinitePercent(user) || !isFinitePercent(kernel) || !isFinitePercent(systemTotal)) {
            return ParseResult(null, ProbeStatus.FAILED, "百分比数值非法")
        }

        val windowInfo = parseWindow(text, nowMillis)
        val value = (user + kernel).coerceIn(0.0, 100.0)
        val freshness = when {
            windowInfo.ageMillis == null -> ProbeStatus.DEGRADED to "无法确认采样时间"
            windowInfo.ageMillis > FRESH_AGE_MS -> ProbeStatus.UNKNOWN to
                "采样已过期（${windowInfo.ageMillis}ms 前）"
            else -> ProbeStatus.PASS to null
        }

        val metric = CpuUsageMetric(
            valuePercent = value,
            userPercent = user,
            kernelPercent = kernel,
            systemTotalPercent = systemTotal,
            iowaitPercent = categories["iowait"],
            irqPercent = categories["irq"],
            softirqPercent = categories["softirq"],
            loadAverage1m = null,
            loadAverage5m = null,
            loadAverage15m = null,
            source = CpuUsageSource.DUMPSYS_CPUINFO,
            status = freshness.first,
            reason = freshness.second,
            sampledAtMillis = nowMillis,
            sampleWindowMillis = windowInfo.windowMillis,
            sampleAgeMillis = windowInfo.ageMillis,
        )
        return ParseResult(metric, freshness.first, freshness.second)
    }

    private fun isFinitePercent(value: Double): Boolean =
        value.isFinite() && value >= 0.0 && value <= 100.0

    data class WindowInfo(val ageMillis: Long?, val windowMillis: Long?)

    fun parseWindow(text: String, nowMillis: Long): WindowInfo {
        val from = usageFromPattern.find(text)
        val relativeEnd = from?.groupValues?.get(2)?.toLongOrNull()
        val window = if (from != null) {
            val start = from.groupValues[1].toLongOrNull()
            if (start != null && relativeEnd != null) (start - relativeEnd).coerceAtLeast(0L) else null
        } else {
            null
        }

        val absolute = absoluteTimePattern.find(text)
        val age = if (absolute != null) {
            val endText = absolute.groupValues[2]
            parseTimestamp(endText)?.let { (nowMillis - it).coerceAtLeast(0L) }
        } else {
            relativeEnd
        }
        return WindowInfo(ageMillis = age, windowMillis = window)
    }

    /** 命令失败分类：权限拒绝 / 不支持 / 超时 / 其他失败。 */
    fun classifyCommandFailure(detail: String): ProbeStatus {
        val lower = detail.lowercase()
        return when {
            lower.contains("permission denied") -> ProbeStatus.DEGRADED
            lower.contains("not found") || lower.contains("unknown command") -> ProbeStatus.UNSUPPORTED
            lower.contains("timed out") || lower.contains("timeout") -> ProbeStatus.UNKNOWN
            else -> ProbeStatus.FAILED
        }
    }

    /** 解析 "2026-10-31 18:08:31.726"（设备本地时间）。 */
    fun parseTimestamp(raw: String): Long? = runCatching {
        val normalized = raw.trim()
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        formatter.parse(normalized)?.time
    }.getOrNull()
}
