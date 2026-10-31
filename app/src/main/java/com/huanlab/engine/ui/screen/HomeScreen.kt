package com.huanlab.engine.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huanlab.engine.core.ShizukuPhase
import com.huanlab.engine.service.MemorySnapshot
import com.huanlab.engine.service.Metric
import com.huanlab.engine.shared.ThermalLevel
import com.huanlab.engine.ui.UiState
import com.huanlab.engine.ui.components.YoeCard
import com.huanlab.engine.ui.components.YoeMetricBar
import com.huanlab.engine.ui.components.YoeSectionTitle
import com.huanlab.engine.ui.components.YoeStatusTile
import com.huanlab.engine.ui.theme.YoeMotion
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: UiState,
    dynamicColor: Boolean,
    dynamicColorSupported: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    onTogglePerformance: (Boolean) -> Unit,
    onPreviewRefreshRate: (Float) -> Unit,
    onApplyRefreshRate: (Float) -> Unit,
    onRetryShizuku: () -> Unit,
    onWhitelistSelf: () -> Unit,
    onOpenControl: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandHeader(state, onRetryShizuku, onOpenControl)
        DynamicColorCard(dynamicColor, dynamicColorSupported, onToggleDynamicColor)
        ShizukuCard(state.shizuku.phase, state.shizuku.detail)
        StatusTiles(state)
        PerformanceCard(state, onTogglePerformance)
        RefreshRateCard(state, onPreviewRefreshRate, onApplyRefreshRate)
        LiveMetricsCard(state)
        Button(
            onClick = onWhitelistSelf,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("加入 Doze 白名单 · deviceidle whitelist", style = MaterialTheme.typography.labelMedium)
        }
        EventLog(state.events)
    }
}

@Composable
private fun BrandHeader(state: UiState, onRetry: () -> Unit, onOpenControl: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(46.dp),
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFF5A6BFF), Color(0xFF8B5CF6))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Y", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("YOET", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "HumanLab Engine · 设备调优控制台",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "重试",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DynamicColorCard(enabled: Boolean, supported: Boolean, onToggle: (Boolean) -> Unit) {
    YoeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("动态取色", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    when {
                        !supported -> "需要 Android 12 及以上，本机不支持"
                        enabled -> "跟随系统壁纸的 Material You 配色"
                        else -> "使用 YOET 品牌蓝紫灰配色"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled && supported,
                onCheckedChange = onToggle,
                enabled = supported,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun ShizukuCard(phase: ShizukuPhase, detail: String) {
    val accent = when (phase) {
        ShizukuPhase.CONNECTED_ROOT, ShizukuPhase.CONNECTED_SHELL -> MaterialTheme.colorScheme.secondary
        ShizukuPhase.NOT_AUTHORIZED -> MaterialTheme.colorScheme.primary
        ShizukuPhase.NOT_RUNNING, ShizukuPhase.NOT_INSTALLED -> MaterialTheme.colorScheme.error
        ShizukuPhase.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(accent),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn(YoeMotion.effects()) togetherWith fadeOut(YoeMotion.effects()) },
                    label = "shizuku-phase",
                ) { current ->
                    Text(
                        when (current) {
                            ShizukuPhase.CONNECTED_SHELL -> "Shizuku 已连接 · shell"
                            ShizukuPhase.CONNECTED_ROOT -> "Shizuku 已连接 · root"
                            ShizukuPhase.NOT_AUTHORIZED -> "Shizuku 等待授权"
                            ShizukuPhase.NOT_RUNNING -> "Shizuku 服务未运行"
                            ShizukuPhase.NOT_INSTALLED -> "未安装 Shizuku"
                            ShizukuPhase.UNKNOWN -> "Shizuku 状态未知"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                    )
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun StatusTiles(state: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        YoeStatusTile("热状态", thermalText(state.thermal), thermalColorOf(state.thermal), Modifier.weight(1f))
        YoeStatusTile(
            "CPU",
            state.cpuUsage?.valuePercent?.let { "%.1f%%".format(it) } ?: "—",
            MaterialTheme.colorScheme.primary,
            Modifier.weight(1f),
        )
        YoeStatusTile("内存", state.memory.memPercentText(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
    }
}

@Composable
private fun PerformanceCard(state: UiState, onToggle: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    YoeCard(
        containerColor = if (state.performanceEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        YoeSectionTitle("性能模式", "固定性能模式 + 关闭省电 + 动画压缩")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.performanceEnabled) "电竞性能" else "均衡",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (state.performanceEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (state.performanceEnabled) "cmd power set-fixed-performance-mode-enabled true" else "默认调度策略",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.performanceEnabled,
                onCheckedChange = onToggle,
                enabled = !state.busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun RefreshRateCard(
    state: UiState,
    onPreview: (Float) -> Unit,
    onApply: (Float) -> Unit,
) {
    var local by remember { mutableFloatStateOf(state.refreshRateTarget) }
    LaunchedEffect(state.refreshRateTarget) { local = state.refreshRateTarget }
    val accent = MaterialTheme.colorScheme.primary
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        YoeSectionTitle("高刷调节", "settings put system peak_refresh_rate")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${local.toInt()}", style = MaterialTheme.typography.displaySmall, color = accent)
            Text(
                " Hz",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.busy) "下发中…" else "松手下发",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onPreview(it)
            },
            onValueChangeFinished = { onApply(local) },
            valueRange = 60f..144f,
            steps = 6,
            enabled = !state.busy,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("60", "90", "120", "144").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LiveMetricsCard(state: UiState) {
    val cpuMetric = state.cpu
    val memMetric = state.memory
    val cpuTarget = (cpuMetric.valueOrNull() ?: 0f) / 100f
    val memTarget = (memMetric.valueOrNull()?.usedRatio ?: 0f) / 100f
    val cpuAnim by animateFloatAsState(targetValue = cpuTarget, animationSpec = YoeMotion.spatial(), label = "cpu")
    val memAnim by animateFloatAsState(targetValue = memTarget, animationSpec = YoeMotion.spatial(), label = "mem")

    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        YoeSectionTitle("实时监控", "跨调用采样窗口 · 每 2 秒刷新")
        Spacer(Modifier.height(14.dp))
        when (cpuMetric) {
            is Metric.Known -> YoeMetricBar("CPU 占用", cpuAnim, MaterialTheme.colorScheme.secondary)
            is Metric.Unknown -> Text("CPU：不可用 · ${cpuMetric.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        when (memMetric) {
            is Metric.Known -> YoeMetricBar("内存占用", memAnim, MaterialTheme.colorScheme.primary)
            is Metric.Unknown -> Text("内存：不可用 · ${memMetric.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        when (memMetric) {
            is Metric.Known -> Text(
                "MemTotal ${gb(memMetric.value.totalBytes)} · MemAvailable ${gb(memMetric.value.availableBytes)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is Metric.Unknown -> Text(
                memMetric.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        val refresh = state.snapshot?.refreshRate
        Text(
            buildString {
                append("请求 ")
                append(refresh?.requestedHz?.let { if (it is Metric.Known) "${it.value.toInt()}Hz" else "—" } ?: "—")
                append(" · 实际 ")
                append(refresh?.actualHz?.let { if (it is Metric.Known) "%.1fHz".format(it.value) else "—" } ?: "—")
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventLog(events: List<String>) {
    if (events.isEmpty()) return
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        YoeSectionTitle("执行记录", "最近 8 条真实命令结果")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            events.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    is Metric.Known -> thermalColor(metric.value)
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

private fun thermalColor(level: ThermalLevel): Color = when (level) {
    ThermalLevel.NORMAL -> Color(0xFF4FD1A5)
    ThermalLevel.LIGHT -> Color(0xFFA3E635)
    ThermalLevel.MODERATE -> Color(0xFFFBBF24)
    ThermalLevel.SEVERE -> Color(0xFFFB923C)
    ThermalLevel.CRITICAL -> Color(0xFFFF8A8A)
    ThermalLevel.UNKNOWN -> Color(0xFF8E97AD)
}

private fun gb(bytes: Long): String = "%.1fG".format(bytes / 1024.0 / 1024.0 / 1024.0)
