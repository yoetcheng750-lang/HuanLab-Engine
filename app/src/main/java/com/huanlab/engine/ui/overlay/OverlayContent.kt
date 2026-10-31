package com.huanlab.engine.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huanlab.engine.service.MemorySnapshot
import com.huanlab.engine.service.Metric
import com.huanlab.engine.service.sampling.SamplingState
import com.huanlab.engine.shared.ThermalLevel
import com.huanlab.engine.ui.theme.HuanLabTheme

@Composable
fun OverlayContent(
    state: SamplingState,
    expanded: Boolean,
    alpha: Float,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onCycleAlpha: () -> Unit,
) {
    HuanLabTheme(dynamicColor = false) {
        val cpu: Metric<Float> = state.snapshot?.cpuPercent ?: Metric.Unknown("待采样")
        val cpuUsagePercent = state.cpuUsage?.valuePercent
        val memory: Metric<MemorySnapshot> = state.snapshot?.memory ?: Metric.Unknown("待采样")
        val thermal: Metric<ThermalLevel> = state.snapshot?.thermal ?: Metric.Unknown("待采样")
        val actualHz: Metric<Float> = state.snapshot?.refreshRate?.actualHz ?: Metric.Unknown("待采样")
        val foreground = state.foreground

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            onDrag(drag.x, drag.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleExpand() },
                        onDoubleTap = { onCycleAlpha() },
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "YOET",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "CPU " + (cpuUsagePercent?.let { "%.1f%%".format(it) } ?: "—"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "✕",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (expanded) {
                Text(
                    "内存 ${memory.memPercentText()} · 热 ${thermal.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "刷新 ${actualHz.hzText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "前台 ${(foreground as? Metric.Known)?.value ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "FPS 需要 Shizuku 采集，见自检页能力状态",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "点按展开 · 双击调透明度",
                    style = MaterialTheme.typography.labelSmall,
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

private fun Metric<ThermalLevel>.label(): String = when (this) {
    is Metric.Known -> value.name
    is Metric.Unknown -> "—"
}

private fun Metric<Float>.hzText(): String = when (this) {
    is Metric.Known -> "${value.toInt()}Hz"
    is Metric.Unknown -> "—"
}
