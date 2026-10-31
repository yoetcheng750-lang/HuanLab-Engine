package com.huanlab.engine.service.fps

import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 真实 FPS 探测：
 *  1. 通过 Shizuku 执行只读诊断命令；
 *  2. Layer 无法唯一确认 → UNKNOWN；
 *  3. ROM 封锁 SurfaceFlinger → UNSUPPORTED；
 *  4. 有效帧不足 → UNKNOWN；
 *  5. 绝不用屏幕刷新率冒充 FPS。
 */
class FpsEngine(private val engine: Engine) {

    suspend fun sample(packageName: String): FpsResult = withContext(Dispatchers.IO) {
        val listReport = engine.execute(
            listOf(listOf("dumpsys", "SurfaceFlinger", "--list")),
        )
        val listOutput = when (listReport) {
            is ExecutionReport.Executed -> {
                val result = listReport.results.firstOrNull()
                if (result == null || !result.ok) {
                    return@withContext classifyFailure(
                        result?.shortMessage() ?: "无输出",
                    )
                }
                result.stdout
            }
            is ExecutionReport.Rejected -> return@withContext FpsResult.failed(
                "Shizuku 未就绪：${listReport.summary}",
            )
        }

        val layers = SurfaceFlingerParser.parseLayerList(listOutput)
        if (layers.isEmpty()) {
            return@withContext FpsResult.unsupported("SurfaceFlinger 未返回任何 Layer（可能被 ROM 封锁）")
        }

        val match = LayerResolver.resolve(layers, packageName)
        when (match.state) {
            LayerMatchState.NONE -> return@withContext FpsResult.unknown(match.reason)
            LayerMatchState.AMBIGUOUS -> return@withContext FpsResult.unknown(match.reason)
            LayerMatchState.UNIQUE -> Unit
        }
        val layer = match.layer ?: return@withContext FpsResult.unknown("Layer 为空")

        val latencyReport = engine.execute(
            listOf(listOf("dumpsys", "SurfaceFlinger", "--latency", layer)),
        )
        val latencyOutput = when (latencyReport) {
            is ExecutionReport.Executed -> {
                val result = latencyReport.results.firstOrNull()
                if (result == null || !result.ok) {
                    return@withContext classifyFailure(result?.shortMessage() ?: "无输出")
                }
                result.stdout
            }
            is ExecutionReport.Rejected -> return@withContext FpsResult.failed(
                "Shizuku 未就绪：${latencyReport.summary}",
            )
        }

        val timestamps = SurfaceFlingerParser.parsePresentTimestamps(latencyOutput)
        val nowNs = System.nanoTime()
        val windowStart = nowNs - WINDOW_NS
        val computation = SurfaceFlingerParser.computeFps(timestamps, windowStart, nowNs)
        FpsResult(
            state = if (computation.fps != null) FpsState.PASS else FpsState.UNKNOWN,
            fps = computation.fps,
            layer = layer,
            windowMillis = computation.windowMillis,
            validFrames = computation.validFrames,
            source = "dumpsys SurfaceFlinger --latency",
            reason = computation.note,
        )
    }

    private fun classifyFailure(detail: String): FpsResult {
        val lower = detail.lowercase()
        return when {
            lower.contains("permission denied") || lower.contains("not found") ->
                FpsResult.unsupported("接口不可用：$detail")
            lower.contains("timed out") || lower.contains("timeout") ->
                FpsResult.failed("命令超时：$detail")
            else -> FpsResult.failed(detail)
        }
    }

    private companion object {
        const val WINDOW_NS = 1_000_000_000L
    }
}
