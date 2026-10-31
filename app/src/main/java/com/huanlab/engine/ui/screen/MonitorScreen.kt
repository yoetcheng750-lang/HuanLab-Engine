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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huanlab.engine.shared.ThermalLevel
import com.huanlab.engine.ui.UiState
import com.huanlab.engine.ui.components.YoeCard
import com.huanlab.engine.ui.components.YoeMetricBar
import com.huanlab.engine.ui.components.YoeSectionTitle
import com.huanlab.engine.ui.components.YoeStatusTile
import com.huanlab.engine.ui.theme.YoeMotion

@Composable
fun MonitorScreen(state: UiState) {
    val cpu by animateFloatAsState(state.cpuPercent / 100f, YoeMotion.spatial(), label = "mon-cpu")
    val mem by animateFloatAsState(state.memory.usedRatio, YoeMotion.spatial(), label = "mon-mem")

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
                "全部数据来自 /proc 与系统 PowerManager，每 2 秒刷新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YoeStatusTile("CPU", "${state.cpuPercent.toInt()}%", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            YoeStatusTile("内存", "${(state.memory.usedRatio * 100).toInt()}%", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            YoeStatusTile("温度档", thermalLabel(state.thermal), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }

        YoeCard {
            YoeSectionTitle("CPU 与内存", "实时占用")
            Spacer(Modifier.height(14.dp))
            YoeMetricBar("CPU", cpu, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            YoeMetricBar("内存", mem, MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(12.dp))
            Text(
                if (state.memory.totalBytes > 0L) {
                    "MemTotal ${gb(state.memory.totalBytes)} · MemAvailable ${gb(state.memory.availableBytes)}"
                } else {
                    "正在读取内存信息…"
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            YoeSectionTitle("刷新率", "当前目标")
            Spacer(Modifier.height(10.dp))
            Text(
                "${state.refreshRate.toInt()} Hz",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            YoeSectionTitle("已知限制", "透明说明")
            Spacer(Modifier.height(8.dp))
            Text(
                "FPS、旁路供电和场景自动调度依赖具体机型与系统接口，当前版本尚未接入；" +
                    "这里不会用模拟数据填充这些指标。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
