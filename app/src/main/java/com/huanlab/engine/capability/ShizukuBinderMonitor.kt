package com.huanlab.engine.capability

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Shizuku Binder 事件测试缝（U11）。
 * 生产实现绑定 Shizuku 静态监听；测试实现可手工触发 received/dead 以覆盖状态机。
 */
interface ShizukuEventSource {
    fun registerBinderListeners(onReceived: () -> Unit, onDead: () -> Unit)
    fun unregisterBinderListeners()
    fun pingBinder(): Boolean
}

object DefaultShizukuEventSource : ShizukuEventSource {
    private var receivedListener: Shizuku.OnBinderReceivedListener? = null
    private var deadListener: Shizuku.OnBinderDeadListener? = null

    override fun registerBinderListeners(onReceived: () -> Unit, onDead: () -> Unit) {
        if (receivedListener != null) return

        val received = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() = onReceived()
        }
        val dead = object : Shizuku.OnBinderDeadListener {
            override fun onBinderDead() = onDead()
        }

        receivedListener = received
        deadListener = dead
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
    }

    override fun unregisterBinderListeners() {
        val received = receivedListener ?: return
        val dead = deadListener
        receivedListener = null
        deadListener = null
        runCatching { Shizuku.removeBinderReceivedListener(received) }
        if (dead != null) runCatching { Shizuku.removeBinderDeadListener(dead) }
    }

    override fun pingBinder(): Boolean = Shizuku.pingBinder()
}

sealed interface ShizukuMonitorState {
    data object Uninitialized : ShizukuMonitorState
    data object BinderAbsent : ShizukuMonitorState
    data object ConnectedUnverified : ShizukuMonitorState
    data object Revalidating : ShizukuMonitorState
    data class NotAuthorized(val reason: String) : ShizukuMonitorState {
        init { require(reason.isNotBlank()) }
    }
    data class Incompatible(val requiredVersion: Int, val actualVersion: Int) : ShizukuMonitorState
    data class Ready(
        val identity: IdentityResolution.Resolved,
        val apiVersion: Int,
    ) : ShizukuMonitorState
    data object Dead : ShizukuMonitorState
    data class WaitingBinder(val deadlineElapsedMillis: Long) : ShizukuMonitorState
    data class NeedsManual(val reason: String) : ShizukuMonitorState {
        init { require(reason.isNotBlank()) }
    }
    data class Unknown(val reason: String) : ShizukuMonitorState {
        init { require(reason.isNotBlank()) }
    }
}

fun interface EpochObserver {
    fun onEpochChanged(previousEpoch: Long, newEpoch: Long, reason: String)
}

data class ShizukuMonitorSnapshot(
    val epoch: Long,
    val state: ShizukuMonitorState,
    val updatedAtElapsedMillis: Long,
)

/**
 * Binder Death 状态机。
 * 规则：死亡立即 epoch++ 并作废所有快照；恢复后必须重验版本/授权/身份；
 * 重连等待只开一次期限，超时进入 NEEDS_MANUAL；不自动轮询、不自动重放事务。
 */
class ShizukuBinderMonitor(
    private val scope: CoroutineScope,
    private val revalidator: ShizukuStateRevalidator,
    private val timeSource: TimeSource,
    private val eventSource: ShizukuEventSource = DefaultShizukuEventSource,
) {
    private val mutex = Mutex()
    private val epochCounter = AtomicLong(0L)
    private val observers = CopyOnWriteArrayList<EpochObserver>()

    private var state: ShizukuMonitorState = ShizukuMonitorState.Uninitialized
    private var updatedAtElapsedMillis: Long = 0L
    private var listenersRegistered = false
    private var hasSeenBinder = false
    private var reconnectTimeoutJob: Job? = null
    private var verificationJob: Job? = null

    fun start() {
        if (listenersRegistered) return
        listenersRegistered = true
        try {
            eventSource.registerBinderListeners(
                onReceived = { scope.launch { onBinderReceived() } },
                onDead = { scope.launch { onBinderDead() } },
            )
        } catch (throwable: Throwable) {
            scope.launch {
                mutex.withLock {
                    updateLocked(
                        ShizukuMonitorState.Unknown(
                            "MONITOR_REGISTRATION_FAILED: ${throwable.javaClass.simpleName}",
                        ),
                    )
                }
            }
        }
        scope.launch { seedInitialStateIfNeeded() }
    }

    fun close() {
        if (!listenersRegistered) return
        listenersRegistered = false
        runCatching { eventSource.unregisterBinderListeners() }
        reconnectTimeoutJob?.cancel()
        verificationJob?.cancel()
    }

    fun addEpochObserver(observer: EpochObserver) {
        observers.addIfAbsent(observer)
    }

    fun removeEpochObserver(observer: EpochObserver) {
        observers.remove(observer)
    }

    fun currentEpoch(): Long = epochCounter.get()

    suspend fun currentSnapshot(): ShizukuMonitorSnapshot = mutex.withLock {
        ShizukuMonitorSnapshot(epochCounter.get(), state, updatedAtElapsedMillis)
    }

    /**
     * 用户/UI 显式触发的重新检测；不自动循环。
     */
    suspend fun retry() {
        val snapshot = currentSnapshot()
        val alive: Boolean? = try {
            withContext(Dispatchers.IO) {
                withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                    eventSource.pingBinder()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

        when (alive) {
            true -> when (snapshot.state) {
                is ShizukuMonitorState.Ready,
                is ShizukuMonitorState.NotAuthorized,
                is ShizukuMonitorState.Incompatible -> {
                    mutex.withLock {
                        verificationJob?.cancel()
                        updateLocked(ShizukuMonitorState.Revalidating)
                    }
                    verificationJob = scope.launch { runVerification(snapshot.epoch) }
                }
                is ShizukuMonitorState.ConnectedUnverified,
                is ShizukuMonitorState.Revalidating -> Unit
                is ShizukuMonitorState.Uninitialized,
                is ShizukuMonitorState.BinderAbsent,
                is ShizukuMonitorState.Dead,
                is ShizukuMonitorState.WaitingBinder,
                is ShizukuMonitorState.NeedsManual,
                is ShizukuMonitorState.Unknown -> onBinderReceived()
            }
            false -> when (snapshot.state) {
                is ShizukuMonitorState.Ready,
                is ShizukuMonitorState.NotAuthorized,
                is ShizukuMonitorState.Incompatible,
                is ShizukuMonitorState.ConnectedUnverified,
                is ShizukuMonitorState.Revalidating -> onBinderDead()
                is ShizukuMonitorState.Uninitialized,
                is ShizukuMonitorState.BinderAbsent,
                is ShizukuMonitorState.Dead,
                is ShizukuMonitorState.WaitingBinder,
                is ShizukuMonitorState.NeedsManual,
                is ShizukuMonitorState.Unknown -> scheduleWaitingWindow()
            }
            null -> mutex.withLock {
                updateLocked(
                    ShizukuMonitorState.Unknown("RECHECK_BINDER_PING_UNREADABLE"),
                )
            }
        }
    }

    private suspend fun seedInitialStateIfNeeded() {
        delay(0)
        val stillUninitialized = mutex.withLock {
            state is ShizukuMonitorState.Uninitialized
        }
        if (!stillUninitialized) return

        val alive: Boolean? = try {
            withContext(Dispatchers.IO) {
                withTimeout(CapabilityConstants.BINDER_PING_TIMEOUT_MS) {
                    eventSource.pingBinder()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

        when (alive) {
            true -> onBinderReceived()
            false -> mutex.withLock {
                if (state is ShizukuMonitorState.Uninitialized) {
                    updateLocked(ShizukuMonitorState.BinderAbsent)
                }
            }
            null -> mutex.withLock {
                if (state is ShizukuMonitorState.Uninitialized) {
                    updateLocked(
                        ShizukuMonitorState.Unknown("INITIAL_BINDER_PROBE_FAILED"),
                    )
                }
            }
        }
    }

    private suspend fun onBinderReceived() {
        var accepted = false
        var previousEpoch = 0L
        var newEpoch = 0L
        var reconnect = false

        mutex.withLock {
            when (state) {
                is ShizukuMonitorState.Ready,
                is ShizukuMonitorState.NotAuthorized,
                is ShizukuMonitorState.Incompatible,
                is ShizukuMonitorState.ConnectedUnverified,
                is ShizukuMonitorState.Revalidating -> Unit
                else -> {
                    accepted = true
                    previousEpoch = epochCounter.get()
                    newEpoch = epochCounter.incrementAndGet()
                    reconnect = hasSeenBinder
                    hasSeenBinder = true
                    reconnectTimeoutJob?.cancel()
                    verificationJob?.cancel()
                    updateLocked(
                        if (reconnect) {
                            ShizukuMonitorState.Revalidating
                        } else {
                            ShizukuMonitorState.ConnectedUnverified
                        },
                    )
                }
            }
        }

        if (!accepted) return
        notifyEpochChanged(
            previousEpoch,
            newEpoch,
            if (reconnect) "BINDER_RECEIVED_AFTER_DISCONNECT" else "BINDER_RECEIVED_INITIAL",
        )
        verificationJob = scope.launch { runVerification(newEpoch) }
    }

    private suspend fun onBinderDead() {
        val previousEpoch = epochCounter.get()
        val newEpoch = epochCounter.incrementAndGet()
        val deadline = timeSource.elapsedRealtimeMillis() +
            CapabilityConstants.RECONNECT_TIMEOUT_MS

        mutex.withLock {
            reconnectTimeoutJob?.cancel()
            verificationJob?.cancel()
            updateLocked(ShizukuMonitorState.Dead)
        }
        notifyEpochChanged(previousEpoch, newEpoch, "BINDER_DIED")

        mutex.withLock {
            if (epochCounter.get() == newEpoch) {
                updateLocked(ShizukuMonitorState.WaitingBinder(deadline))
            }
        }
        scheduleReconnectTimeout(newEpoch)
    }

    private suspend fun runVerification(expectedEpoch: Long) {
        val result = try {
            revalidator.revalidate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            ShizukuRevalidation.Unreadable(
                "REVALIDATION_THREW: ${throwable.javaClass.simpleName}",
            )
        }

        mutex.withLock {
            if (epochCounter.get() == expectedEpoch) {
                val newState = when (result) {
                    is ShizukuRevalidation.Ready -> ShizukuMonitorState.Ready(
                        result.identity,
                        result.apiVersion,
                    )
                    is ShizukuRevalidation.NotAuthorized -> ShizukuMonitorState.NotAuthorized(
                        result.reason,
                    )
                    is ShizukuRevalidation.Incompatible -> ShizukuMonitorState.Incompatible(
                        result.requiredVersion,
                        result.actualVersion,
                    )
                    is ShizukuRevalidation.Unreadable -> ShizukuMonitorState.Unknown(
                        result.reason,
                    )
                }
                updateLocked(newState)
            }
        }
    }

    private suspend fun scheduleWaitingWindow() {
        val deadline = timeSource.elapsedRealtimeMillis() +
            CapabilityConstants.RECONNECT_TIMEOUT_MS
        mutex.withLock {
            reconnectTimeoutJob?.cancel()
            updateLocked(ShizukuMonitorState.WaitingBinder(deadline))
        }
        scheduleReconnectTimeout(epochCounter.get())
    }

    private fun scheduleReconnectTimeout(epoch: Long) {
        val job = scope.launch {
            delay(CapabilityConstants.RECONNECT_TIMEOUT_MS)
            mutex.withLock {
                if (
                    epochCounter.get() == epoch &&
                    state is ShizukuMonitorState.WaitingBinder
                ) {
                    updateLocked(
                        ShizukuMonitorState.NeedsManual(
                            "RECONNECT_TIMEOUT: binder 未在 " +
                                "${CapabilityConstants.RECONNECT_TIMEOUT_MS}ms 内恢复，需要用户手动检查",
                        ),
                    )
                }
            }
        }
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = job
    }

    private fun notifyEpochChanged(previous: Long, current: Long, reason: String) {
        observers.forEach { observer ->
            try {
                observer.onEpochChanged(previous, current, reason)
            } catch (_: Throwable) {
                // 观察者异常不得破坏 B 的状态机；D/E 自行负责其失败处理。
            }
        }
    }

    private fun updateLocked(newState: ShizukuMonitorState) {
        state = newState
        updatedAtElapsedMillis = timeSource.elapsedRealtimeMillis()
    }
}
