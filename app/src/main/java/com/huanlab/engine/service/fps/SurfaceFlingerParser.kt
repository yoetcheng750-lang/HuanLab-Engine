package com.huanlab.engine.service.fps

/**
 * SurfaceFlinger --latency 输出解析。
 *
 * 输入格式：
 *   <refreshPeriodNs>
 *   <desiredPresentTimeNs> <actualPresentTimeNs> <frameReadyTimeNs>
 *   ...
 * 只使用 actualPresentTime（第二列），并过滤 INT64_MAX / 0 / 负数等无效值。
 */
object SurfaceFlingerParser {

    const val INVALID_TIMESTAMP = Long.MAX_VALUE

    fun parseLayerList(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Display") && !it.contains(" ") }
            .distinct()
            .toList()

    fun parsePresentTimestamps(output: String): List<Long> {
        val timestamps = ArrayList<Long>()
        var first = true
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            if (first) {
                first = false
                if (trimmed.toLongOrNull() != null) return@forEach
            }
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 3) return@forEach
            val present = parts[1].toLongOrNull() ?: return@forEach
            if (present == INVALID_TIMESTAMP || present <= 0L) return@forEach
            timestamps += present
        }
        return timestamps
    }

    /**
     * 以当前窗口过滤并计算 FPS：按时间升序、去重、丢弃倒退值。
     * 有效帧不足 MIN_FRAMES 时返回 null，绝不用刷新率补位。
     */
    fun computeFps(
        timestampsNs: List<Long>,
        windowStartNs: Long,
        windowEndNs: Long,
    ): FpsComputation {
        if (timestampsNs.isEmpty()) {
            return FpsComputation(null, 0, 0L, "空输出")
        }
        val filtered = ArrayList<Long>()
        var previous = Long.MIN_VALUE
        for (value in timestampsNs.sorted()) {
            if (value < windowStartNs || value > windowEndNs) continue
            if (value <= previous) continue
            previous = value
            filtered += value
        }
        if (filtered.size < MIN_FRAMES) {
            return FpsComputation(null, filtered.size, 0L, "有效帧不足（${filtered.size}/$MIN_FRAMES）")
        }
        val first = filtered.first()
        val last = filtered.last()
        val span = last - first
        if (span <= 0L) {
            return FpsComputation(null, filtered.size, 0L, "时间窗口无效")
        }
        val fps = (filtered.size - 1).toDouble() / (span.toDouble() / 1_000_000_000.0)
        if (fps.isNaN() || fps.isInfinite() || fps <= 0.0) {
            return FpsComputation(null, filtered.size, span / 1_000_000L, "计算结果无效")
        }
        return FpsComputation(fps.toFloat(), filtered.size, span / 1_000_000L, "OK")
    }

    const val MIN_FRAMES = 10

    data class FpsComputation(
        val fps: Float?,
        val validFrames: Int,
        val windowMillis: Long,
        val note: String,
    )
}
