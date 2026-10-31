package com.huanlab.engine.ui.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanlab.engine.service.AppLockState
import com.huanlab.engine.ui.AppControlUiState
import com.huanlab.engine.ui.ManagedAppUi
import com.huanlab.engine.ui.components.YoeAppIcon
import com.huanlab.engine.ui.components.YoeCard
import com.huanlab.engine.ui.components.YoeSectionTitle
import com.huanlab.engine.ui.components.YoeStatusTile

@Composable
fun AppControlScreen(
    state: AppControlUiState,
    onQuery: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBulk: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearMessage: () -> Unit,
) {
    var pendingBulk by remember { mutableStateOf<Boolean?>(null) }
    val query = state.query.trim()
    val apps = if (query.isEmpty()) {
        state.apps
    } else {
        state.apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "应用管控",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "仅列出普通用户应用 · 已锁定 ${state.lockedCount} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (state.busy) "执行中…" else "刷新",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !state.busy, onClick = onRefresh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YoeStatusTile(
                "Shizuku",
                if (state.shizukuReady) "就绪" else "未就绪",
                if (state.shizukuReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                Modifier.weight(1f),
            )
            YoeStatusTile(
                "待恢复",
                state.apps.count { it.state == AppLockState.PENDING }.toString(),
                MaterialTheme.colorScheme.tertiary,
                Modifier.weight(1f),
            )
            YoeStatusTile(
                "用户应用",
                state.apps.size.toString(),
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            label = { Text("搜索应用名或包名") },
            placeholder = { Text("例如 原神 / com.miHoYo") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionChip("全选当前列表", enabled = !state.busy && apps.isNotEmpty(), Modifier.weight(1f)) {
                pendingBulk = true
            }
            ActionChip("取消全选", enabled = !state.busy && apps.isNotEmpty(), Modifier.weight(1f)) {
                pendingBulk = false
            }
        }

        if (state.message != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearMessage),
            ) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        enabled = !state.busy,
                        onToggle = { locked -> onToggle(app.packageName, locked) },
                    )
                }
                item {
                    YoeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        YoeSectionTitle("说明", "后台限制会做什么")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "锁定后会执行 appops RUN_IN_BACKGROUND / WAKE_LOCK ignore 与 restricted 待机桶。" +
                                "这可能影响推送、同步、定位和音乐播放等后台任务；" +
                                "解除锁定时按记录的原值恢复。它不是彻底冻结进程。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    pendingBulk?.let { lock ->
        AlertDialog(
            onDismissRequest = { pendingBulk = null },
            title = { Text(if (lock) "批量锁定" else "批量解除") },
            text = {
                Text(
                    "将对当前过滤条件下 ${apps.size} 个普通用户应用执行${if (lock) "后台限制" else "解除限制"}，" +
                        "首批结果会汇总显示。确定继续？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulk = null
                    onBulk(lock)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulk = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(app: ManagedAppUi, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val stateColor = when (app.state) {
        AppLockState.LOCKED -> MaterialTheme.colorScheme.secondary
        AppLockState.PENDING -> MaterialTheme.colorScheme.tertiary
        AppLockState.FAILED -> MaterialTheme.colorScheme.error
        AppLockState.UNLOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YoeAppIcon(app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.state != AppLockState.UNLOCKED) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(stateColor))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (app.state) {
                                AppLockState.LOCKED -> "已锁定"
                                AppLockState.PENDING -> "等待 Shizuku 恢复"
                                AppLockState.FAILED -> "执行失败"
                                AppLockState.UNLOCKED -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor,
                        )
                    }
                }
            }
            Switch(
                checked = app.locked,
                onCheckedChange = onToggle,
                enabled = enabled,
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
