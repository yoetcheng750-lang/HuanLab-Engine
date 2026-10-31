package com.huanlab.engine.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huanlab.engine.core.Engine
import com.huanlab.engine.service.EngineProvider
import com.huanlab.engine.core.ShizukuPhase
import com.huanlab.engine.service.AppControlManager
import com.huanlab.engine.service.AppLockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ManagedAppUi(
    val packageName: String,
    val label: String,
    val locked: Boolean,
    val state: AppLockState,
)

data class AppControlUiState(
    val apps: List<ManagedAppUi> = emptyList(),
    val query: String = "",
    val lockedCount: Int = 0,
    val busy: Boolean = false,
    val loading: Boolean = true,
    val shizukuReady: Boolean = false,
    val shizukuDetail: String = "",
    val message: String? = null,
)

class AppControlViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: Engine = EngineProvider.get(application)
    private val manager = AppControlManager(application, engine)

    private val _state = MutableStateFlow(AppControlUiState())
    val state: StateFlow<AppControlUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val apps = manager.listUserApps().map { app ->
                    val lockState = manager.stateOf(app.packageName)
                    ManagedAppUi(
                        packageName = app.packageName,
                        label = app.label,
                        locked = lockState != AppLockState.UNLOCKED,
                        state = lockState,
                    )
                }
                val shizuku = engine.shizukuState()
                Triple(apps, shizuku, manager.lockedCount())
            }
            _state.update {
                it.copy(
                    apps = snapshot.first,
                    lockedCount = snapshot.third,
                    shizukuReady = snapshot.second.phase == ShizukuPhase.CONNECTED_SHELL ||
                        snapshot.second.phase == ShizukuPhase.CONNECTED_ROOT,
                    shizukuDetail = snapshot.second.detail,
                    loading = false,
                )
            }
            runCatching { manager.reconcilePending() }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggle(packageName: String, locked: Boolean) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val outcome = manager.setLocked(packageName, locked)
            applyOutcome(packageName, outcome.state, outcome.message)
            _state.update { it.copy(busy = false, lockedCount = manager.lockedCount()) }
            refreshSilently()
        }
    }

    fun applyBulk(lock: Boolean) {
        if (_state.value.busy) return
        val targets = filteredApps().map { it.packageName }
        if (targets.isEmpty()) {
            _state.update { it.copy(message = "当前过滤条件下没有可操作的应用") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            var ok = 0
            var pending = 0
            var failed = 0
            for (packageName in targets) {
                val outcome = manager.setLocked(packageName, lock)
                when (outcome.state) {
                    AppLockState.LOCKED, AppLockState.UNLOCKED -> ok++
                    AppLockState.PENDING -> pending++
                    AppLockState.FAILED, AppLockState.DRIFTED, AppLockState.RESTORING -> failed++
                }
                applyOutcome(packageName, outcome.state, null)
            }
            _state.update {
                it.copy(
                    busy = false,
                    lockedCount = manager.lockedCount(),
                    message = "批量${if (lock) "锁定" else "解除"}：成功 $ok，等待 Shizuku $pending，失败 $failed",
                )
            }
            refreshSilently()
        }
    }

    fun restoreAll() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val outcomes = manager.restoreAll()
            val ok = outcomes.count { it.state == AppLockState.UNLOCKED }
            val pending = outcomes.count { it.state == AppLockState.PENDING }
            val failed = outcomes.size - ok - pending
            _state.update {
                it.copy(
                    busy = false,
                    lockedCount = manager.lockedCount(),
                    message = "一键恢复：成功 $ok，等待 Shizuku $pending，失败 $failed",
                )
            }
            refreshSilently()
        }
    }

    fun verifyAll() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val targets = _state.value.apps.filter { it.locked }
            var drifted = 0
            var pending = 0
            for (app in targets) {
                val state = manager.verify(app.packageName)
                when (state) {
                    AppLockState.DRIFTED -> drifted++
                    AppLockState.PENDING -> pending++
                    else -> Unit
                }
                applyOutcome(app.packageName, state, null)
            }
            _state.update {
                it.copy(
                    busy = false,
                    message = "一致性检查：扫描 ${targets.size} 个，漂移 $drifted，待 Shizuku $pending",
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun filteredApps(): List<ManagedAppUi> {
        val query = _state.value.query.trim()
        val apps = _state.value.apps
        if (query.isEmpty()) return apps
        return apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun applyOutcome(packageName: String, state: AppLockState, message: String?) {
        _state.update { current ->
            current.copy(
                apps = current.apps.map {
                    if (it.packageName == packageName) {
                        it.copy(locked = state != AppLockState.UNLOCKED, state = state)
                    } else {
                        it
                    }
                },
                message = message ?: current.message,
            )
        }
    }

    private fun refreshSilently() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                manager.listUserApps().map { app ->
                    val lockState = manager.stateOf(app.packageName)
                    ManagedAppUi(app.packageName, app.label, lockState != AppLockState.UNLOCKED, lockState)
                } to manager.lockedCount()
            }
            _state.update {
                it.copy(apps = snapshot.first, lockedCount = snapshot.second)
            }
        }
    }
}
