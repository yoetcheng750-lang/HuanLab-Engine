package com.huanlab.engine.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 前台应用识别。
 *
 * 依赖 UsageStatsManager：没有「使用情况访问」权限时返回 Unknown，
 * 不做任何基于猜测的推断，也不回退到模拟数据。
 */
class ForegroundAppDetector(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun currentForeground(): Metric<String> {
        if (!hasUsageAccess()) {
            return Metric.Unknown("缺少「使用情况访问」权限")
        }
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Metric.Unknown("UsageStatsManager 不可用")
        val now = System.currentTimeMillis()
        val events = runCatching {
            manager.queryEvents(now - LOOKBACK_MS, now)
        }.getOrNull() ?: return Metric.Unknown("读取使用情况事件失败")

        var lastPackage: String? = null
        var lastAt = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp >= lastAt) {
                    lastAt = event.timeStamp
                    lastPackage = event.packageName
                }
            }
        }
        return if (lastPackage != null && lastPackage != context.packageName) {
            Metric.Known(lastPackage)
        } else {
            Metric.Unknown("最近窗口内无前台应用事件")
        }
    }

    private companion object {
        const val LOOKBACK_MS = 30_000L
    }
}
