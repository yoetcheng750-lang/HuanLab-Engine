package com.huanlab.engine.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huanlab.engine.service.AppProfile
import com.huanlab.engine.service.AutomationOutcome
import com.huanlab.engine.service.Metric
import com.huanlab.engine.ui.AutomationUiState
import com.huanlab.engine.ui.ManagedAppUi
import com.huanlab.engine.ui.components.YoeCard
import com.huanlab.engine.ui.components.YoeSectionTitle
import com.huanlab.engine.ui.components.YoeStatusTile

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    apps: List<ManagedAppUi>,
    onToggle: (Boolean) -> Unit,
    onAddProfile: (String, String, Boolean, Int?) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onClearLogs: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动调度", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "热安全 > 用户开关 > 应用方案 > 默认均衡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }

        if (!state.usageAccess) {
            YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                YoeSectionTitle("缺少「使用情况访问」权限", "无法识别前台应用")
                Spacer(Modifier.height(8.dp))
                Text(
                    "请到系统设置 → 应用 → 特殊权限 → 使用情况访问，允许 YOET 访问。" +
                        "未授权时自动调度会显示「无法识别前台应用」，不会猜测或伪造场景。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.outcome?.let { CurrentStatusCard(it) }

        YoeCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YoeSectionTitle("应用方案", "为指定应用覆盖性能与刷新率")
                Spacer(Modifier.weight(1f))
                Text(
                    "新增",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(enabled = !state.busy && apps.isNotEmpty()) { showAddDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            if (state.profiles.isEmpty()) {
                Text(
                    "尚未配置任何应用方案。添加后，对应应用进入前台时会自动切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.forEach { profile ->
                        ProfileRow(profile = profile, onRemove = { onRemoveProfile(profile.packageName) })
                    }
                }
            }
        }

        YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YoeSectionTitle("策略执行记录", "最近 ${state.logs.size} 条")
                Spacer(Modifier.weight(1f))
                Text(
                    "清空",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clickable { onClearLogs() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.logs.isEmpty()) {
                Text(
                    "暂无记录。自动化触发后会在这里显示原因与结果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.logs.take(30).forEach { entry ->
                        Text(
                            entry.format(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.success == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            apps = apps,
            onDismiss = { showAddDialog = false },
            onConfirm = { pkg, label, performance, hz ->
                showAddDialog = false
                onAddProfile(pkg, label, performance, hz)
            },
        )
    }
}

@Composable
private fun CurrentStatusCard(outcome: AutomationOutcome) {
    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        YoeSectionTitle("当前策略", outcome.reason)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YoeStatusTile(
                "前台",
                when (val fg = outcome.foreground) {
                    is Metric.Known -> fg.value.substringAfterLast('.')
                    is Metric.Unknown -> "未知"
                },
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f),
            )
            YoeStatusTile(
                "性能",
                when (outcome.targetPerformance) {
                    true -> "电竞"
                    false -> "均衡"
                    null -> "—"
                },
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f),
            )
            YoeStatusTile(
                "刷新率",
                outcome.targetRefreshRate?.let { "${it}Hz" } ?: "不变",
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(outcome.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (outcome.verified == false) {
            Spacer(Modifier.height(6.dp))
            Text("回读校验不一致：系统实际值未达到目标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProfileRow(profile: AppProfile, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    buildString {
                        append(if (profile.performanceMode) "电竞性能" else "均衡")
                        append(" · ")
                        append(profile.refreshRate?.let { "${it}Hz" } ?: "刷新率不变")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(profile.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "删除",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
            )
        }
    }
}

@Composable
private fun AddProfileDialog(
    apps: List<ManagedAppUi>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, Int?) -> Unit,
) {
    var selected by remember { mutableStateOf<ManagedAppUi?>(null) }
    var performance by remember { mutableStateOf(true) }
    var overrideRefresh by remember { mutableStateOf(true) }
    var hz by remember { mutableFloatStateOf(120f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增应用方案") },
        text = {
            Column {
                Text("选择应用", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = app }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected?.packageName == app.packageName) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (selected?.packageName == app.packageName) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("电竞性能", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = performance, onCheckedChange = { performance = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("覆盖刷新率", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = overrideRefresh, onCheckedChange = { overrideRefresh = it })
                }
                if (overrideRefresh) {
                    Text("${hz.toInt()} Hz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = hz,
                        onValueChange = { hz = it },
                        valueRange = 60f..144f,
                        steps = 6,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    val app = selected ?: return@TextButton
                    onConfirm(
                        app.packageName,
                        app.label,
                        performance,
                        if (overrideRefresh) hz.toInt() else null,
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
