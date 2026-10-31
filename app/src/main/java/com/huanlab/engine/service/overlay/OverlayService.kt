package com.huanlab.engine.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.huanlab.engine.service.sampling.SamplingCoordinator
import com.huanlab.engine.service.sampling.SamplingCoordinatorProvider
import com.huanlab.engine.ui.overlay.OverlayContent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 性能悬浮窗前台服务。
 *
 * 生命周期：权限检查 → 前台通知 → WindowManager 挂载 → 采样订阅。
 * 关闭流程严格按顺序释放：采样引用 → 移除窗口 → 销毁 lifecycle → 停通知 → stopSelf。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: OverlayPreferences
    private lateinit var owner: OverlayOwner
    private lateinit var sampling: SamplingCoordinator
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val expandedState = MutableStateFlow(true)
    private var shuttingDown = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = OverlayPreferences(this)
        owner = OverlayOwner().apply {
            create()
            resume()
        }
        sampling = SamplingCoordinatorProvider.get(this).also { it.acquire() }
        startForegroundSafe()
        attachOverlay()
        OverlayController.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clampPosition()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun attachOverlay() {
        val metrics = resources.displayMetrics
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.x.coerceIn(0, metrics.widthPixels)
            y = prefs.y.coerceIn(0, metrics.heightPixels)
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val state by sampling.state.collectAsState()
                val expanded by expandedState.collectAsState()
                OverlayContent(
                    state = state,
                    expanded = expanded,
                    alpha = prefs.alpha,
                    onToggleExpand = {
                        val next = !expandedState.value
                        expandedState.value = next
                        prefs.expanded = next
                    },
                    onClose = { shutdown() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { persistAndSnap() },
                    onCycleAlpha = {
                        val next = when {
                            prefs.alpha > 0.8f -> 0.65f
                            prefs.alpha > 0.6f -> 0.45f
                            else -> 0.88f
                        }
                        prefs.alpha = next
                        composeView?.let { windowManager.updateViewLayout(it, params) }
                    },
                )
            }
        }
        composeView = view
        expandedState.value = prefs.expanded
        windowManager.addView(view, params)
    }

    private fun moveBy(dx: Float, dy: Float) {
        val metrics = resources.displayMetrics
        val view = composeView ?: return
        val width = if (view.width > 0) view.width else 320
        val height = if (view.height > 0) view.height else 160
        params.x = (params.x + dx.toInt()).coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        params.y = (params.y + dy.toInt()).coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun persistAndSnap() {
        val metrics = resources.displayMetrics
        val view = composeView ?: return
        val width = if (view.width > 0) view.width else 320
        val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
        params.x = if (params.x + width / 2 < metrics.widthPixels / 2) 0 else maxX
        prefs.x = params.x
        prefs.y = params.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun clampPosition() {
        val view = composeView ?: return
        val metrics = resources.displayMetrics
        val width = if (view.width > 0) view.width else 320
        val height = if (view.height > 0) view.height else 160
        params.x = params.x.coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
        prefs.x = params.x
        prefs.y = params.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun startForegroundSafe() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "性能悬浮窗",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "YOET 性能悬浮窗运行状态"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("YOET 性能悬浮窗")
            .setContentText("正在显示真实设备指标，点击关闭即可停止采样")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "关闭悬浮窗", stopIntent)
            .build()
    }

    private fun shutdown() {
        if (shuttingDown) return
        shuttingDown = true
        OverlayController.setRunning(false)
        runCatching { sampling.release() }
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        composeView = null
        runCatching { owner.destroy() }
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.huanlab.engine.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "yoe_overlay"
        private const val NOTIFICATION_ID = 4101
    }
}
