package com.huanlab.engine.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huanlab.engine.service.MemorySnapshot
import com.huanlab.engine.service.Metric
import com.huanlab.engine.service.metrics.CpuFrequencySnapshot
import com.huanlab.engine.service.metrics.CpuUsageMetric
import com.huanlab.engine.service.metrics.ProbeStatus
import com.huanlab.engine.service.metrics.SensorCategory
import com.huanlab.engine.service.metrics.ThermalSnapshot
import com.huanlab.engine.shared.ThermalLevel
import com.huanlab.engine.ui.UiState
import com.huanlab.engine.ui.components.YoeCard
import com.huanlab.engine.ui.components.YoeMetricBar
import com.huanlab.engine.ui.components.YoeSectionTitle
import com.huanlab.engine.ui.components.YoeStatusTile
import com.huanlab.engine.ui.theme.YoeMotion

@Composable
fun MonitorScreen(state: UiState) {
    val cpu = state.cpu
    val mem = state.memory
    val thermal = state.thermal
    val refresh = state.snapshot?.refreshRate

    val cpuAnim by animateFloatAsState(
        (cpu.valueOrNull() ?: 0f) / 100f,
        YoeMotion.spatial(),
        label = "mon-cpu",
    )
    val memAnim by animateFloatAsState(
        (mem.valueOrNull()?.usedRatio ?: 0f) / 100f,
        YoeMotion.spatial(),
        label = "mon-mem",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("设备监控", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "数据来自 /proc、ThermalStatus 与 Display.Mode；不可用时显示「—」而不是 0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YoeStatusTile("热状态", thermalText(thermal), thermalColorOf(thermal), Modifier.weight(1f))
            YoeStatusTile("CPU", cpu.cpuPercentText(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            YoeStatusTile("内存", mem.memPercentText(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }

        YoeCard {
            YoeSectionTitle("CPU 与内存", "跨调用采样窗口，避免瞬时抖动")
            Spacer(Modifier.height(14.dp))
            when (cpu) {
                is Metric.Known -> {
                    YoeMetricBar("内核采样 /proc/stat", cpuAnim, MaterialTheme.colorScheme.primary)
                }
                is Metric.Unknown -> Text(
                    "内核采样不可用 · ${cpu.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            when (mem) {
                is Metric.Known -> YoeMetricBar("内存", memAnim, MaterialTheme.colorScheme.tertiary)
                is Metric.Unknown -> Text("内存：不可用 · ${mem.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            when (mem) {
                is Metric.Known -> Text(
                    "MemTotal ${gb(mem.value.totalBytes)} · MemAvailable ${gb(mem.value.availableBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is Metric.Unknown -> Text(mem.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            YoeSectionTitle("刷新率", "请求值 / 实际模式 / 设备支持")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                YoeStatusTile(
                    "请求",
                    refresh?.requestedHz?.hzText() ?: "—",
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f),
                )
                YoeStatusTile(
                    "实际",
                    refresh?.actualHz?.hzText() ?: "—",
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            val supported = refresh?.supportedHz.orEmpty()
            Text(
                if (supported.isEmpty()) "设备支持的刷新率：不可用"
                else "设备支持：${supported.joinToString(" / ") { "%.0f".format(it) }} Hz",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CpuUsageCard(state.cpuUsage)
        CpuFrequencyCard(state.cpuFrequency)
        ThermalSensorsCard(state.thermalSensors)

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            YoeSectionTitle("数据来源", "真实接口优先")
            Spacer(Modifier.height(8.dp))
            Text(
                "CPU：/proc/stat 两次采样差值；内存：/proc/meminfo；热状态：PowerManager.getCurrentThermalStatus；" +
                    "实际刷新率：Display.getMode().refreshRate；请求刷新率：Settings.System.peak_refresh_rate。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "FPS、旁路供电等能力尚未接入；此处不会用估算值冒充真实数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CpuUsageCard(usage: CpuUsageMetric?) {
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        YoeSectionTitle("CPU 使用率", "user + kernel（执行态占用，不含 iowait/irq/softirq）")
        Spacer(Modifier.height(10.dp))
        val value = usage?.valuePercent
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.1f%%".format(value),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    usage.sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            val user = usage.userPercent
            val kernel = usage.kernelPercent
            if (user != null && kernel != null) {
                Text(
                    "执行态：${"%.1f".format(user)}% user + ${"%.1f".format(kernel)}% kernel",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            usage.systemTotalPercent?.let { total ->
                Text(
                    "系统总比例：${"%.1f".format(total)}%（含 iowait/irq/softirq，口径不同）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                buildString {
                    usage.sampleWindowMillis?.let { append("窗口 ${it}ms · ") }
                    usage.sampleAgeMillis?.let { append("样本年龄 ${it}ms · ") }
                    append("状态 ${usage.status.name}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (usage.status != ProbeStatus.PASS) {
                usage.reason?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ColorOS 系统限制无法读取 CPU 使用率，请参考当前频率",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            usage?.reason?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            usage?.loadAverage1m?.let { load ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "系统负载（1分钟）：${"%.2f".format(load)}（负载不是 CPU 使用率）",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CpuFrequencyCard(snapshot: CpuFrequencySnapshot?) {
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        YoeSectionTitle(
            "CPU 频率",
            if (snapshot?.policies?.any { it.source.name == "SCALING_CUR_FREQ" } == true) {
                "内核调频报告值（非硬件瞬时频率）"
            } else {
                "真实频率节点"
            },
        )
        Spacer(Modifier.height(10.dp))
        val policies = snapshot?.policies.orEmpty()
        if (policies.isEmpty()) {
            Text(
                "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                snapshot?.reason ?: "尚未采样",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                policies.forEach { policy ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                policy.policyId + if (policy.affectedCpus.isNotEmpty()) {
                                    " · cpu " + policy.affectedCpus.joinToString(",")
                                } else {
                                    ""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                policy.source.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            policy.frequencyMhz?.let { "%.0f MHz".format(it) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (policy.status == ProbeStatus.PASS) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        if (snapshot != null && snapshot.status != ProbeStatus.PASS) {
            Spacer(Modifier.height(8.dp))
            Text(
                snapshot.reason ?: "部分 Policy 不可读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThermalSensorsCard(snapshot: ThermalSnapshot?) {
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        YoeSectionTitle("设备温度", "来自真实传感器节点 / BatteryManager")
        Spacer(Modifier.height(10.dp))
        val sensors = snapshot?.sensors.orEmpty()
        if (sensors.isEmpty()) {
            Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(snapshot?.reason ?: "尚未采样", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sensors.forEach { sensor ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sensor.category.label + " · " + sensor.zoneId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                sensor.rawType + " · " + sensor.source.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            sensor.temperatureCelsius?.let { "%.1f℃".format(it) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (sensor.status == ProbeStatus.PASS) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    sensor.reason?.takeIf { sensor.status != ProbeStatus.PASS }?.let { reason ->
                        Text(
                            reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "CPU/GPU 温度依赖 /sys/class/thermal；被系统权限限制时显示「—」。SoC 与 CPU 不混用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Metric<Float>.hzText(): String = when (this) {
    is Metric.Known -> "%.1fHz".format(value)
    is Metric.Unknown -> "—"
}

private fun Metric<Float>.cpuPercentText(): String = when (this) {
    is Metric.Known -> "${value.toInt()}%"
    is Metric.Unknown -> "—"
}

private fun Metric<MemorySnapshot>.memPercentText(): String = when (this) {
    is Metric.Known -> "${value.usedRatio.toInt()}%"
    is Metric.Unknown -> "—"
}

private fun thermalText(metric: Metric<ThermalLevel>): String = when (metric) {
    is Metric.Known -> thermalLabel(metric.value)
    is Metric.Unknown -> "—"
}

private fun thermalColorOf(metric: Metric<ThermalLevel>): Color = when (metric) {
    is Metric.Known -> when (metric.value) {
        ThermalLevel.NORMAL -> Color(0xFF4FD1A5)
        ThermalLevel.LIGHT -> Color(0xFFA3E635)
        ThermalLevel.MODERATE -> Color(0xFFFBBF24)
        ThermalLevel.SEVERE -> Color(0xFFFB923C)
        ThermalLevel.CRITICAL -> Color(0xFFFF8A8A)
        ThermalLevel.UNKNOWN -> Color(0xFF8E97AD)
    }
    is Metric.Unknown -> Color(0xFF8E97AD)
}

private fun thermalLabel(level: ThermalLevel): String = when (level) {
    ThermalLevel.NORMAL -> "NORMAL"
    ThermalLevel.LIGHT -> "LIGHT"
    ThermalLevel.MODERATE -> "MODERATE"
    ThermalLevel.SEVERE -> "SEVERE"
    ThermalLevel.CRITICAL -> "CRITICAL"
    ThermalLevel.UNKNOWN -> "UNKNOWN"
}

private fun gb(bytes: Long): String = "%.1fG".format(bytes / 1024.0 / 1024.0 / 1024.0)
