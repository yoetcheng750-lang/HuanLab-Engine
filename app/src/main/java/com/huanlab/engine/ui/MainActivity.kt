package com.huanlab.engine.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huanlab.engine.core.ShizukuPhase
import com.huanlab.engine.shared.ThermalLevel
import com.huanlab.engine.ui.theme.HuanLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HuanLabTheme {
                val viewModel: HuanLabViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                HuanLabScreen(
                    state = state,
                    onTogglePerformance = viewModel::togglePerformance,
                    onPreviewRefreshRate = viewModel::previewRefreshRate,
                    onApplyRefreshRate = viewModel::applyRefreshRate,
                    onRetryShizuku = viewModel::retryShizuku,
                    onWhitelistSelf = viewModel::whitelistSelf,
                )
            }
        }
    }
}

@Composable
private fun HuanLabScreen(
    state: UiState,
    onTogglePerformance: (Boolean) -> Unit,
    onPreviewRefreshRate: (Float) -> Unit,
    onApplyRefreshRate: (Float) -> Unit,
    onRetryShizuku: () -> Unit,
    onWhitelistSelf: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0B1020),
                            MaterialTheme.colorScheme.background,
                            Color(0xFF05070D),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 56.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(state, onRetryShizuku)
                StatusRow(state)
                PerformanceCard(state, onTogglePerformance)
                RefreshRateCard(state, onPreviewRefreshRate, onApplyRefreshRate)
                MetricsCard(state)
                ActionRow(state, onWhitelistSelf)
                EventLog(state.events)
            }
        }
    }
}

@Composable
private fun Header(state: UiState, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("幻", color = Color(0xFF080B12), fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "幻实验室",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "HuanLab Engine · 设备调优控制台",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = state.shizuku.phase,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "shizuku-status",
        ) { phase ->
            StatusBanner(phase, state)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "重新检测 Shizuku",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatusBanner(phase: ShizukuPhase, state: UiState) {
    val color = when (phase) {
        ShizukuPhase.CONNECTED_ROOT, ShizukuPhase.CONNECTED_SHELL -> Color(0xFF34D399)
        ShizukuPhase.NOT_AUTHORIZED -> Color(0xFFFBBF24)
        ShizukuPhase.NOT_RUNNING, ShizukuPhase.NOT_INSTALLED -> Color(0xFFFF6B6B)
        ShizukuPhase.UNKNOWN -> Color(0xFF94A3B8)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (phase) {
                    ShizukuPhase.CONNECTED_SHELL -> "Shizuku 已连接 · shell"
                    ShizukuPhase.CONNECTED_ROOT -> "Shizuku 已连接 · root"
                    ShizukuPhase.NOT_AUTHORIZED -> "Shizuku 等待授权"
                    ShizukuPhase.NOT_RUNNING -> "Shizuku 服务未运行"
                    ShizukuPhase.NOT_INSTALLED -> "未安装 Shizuku"
                    ShizukuPhase.UNKNOWN -> "Shizuku 状态未知"
                },
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
            Text(
                state.shizuku.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            thermalLabel(state.thermal),
            style = MaterialTheme.typography.labelSmall,
            color = thermalColor(state.thermal),
        )
    }
}

@Composable
private fun StatusRow(state: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("热状态", thermalLabel(state.thermal), thermalColor(state.thermal), Modifier.weight(1f))
        Pill("CPU", "${state.cpuPercent.toInt()}%", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        Pill(
            "内存",
            if (state.memory.totalBytes > 0L) "${(state.memory.usedRatio * 100).toInt()}%" else "--",
            MaterialTheme.colorScheme.tertiary,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Pill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun PerformanceCard(state: UiState, onToggle: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    SectionCard(
        title = "性能模式",
        subtitle = "固定性能模式 + 关闭省电 + 动画压缩",
        accent = accent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.performanceEnabled) "电竞性能" else "均衡",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.performanceEnabled) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (state.performanceEnabled) "cmd power set-fixed-performance-mode-enabled true"
                    else "默认调度策略",
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
                    checkedThumbColor = Color(0xFF0B0E14),
                    checkedTrackColor = accent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
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
    var local by remember { mutableFloatStateOf(state.refreshRate) }
    LaunchedEffect(state.refreshRate) { local = state.refreshRate }
    val accent = MaterialTheme.colorScheme.secondary

    SectionCard(
        title = "高刷调节",
        subtitle = "settings put system peak_refresh_rate",
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${local.toInt()}",
                style = MaterialTheme.typography.displaySmall,
                color = accent,
            )
            Text(
                " Hz",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.busy) "下发中…" else "松手下发",
                style = MaterialTheme.typography.labelSmall,
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
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("90", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("120", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("144", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricsCard(state: UiState) {
    val cpuTarget = state.cpuPercent / 100f
    val memTarget = state.memory.usedRatio
    val cpuAnim by animateFloatAsState(
        targetValue = cpuTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cpu",
    )
    val memAnim by animateFloatAsState(
        targetValue = memTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "mem",
    )

    SectionCard(
        title = "状态监控",
        subtitle = "每 2 秒轮询 /proc/stat 与 /proc/meminfo",
        accent = MaterialTheme.colorScheme.tertiary,
    ) {
        MetricBar("CPU 占用", cpuAnim, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(14.dp))
        MetricBar("内存占用", memAnim, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(10.dp))
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
}

@Composable
private fun MetricBar(label: String, ratio: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = color)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))),
            )
        }
    }
}

@Composable
private fun ActionRow(state: UiState, onWhitelist: () -> Unit) {
    Button(
        onClick = onWhitelist,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text("加入 Doze 白名单（dumpsys deviceidle whitelist +pkg）", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventLog(events: List<String>) {
    if (events.isEmpty()) return
    SectionCard(
        title = "执行记录",
        subtitle = "最近 8 条真实命令结果",
        accent = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            events.forEach { event ->
                Text(
                    event,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    val border by animateColorAsState(
        targetValue = accent.copy(alpha = 0.28f),
        animationSpec = tween(400),
        label = "card-border",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
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

private fun thermalColor(level: ThermalLevel): Color = when (level) {
    ThermalLevel.NORMAL -> Color(0xFF34D399)
    ThermalLevel.LIGHT -> Color(0xFFA3E635)
    ThermalLevel.MODERATE -> Color(0xFFFBBF24)
    ThermalLevel.SEVERE -> Color(0xFFFB923C)
    ThermalLevel.CRITICAL -> Color(0xFFFF6B6B)
    ThermalLevel.UNKNOWN -> Color(0xFF94A3B8)
}

private fun gb(bytes: Long): String = "%.1fG".format(bytes / 1024.0 / 1024.0 / 1024.0)
