package com.huanlab.engine.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.provider.AlarmClock
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.provider.Telephony
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

enum class ProtectionReason(val label: String) {
    SELF("YOET 自身"),
    SHIZUKU("Shizuku"),
    SYSTEM_APP("系统应用"),
    UPDATED_SYSTEM_APP("更新的系统应用"),
    HOME_LAUNCHER("系统桌面"),
    DIALER("电话/拨号"),
    SYSTEM_UI("系统界面"),
    INPUT_METHOD("输入法"),
    ACCESSIBILITY("无障碍服务"),
    VPN("VPN 服务"),
    ALARM("闹钟"),
    CRITICAL_PACKAGE("系统关键组件"),
}

/**
 * 强健的应用保护网关：不依赖单一 FLAG，而是组合静态标记 + 系统角色解析。
 * 只有全部检查通过（返回 null）的应用才允许出现在管控列表并可被锁定。
 */
class ProtectedApps(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val dynamicPackages: Set<String> by lazy { resolveDynamicProtectedPackages() }

    fun reasonFor(packageName: String, info: ApplicationInfo): ProtectionReason? {
        if (packageName == context.packageName) return ProtectionReason.SELF
        if (packageName in SHIZUKU_PACKAGES) return ProtectionReason.SHIZUKU
        if (packageName in CRITICAL_PACKAGES) return ProtectionReason.CRITICAL_PACKAGE
        if (packageName == SYSTEM_UI_PACKAGE) return ProtectionReason.SYSTEM_UI
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return ProtectionReason.SYSTEM_APP
        if ((info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            return ProtectionReason.UPDATED_SYSTEM_APP
        }
        if (packageName in dynamicPackages) {
            return when {
                packageName in launcherPackages -> ProtectionReason.HOME_LAUNCHER
                packageName in dialerPackages -> ProtectionReason.DIALER
                packageName in inputMethodPackages -> ProtectionReason.INPUT_METHOD
                packageName in accessibilityPackages -> ProtectionReason.ACCESSIBILITY
                packageName in vpnPackages -> ProtectionReason.VPN
                packageName in alarmPackages -> ProtectionReason.ALARM
                else -> ProtectionReason.CRITICAL_PACKAGE
            }
        }
        return null
    }

    private val launcherPackages: Set<String> by lazy {
        queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private val dialerPackages: Set<String> by lazy {
        queryActivities(Intent(Intent.ACTION_DIAL)) +
            queryActivities(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)) +
            queryActivities(Intent(Intent.ACTION_CALL))
    }

    private val inputMethodPackages: Set<String> by lazy {
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet() +
                imm.inputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private val accessibilityPackages: Set<String> by lazy {
        runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private val vpnPackages: Set<String> by lazy {
        runCatching {
            packageManager.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private val alarmPackages: Set<String> by lazy {
        queryActivities(Intent(AlarmClock.ACTION_SET_ALARM)) +
            queryActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    private fun resolveDynamicProtectedPackages(): Set<String> =
        launcherPackages + dialerPackages + inputMethodPackages +
            accessibilityPackages + vpnPackages + alarmPackages

    private fun queryActivities(intent: Intent): Set<String> = runCatching {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    companion object {
        private val SHIZUKU_PACKAGES = setOf(
            "moe.shizuku.privileged.api",
            "moe.shizuku.manager",
            "rikka.shizuku",
        )
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private val CRITICAL_PACKAGES = setOf(
            "android",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.shell",
        )
    }
}
