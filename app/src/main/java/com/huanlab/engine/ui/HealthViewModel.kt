package com.huanlab.engine.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.huanlab.engine.core.Engine
import com.huanlab.engine.service.EngineProvider
import com.huanlab.engine.service.health.DiagnosticReport
import com.huanlab.engine.service.health.HealthItem
import com.huanlab.engine.service.health.HealthState
import com.huanlab.engine.service.health.HealthSummary
import com.huanlab.engine.service.health.SystemHealthChecker
import com.huanlab.engine.service.overlay.OverlayController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: Engine = EngineProvider.get(application)
    private val checker = SystemHealthChecker(application)
    private val report = DiagnosticReport(application)

    private val _state = MutableStateFlow(emptySummary())
    val state: StateFlow<HealthSummary> = _state.asStateFlow()

    private val _overlayRunning = MutableStateFlow(false)
    val overlayRunning: StateFlow<Boolean> = _overlayRunning.asStateFlow()

    init {
        refresh()
    }

    fun refreshOverlayState() {
        _overlayRunning.value = OverlayController.isRunning()
    }

    fun canDrawOverlays(): Boolean = OverlayController.canDrawOverlays(getApplication())

    fun startOverlay(): Boolean {
        val started = OverlayController.start(getApplication())
        refreshOverlayState()
        return started
    }

    fun stopOverlay() {
        OverlayController.stop(getApplication())
        refreshOverlayState()
    }

    fun refresh() {
        val items = ArrayList<HealthItem>()
        items += checker.checkAppIntegrity()
        items += checker.checkShizuku(engine)
        items += checker.checkPermissions()
        _state.value = HealthSummary.aggregate(items, System.currentTimeMillis())
    }

    fun export(): Uri? = runCatching { report.write(_state.value) }.getOrNull()

    private fun emptySummary(): HealthSummary = HealthSummary.aggregate(
        listOf(
            HealthItem("init", "初始化", HealthState.UNKNOWN, "尚未执行检查", "local"),
        ),
        System.currentTimeMillis(),
    )
}
