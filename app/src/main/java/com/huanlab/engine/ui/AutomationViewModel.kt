package com.huanlab.engine.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huanlab.engine.core.Engine
import com.huanlab.engine.service.AppProfile
import com.huanlab.engine.service.AppProfileStore
import com.huanlab.engine.service.AutomationEngine
import com.huanlab.engine.service.AutomationOutcome
import com.huanlab.engine.service.AutomationSettingsStore
import com.huanlab.engine.service.DeviceMonitor
import com.huanlab.engine.service.EngineProvider
import com.huanlab.engine.service.ForegroundAppDetector
import com.huanlab.engine.service.StrategyLogEntry
import com.huanlab.engine.service.StrategyLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AutomationUiState(
    val enabled: Boolean = false,
    val usageAccess: Boolean = false,
    val outcome: AutomationOutcome? = null,
    val profiles: List<AppProfile> = emptyList(),
    val logs: List<StrategyLogEntry> = emptyList(),
    val busy: Boolean = false,
)

class AutomationViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: Engine = EngineProvider.get(application)
    private val settings = AutomationSettingsStore(application)
    private val profiles = AppProfileStore(application)
    private val foreground = ForegroundAppDetector(application)
    private val logStore = StrategyLogStore()
    private val automation = AutomationEngine(
        engine = engine,
        monitor = DeviceMonitor(application),
        profiles = profiles,
        foreground = foreground,
        settings = settings,
        log = logStore,
    )

    private val _state = MutableStateFlow(
        AutomationUiState(
            enabled = settings.enabled,
            usageAccess = foreground.hasUsageAccess(),
            profiles = profiles.all(),
        ),
    )
    val state: StateFlow<AutomationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loop() }
    }

    private suspend fun loop() {
        while (true) {
            if (_state.value.enabled) {
                val outcome = withContext(Dispatchers.IO) { automation.tick() }
                _state.update {
                    it.copy(
                        outcome = outcome,
                        logs = logStore.entries.value,
                        usageAccess = foreground.hasUsageAccess(),
                    )
                }
            } else {
                _state.update { it.copy(logs = logStore.entries.value) }
            }
            delay(TICK_INTERVAL_MS)
        }
    }

    fun setEnabled(enabled: Boolean) {
        settings.enabled = enabled
        automation.resetDebounce()
        logStore.record("AUTO", if (enabled) "自动调度已开启" else "自动调度已关闭", success = true)
        _state.update {
            it.copy(
                enabled = enabled,
                outcome = if (enabled) it.outcome else null,
                logs = logStore.entries.value,
            )
        }
    }

    fun upsertProfile(packageName: String, label: String, performance: Boolean, refreshRate: Int?) {
        profiles.upsert(
            AppProfile(
                packageName = packageName,
                label = label,
                performanceMode = performance,
                refreshRate = refreshRate,
                enabled = true,
            ),
        )
        automation.resetDebounce()
        logStore.record("PROFILE", "更新方案：$label（性能=$performance，刷新率=${refreshRate ?: "不变"}）", true)
        _state.update { it.copy(profiles = profiles.all(), logs = logStore.entries.value) }
    }

    fun removeProfile(packageName: String) {
        val label = profiles.get(packageName)?.label ?: packageName
        profiles.delete(packageName)
        automation.resetDebounce()
        logStore.record("PROFILE", "删除方案：$label", true)
        _state.update { it.copy(profiles = profiles.all(), logs = logStore.entries.value) }
    }

    fun refresh() {
        _state.update {
            it.copy(
                profiles = profiles.all(),
                usageAccess = foreground.hasUsageAccess(),
                logs = logStore.entries.value,
            )
        }
    }

    fun clearLogs() {
        logStore.clear()
        _state.update { it.copy(logs = emptyList()) }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 5_000L
    }
}
