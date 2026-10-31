package com.huanlab.engine.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huanlab.engine.ui.screen.AppControlScreen
import com.huanlab.engine.ui.screen.AutomationScreen
import com.huanlab.engine.ui.screen.HealthScreen
import com.huanlab.engine.ui.screen.HomeScreen
import com.huanlab.engine.ui.screen.MonitorScreen
import com.huanlab.engine.ui.theme.HuanLabTheme
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
                val homeViewModel: HuanLabViewModel = viewModel()
                val controlViewModel: AppControlViewModel = viewModel()
                val automationViewModel: AutomationViewModel = viewModel()
                val healthViewModel: HealthViewModel = viewModel()
                YoeApp(
                    homeViewModel = homeViewModel,
                    controlViewModel = controlViewModel,
                    automationViewModel = automationViewModel,
                    healthViewModel = healthViewModel,
                    dynamicColor = dynamicColor,
                    dynamicColorSupported = supportsDynamicColor(),
                    onToggleDynamicColor = { enabled ->
                        dynamicColor = enabled
                        uiPreferences.dynamicColorEnabled = enabled
                    },
                )
            }
        }
    }
}

@Composable
private fun YoeApp(
    homeViewModel: HuanLabViewModel,
    controlViewModel: AppControlViewModel,
    automationViewModel: AutomationViewModel,
    healthViewModel: HealthViewModel,
    dynamicColor: Boolean,
    dynamicColorSupported: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val controlState by controlViewModel.state.collectAsStateWithLifecycle()
    val automationState by automationViewModel.state.collectAsStateWithLifecycle()
    val healthState by healthViewModel.state.collectAsStateWithLifecycle()
    val overlayRunning by healthViewModel.overlayRunning.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = Dp.Hairline,
            ) {
                listOf("首页" to "首", "管控" to "管", "自动" to "自", "监控" to "监", "自检" to "检")
                    .forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(item.second, style = MaterialTheme.typography.labelMedium) },
                            label = { Text(item.first, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tab) {
                0 -> HomeScreen(
                    state = homeState,
                    dynamicColor = dynamicColor,
                    dynamicColorSupported = dynamicColorSupported,
                    onToggleDynamicColor = onToggleDynamicColor,
                    onTogglePerformance = homeViewModel::togglePerformance,
                    onPreviewRefreshRate = homeViewModel::previewRefreshRate,
                    onApplyRefreshRate = homeViewModel::applyRefreshRate,
                    onRetryShizuku = homeViewModel::retryShizuku,
                    onWhitelistSelf = homeViewModel::whitelistSelf,
                    onOpenControl = { tab = 1 },
                )

                1 -> AppControlScreen(
                    state = controlState,
                    onQuery = controlViewModel::setQuery,
                    onToggle = controlViewModel::toggle,
                    onBulk = controlViewModel::applyBulk,
                    onRefresh = controlViewModel::refresh,
                    onClearMessage = controlViewModel::clearMessage,
                    onRestoreAll = controlViewModel::restoreAll,
                    onVerifyAll = controlViewModel::verifyAll,
                )

                2 -> AutomationScreen(
                    state = automationState,
                    apps = controlState.apps,
                    onToggle = automationViewModel::setEnabled,
                    onAddProfile = automationViewModel::upsertProfile,
                    onRemoveProfile = automationViewModel::removeProfile,
                    onClearLogs = automationViewModel::clearLogs,
                )

                3 -> MonitorScreen(state = homeState)

                else -> HealthScreen(
                    summary = healthState,
                    overlayRunning = overlayRunning,
                    canDrawOverlays = healthViewModel.canDrawOverlays(),
                    onRefresh = healthViewModel::refresh,
                    onExport = healthViewModel::export,
                    onStartOverlay = { healthViewModel.startOverlay() },
                    onStopOverlay = { healthViewModel.stopOverlay() },
                    onRequestOverlayPermission = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + context.packageName),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
        }
    }
}
