package com.huanlab.engine.service.metrics

import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CPU 使用率唯一数据源：dumpsys cpuinfo。
 *
 * 不执行 top，不读取 /proc/loadavg，不做任何降级换算；
 * 任一环节失败均返回 UNKNOWN（valuePercent = null），UI 显示「—」。
 */
class CpuUsageProvider(private val engine: Engine) {

    suspend fun sample(): CpuUsageMetric = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val report = engine.execute(listOf(listOf("dumpsys", "cpuinfo")))
        when (report) {
            is ExecutionReport.Rejected ->
                CpuUsageMetric.unknown("Shizuku 未就绪：${report.summary}", now)

            is ExecutionReport.Executed -> {
                val result = report.results.firstOrNull()
                if (result == null || !result.ok) {
                    CpuUsageMetric.unknown(
                        result?.stderr?.ifBlank { null } ?: "dumpsys cpuinfo 执行失败",
                        now,
                    )
                } else {
                    val parsed = CpuDumpsysParser.parse(result.stdout, now)
                    val percent = parsed.valuePercent
                    if (percent == null || parsed.status != ProbeStatus.PASS) {
                        CpuUsageMetric.unknown(parsed.reason ?: "无有效 CPU 汇总数据", now)
                    } else {
                        CpuUsageMetric.valid(percent, parsed.sampleAgeMillis, now)
                    }
                }
            }
        }
    }
}
