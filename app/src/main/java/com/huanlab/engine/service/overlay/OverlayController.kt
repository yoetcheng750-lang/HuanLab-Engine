package com.huanlab.engine.service.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** 悬浮窗生命周期入口：权限检查、启动、停止、运行状态。 */
object OverlayController {

    @Volatile
    private var running = false

    fun isRunning(): Boolean = running

    fun setRunning(value: Boolean) {
        running = value
    }

    fun canDrawOverlays(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    fun start(context: Context): Boolean {
        if (!canDrawOverlays(context)) return false
        val intent = Intent(context, OverlayService::class.java)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            true
        }.getOrDefault(false)
    }

    fun stop(context: Context) {
        runCatching { context.startService(Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }) }
        runCatching { context.stopService(Intent(context, OverlayService::class.java)) }
        running = false
    }
}
