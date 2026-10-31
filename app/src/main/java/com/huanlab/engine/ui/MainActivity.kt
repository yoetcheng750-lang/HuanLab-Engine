package com.huanlab.engine.ui

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huanlab.engine.core.ShizukuPhase
import com.huanlab.engine.shared.ThermalLevel
import com.huanlab.engine.ui.theme.HuanLabTheme
import com.huanlab.engine.ui.theme.YoeMotion
import com.huanlab.engine.ui.theme.supportsDynamicColor

class MainActivity : ComponentActivity() {

    private lateinit var uiPreferences: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiPreferences = UiPreferences(this)
        enableEdgeToEdge()
        setContent {
            var dynamicColor by remember { mutableStateOf(uiPreferences.dynamicColorEnabled) }
            HuanLabTheme(dynamicColor = dynamicColor) {
                val viewModel: HuanLabViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                YoeScreen(
                    state = state,
                    dynamicColor = dynamicColor,
                    dynamicColorSupported = supportsDynamicColor(),
                    onToggleDynamicColor = { enabled ->
                        dynamicColor = enabled
                        uiPreferences.dynamicColorEnabled = enabled
                    },
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
private fun YoeScreen(
    state: UiState,
    dynamicColor: Boolean,
    dynamicColorSupported: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    onTogglePerformance: (Boolean) -> Unit,
    onPreviewRefreshRate: (Float) -> Unit,
    onApplyRefreshRate: (Float) -> Unit,
    onRetryShizuku: () -> Unit,
    onWhitelistSelf: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            YoeHeader(state, onRetryShizuku)
            DynamicColorCard(
                enabled = dynamicColor,
                supported = dynamicColorSupported,
                onToggle = onToggleDynamicColor,
            )
            ShizukuBanner(state.shizuku.phase, state)
            StatusStrip(state)
            PerformanceCard(state, onTogglePerformance)
            RefreshRateCard(state, onPreviewRefreshRate, onApplyRefreshRate)
            MetricsCard(state)
            WhitelistButton(state, onWhitelistSelf)
            EventLog(state.events)
        }
    }
}

@Composable
private fun YoeHeader(state: UiState, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "Y",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "YOET",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
private fun DynamicColorCard(
    enabled: Boolean,
    supported: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "动态取色",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        !supported -> "需要 Android 12 及以上，本机不支持"
                        enabled -> "跟随系统壁纸的 Material You 配色"
                        else -> "使用 YOET 品牌深空配色"
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
private fun ShizukuBanner(phase: ShizukuPhase, state: UiState) {
    val accent = when (phase) {
        ShizukuPhase.CONNECTED_ROOT, ShizukuPhase.CONNECTED_SHELL -> MaterialTheme.colorScheme.tertiary
        ShizukuPhase.NOT_AUTHORIZED -> MaterialTheme.colorScheme.primary
        ShizukuPhase.NOT_RUNNING, ShizukuPhase.NOT_INSTALLED -> MaterialTheme.colorScheme.error
        ShizukuPhase.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        fadeIn(YoeMotion.effects()) togetherWith
                            fadeOut(YoeMotion.effects())
                    },
                    label = "banner-phase",
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
                Text(
                    state.shizuku.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                thermalLabel(state.thermal),
                style = MaterialTheme.typography.labelMedium,
                color = thermalColor(state.thermal),
            )
        }
    }
}

@Composable
private fun StatusStrip(state: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricPill("热状态", thermalLabel(state.thermal), thermalColor(state.thermal), Modifier.weight(1f))
        MetricPill("CPU", "${state.cpuPercent.toInt()}%", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        MetricPill(
            "内存",
            if (state.memory.totalBytes > 0L) "${(state.memory.usedRatio * 100).toInt()}%" else "--",
            MaterialTheme.colorScheme.primary,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent)
        }
    }
}

@Composable
private fun PerformanceCard(state: UiState, onToggle: (Boolean) -> Unit) {
    ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SectionHeader("性能模式", "固定性能模式 + 关闭省电 + 动画压缩")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.performanceEnabled) "电竞性能" else "均衡",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (state.performanceEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    if (state.performanceEnabled) {
                        "cmd power set-fixed-performance-mode-enabled true"
                    } else {
                        "默认调度策略"
                    },
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
private fun RefreshRateCard(
    state: UiState,
    onPreview: (Float) -> Unit,
    onApply: (Float) -> Unit,
) {
    var local by remember { mutableFloatStateOf(state.refreshRate) }
    LaunchedEffect(state.refreshRate) { local = state.refreshRate }
    val accent = MaterialTheme.colorScheme.secondary

    ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SectionHeader("高刷调节", "settings put system peak_refresh_rate")
        Spacer(Modifier.height(12.dp))
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
            listOf("60", "90", "120", "144").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricsCard(state: UiState) {
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val spatial: FiniteAnimationSpec<Float> =
        if (animationsEnabled) YoeMotion.spatial() else snap()

    val cpuAnim by animateFloatAsState(
        targetValue = state.cpuPercent / 100f,
        animationSpec = spatial,
        label = "cpu",
    )
    val memAnim by animateFloatAsState(
        targetValue = state.memory.usedRatio,
        animationSpec = spatial,
        label = "mem",
    )

    ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SectionHeader("状态监控", "每 2 秒轮询 /proc/stat 与 /proc/meminfo")
        Spacer(Modifier.height(16.dp))
        MetricBar("CPU 占用", cpuAnim, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        MetricBar("内存占用", memAnim, MaterialTheme.colorScheme.primary)
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
}

@Composable
private fun MetricBar(label: String, ratio: Float, accent: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = accent)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.6f), accent),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun WhitelistButton(state: UiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(
            "加入 Doze 白名单 · deviceidle whitelist",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EventLog(events: List<String>) {
    if (events.isEmpty()) return
    ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        SectionHeader("执行记录", "最近 8 条真实命令结果")
        Spacer(Modifier.height(12.dp))
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
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpressiveCard(
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
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
