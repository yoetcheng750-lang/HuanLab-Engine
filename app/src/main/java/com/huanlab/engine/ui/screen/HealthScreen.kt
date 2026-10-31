package com.huanlab.engine.ui.screen

import android.content.Intent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huanlab.engine.service.health.HealthItem
import com.huanlab.engine.service.health.HealthState
import com.huanlab.engine.service.health.HealthSummary
import com.huanlab.engine.ui.components.YoeCard
import com.huanlab.engine.ui.components.YoeSectionTitle

@Composable
fun HealthScreen(
    summary: HealthSummary,
    overlayRunning: Boolean,
    canDrawOverlays: Boolean,
    onRefresh: () -> Unit,
    onExport: () -> android.net.Uri?,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("系统自检", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "基于真实系统接口与构建期基线，不伪造结论",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(stateColor(summary.overall)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("整体状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary.overall.name, style = MaterialTheme.typography.headlineSmall, color = stateColor(summary.overall))
                }
                Text(
                    "重新检查",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            YoeSectionTitle("性能悬浮窗", "前台服务 + WindowManager，关闭后立即停止采样")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (overlayRunning) Color(0xFF4FD1A5) else Color(0xFF8E97AD),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !canDrawOverlays -> "未授予悬浮窗权限"
                        overlayRunning -> "运行中"
                        else -> "已停止"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        !canDrawOverlays -> "去授权"
                        overlayRunning -> "关闭"
                        else -> "启动"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            when {
                                !canDrawOverlays -> onRequestOverlayPermission()
                                overlayRunning -> onStopOverlay()
                                else -> onStartOverlay()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "悬浮窗只展示真实采样数据；FPS 需在 Shizuku 就绪后由采样引擎单独获取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        summary.items.groupBy { groupOf(it.id) }.forEach { (group, items) ->
            YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                YoeSectionTitle(group, "${items.size} 项")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { item -> HealthRow(item) }
                }
            }
        }

        Button(
            onClick = {
                val uri = onExport()
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "导出诊断报告")) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("导出诊断报告", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "报告仅含白名单字段：版本、Android 信息、Shizuku 状态、权限与热状态，不含任何凭据或证书指纹。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthRow(item: HealthItem) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(stateColor(item.state)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(item.state.name, style = MaterialTheme.typography.labelSmall, color = stateColor(item.state))
            }
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("来源：${item.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun groupOf(id: String): String = when {
    id.startsWith("app.") || id.startsWith("core.") -> "工程完整性"
    id.startsWith("shizuku.") -> "Shizuku"
    else -> "权限与系统能力"
}

private fun stateColor(state: HealthState): Color = when (state) {
    HealthState.PASS -> Color(0xFF4FD1A5)
    HealthState.DEGRADED -> Color(0xFFFBBF24)
    HealthState.UNSUPPORTED -> Color(0xFF8E97AD)
    HealthState.UNKNOWN -> Color(0xFFA78BFA)
    HealthState.FAILED -> Color(0xFFFF8A8A)
}
