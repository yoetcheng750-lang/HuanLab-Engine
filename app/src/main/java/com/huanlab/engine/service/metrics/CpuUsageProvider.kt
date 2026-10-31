package com.huanlab.engine.service.metrics

import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CPU 使用率数据源调度：
 *   P0 dumpsys cpuinfo（ActivityManager 系统服务统计，shell 可读）
 *   P1 Toybox top 系统汇总行
 *   P2 /proc/loadavg（仅独立负载，绝不换算成 CPU%）
 *   P3 全部失败 → UNKNOWN（UI 显示「—」）
 */
class CpuUsageProvider(private val engine: Engine) {

    suspend fun sample(): CpuUsageMetric = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val load = readLoadAverage()

        val dumpsys = runDumpsys(now, load)
        if (dumpsys.valuePercent != null &&
            (dumpsys.status == ProbeStatus.PASS || dumpsys.status == ProbeStatus.DEGRADED)
        ) {
            return@withContext dumpsys
        }

        val top = runTop(now, load)
        if (top.valuePercent != null) {
            return@withContext top
        }

        val reason = buildString {
            append("dumpsys=")
            append(dumpsys.reason ?: "无数据")
            append("；top=")
            append(top.reason ?: "无数据")
        }
        CpuUsageMetric(
            valuePercent = null,
            userPercent = null,
            kernelPercent = null,
            systemTotalPercent = null,
            iowaitPercent = null,
            irqPercent = null,
            softirqPercent = null,
            loadAverage1m = load?.oneMinute,
            loadAverage5m = load?.fiveMinutes,
            loadAverage15m = load?.fifteenMinutes,
            source = CpuUsageSource.UNKNOWN,
            status = ProbeStatus.UNKNOWN,
            reason = reason,
            sampledAtMillis = now,
            sampleWindowMillis = null,
            sampleAgeMillis = null,
        )
    }

    private suspend fun runDumpsys(now: Long, load: CpuLoadAvgParser.LoadAvg?): CpuUsageMetric {
        val report = engine.execute(listOf(listOf("dumpsys", "cpuinfo")))
        val result = (report as? ExecutionReport.Executed)?.results?.firstOrNull()
        if (result == null || !result.ok) {
            val detail = if (report is ExecutionReport.Rejected) {
                (report as? ExecutionReport.Rejected)?.summary ?: "Shizuku 拒绝"
            } else {
                result?.stderr?.ifBlank { null } ?: "无输出"
            }
            val status = CpuDumpsysParser.classifyCommandFailure(detail)
            return emptyMetric(now, load, CpuUsageSource.DUMPSYS_CPUINFO, status, detail)
        }
        val parsed = CpuDumpsysParser.parse(result.stdout, now)
        val metric = parsed.metric
        if (metric == null) {
            return emptyMetric(
                now, load, CpuUsageSource.DUMPSYS_CPUINFO,
                parsed.status, parsed.reason ?: "解析失败",
            )
        }
        return metric.copy(
            loadAverage1m = load?.oneMinute,
            loadAverage5m = load?.fiveMinutes,
            loadAverage15m = load?.fifteenMinutes,
        )
    }

    private suspend fun runTop(now: Long, load: CpuLoadAvgParser.LoadAvg?): CpuUsageMetric {
        val report = engine.execute(listOf(listOf("top", "-b", "-n", "1", "-m", "5")))
        val result = (report as? ExecutionReport.Executed)?.results?.firstOrNull()
        if (result == null || !result.ok) {
            val detail = if (report is ExecutionReport.Rejected) {
                (report as? ExecutionReport.Rejected)?.summary ?: "Shizuku 拒绝"
            } else {
                result?.stderr?.ifBlank { null } ?: "无输出"
            }
            return emptyMetric(
                now, load, CpuUsageSource.TOYBOX_TOP,
                CpuDumpsysParser.classifyCommandFailure(detail), detail,
            )
        }
        val parsed = CpuTopParser.parse(result.stdout)
        val normalized = CpuTopParser.normalize(
            parsed.userPercent,
            parsed.systemPercent,
            parsed.totalCapacityPercent,
        )
        if (normalized == null) {
            return emptyMetric(
                now, load, CpuUsageSource.TOYBOX_TOP,
                parsed.status, parsed.reason ?: "top 汇总不可用",
            )
        }
        return CpuUsageMetric(
            valuePercent = normalized,
            userPercent = parsed.userPercent,
            kernelPercent = parsed.systemPercent,
            systemTotalPercent = null,
            iowaitPercent = parsed.ioPercent,
            irqPercent = parsed.irqPercent,
            softirqPercent = null,
            loadAverage1m = load?.oneMinute,
            loadAverage5m = load?.fiveMinutes,
            loadAverage15m = load?.fifteenMinutes,
            source = CpuUsageSource.TOYBOX_TOP,
            status = ProbeStatus.DEGRADED,
            reason = parsed.reason,
            sampledAtMillis = now,
            sampleWindowMillis = null,
            sampleAgeMillis = null,
        )
    }

    private suspend fun readLoadAverage(): CpuLoadAvgParser.LoadAvg? {
        val report = engine.execute(listOf(listOf("cat", "/proc/loadavg")))
        val result = (report as? ExecutionReport.Executed)?.results?.firstOrNull() ?: return null
        if (!result.ok) return null
        return CpuLoadAvgParser.parse(result.stdout)
    }

    private fun emptyMetric(
        now: Long,
        load: CpuLoadAvgParser.LoadAvg?,
        source: CpuUsageSource,
        status: ProbeStatus,
        reason: String,
    ): CpuUsageMetric = CpuUsageMetric(
        valuePercent = null,
        userPercent = null,
        kernelPercent = null,
        systemTotalPercent = null,
        iowaitPercent = null,
        irqPercent = null,
        softirqPercent = null,
        loadAverage1m = load?.oneMinute,
        loadAverage5m = load?.fiveMinutes,
        loadAverage15m = load?.fifteenMinutes,
        source = source,
        status = status,
        reason = reason,
        sampledAtMillis = now,
        sampleWindowMillis = null,
        sampleAgeMillis = null,
    )
}
