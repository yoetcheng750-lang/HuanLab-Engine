package com.huanlab.engine.service.metrics

import com.huanlab.engine.action.ActionExecutor
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CpuFrequencySnapshot(
    val policies: List<CpuFrequencyMetric>,
    val status: ProbeStatus,
    val reason: String?,
    val sampledAtMillis: Long,
)

/**
 * CPU 频率真实采样。
 *
 * 数据源优先级：cpuinfo_cur_freq（硬件反馈）> cpuinfo_avg_freq > scaling_cur_freq（内核报告）。
 * 当前探测到的 ColorOS 设备上 cpuinfo_cur_freq 被 SELinux 拒绝，只有 scaling_cur_freq 可读，
 * 因此 UI 必须标注「内核报告」，不能声称是硬件瞬时频率。
 */
class CpuFrequencyMonitor(private val engine: Engine) {

    private val executor = ActionExecutor()

    suspend fun sample(): CpuFrequencySnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val base = "/sys/devices/system/cpu/cpufreq"

        val listReport = engine.execute(listOf(listOf("ls", base)))
        val listResult = (listReport as? ExecutionReport.Executed)?.results?.firstOrNull()
        if (listResult == null || !listResult.ok) {
            val status = if (listReport is ExecutionReport.Rejected) {
                ProbeStatus.FAILED
            } else {
                CpuFrequencyParser.classifyRead(false, listResult?.stderr) ?: ProbeStatus.UNKNOWN
            }
            val detail = if (listReport is ExecutionReport.Rejected) {
                listReport.summary
            } else {
                listResult?.stderr ?: "无输出"
            }
            return@withContext CpuFrequencySnapshot(
                policies = emptyList(),
                status = status,
                reason = CpuFrequencyParser.reasonFor(status, detail),
                sampledAtMillis = now,
            )
        }

        val policies = CpuFrequencyParser.parsePolicyList(listResult.stdout)
        if (policies.isEmpty()) {
            return@withContext CpuFrequencySnapshot(
                policies = emptyList(),
                status = ProbeStatus.UNSUPPORTED,
                reason = "设备未提供 cpufreq policy",
                sampledAtMillis = now,
            )
        }

        val commands = ArrayList<List<String>>(policies.size * 3)
        policies.forEach { policy ->
            commands += listOf("cat", "$base/$policy/related_cpus")
            commands += listOf("cat", "$base/$policy/cpuinfo_cur_freq")
            commands += listOf("cat", "$base/$policy/scaling_cur_freq")
        }

        val report = engine.execute(commands)
        if (report !is ExecutionReport.Executed) {
            return@withContext CpuFrequencySnapshot(
                policies = emptyList(),
                status = ProbeStatus.FAILED,
                reason = "Shizuku 未就绪：" + ((report as? ExecutionReport.Rejected)?.summary ?: "未知拒绝"),
                sampledAtMillis = now,
            )
        }

        val metrics = ArrayList<CpuFrequencyMetric>(policies.size)
        var index = 0
        for (policy in policies) {
            val related = report.results.getOrNull(index++)
            val cpuinfo = report.results.getOrNull(index++)
            val scaling = report.results.getOrNull(index++)
            val cpus = CpuFrequencyParser.parseCpuList(related?.takeIf { it.ok }?.stdout)

            val cpuinfoParsed = if (cpuinfo?.ok == true) {
                CpuFrequencyParser.parseKhz(cpuinfo.stdout)
            } else {
                null
            }
            val scalingParsed = if (scaling?.ok == true) {
                CpuFrequencyParser.parseKhz(scaling.stdout)
            } else {
                null
            }

            val metric = when {
                cpuinfoParsed?.khz != null -> CpuFrequencyMetric(
                    policyId = policy,
                    affectedCpus = cpus,
                    frequencyKhz = cpuinfoParsed.khz,
                    frequencyMhz = CpuFrequencyParser.toMhz(cpuinfoParsed.khz),
                    source = CpuFreqSource.CPUINFO_CUR_FREQ,
                    status = ProbeStatus.PASS,
                    reason = null,
                    sampledAtMillis = now,
                )

                scalingParsed?.khz != null -> CpuFrequencyMetric(
                    policyId = policy,
                    affectedCpus = cpus,
                    frequencyKhz = scalingParsed.khz,
                    frequencyMhz = CpuFrequencyParser.toMhz(scalingParsed.khz),
                    source = CpuFreqSource.SCALING_CUR_FREQ,
                    status = ProbeStatus.PASS,
                    reason = null,
                    sampledAtMillis = now,
                )

                else -> {
                    val status = CpuFrequencyParser.classifyRead(
                        ok = false,
                        stderr = scaling?.stderr ?: cpuinfo?.stderr,
                    ) ?: ProbeStatus.UNKNOWN
                    CpuFrequencyMetric(
                        policyId = policy,
                        affectedCpus = cpus,
                        frequencyKhz = null,
                        frequencyMhz = null,
                        source = CpuFreqSource.UNKNOWN,
                        status = status,
                        reason = CpuFrequencyParser.reasonFor(
                            status,
                            scaling?.stderr?.ifBlank { null } ?: cpuinfo?.stderr ?: "无输出",
                        ),
                        sampledAtMillis = now,
                    )
                }
            }
            metrics += metric
        }

        val overall = when {
            metrics.isEmpty() -> ProbeStatus.UNSUPPORTED
            metrics.all { it.status == ProbeStatus.PASS } -> ProbeStatus.PASS
            metrics.any { it.status == ProbeStatus.PASS } -> ProbeStatus.DEGRADED
            else -> metrics.first().status
        }
        CpuFrequencySnapshot(
            policies = metrics,
            status = overall,
            reason = if (overall == ProbeStatus.PASS) null else metrics.firstOrNull { it.status != ProbeStatus.PASS }?.reason,
            sampledAtMillis = now,
        )
    }
}
