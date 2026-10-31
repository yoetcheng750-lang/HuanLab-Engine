package com.huanlab.engine.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huanlab.engine.service.MemorySnapshot
import com.huanlab.engine.service.Metric
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
                is Metric.Known -> YoeMetricBar("CPU", cpuAnim, MaterialTheme.colorScheme.primary)
                is Metric.Unknown -> Text("CPU：不可用 · ${cpu.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
