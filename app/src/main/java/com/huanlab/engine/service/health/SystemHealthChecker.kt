package com.huanlab.engine.service.health

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ShizukuPhase

/**
 * 系统健康检查：全部基于真实接口。
 * 核心文件完整性只能在构建期校验，运行时展示构建时固化的基线文件，不伪造运行时校验结果。
 */
class SystemHealthChecker(private val context: Context) {

    fun checkAppIntegrity(): List<HealthItem> {
        val items = ArrayList<HealthItem>()
        val packageName = context.packageName

        items += HealthItem(
            id = "app.id",
            title = "applicationId",
            state = if (packageName == EXPECTED_APP_ID) HealthState.PASS else HealthState.FAILED,
            detail = packageName,
            source = "PackageManager",
        )

        val info = runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        if (info == null) {
            items += HealthItem("app.version", "版本信息", HealthState.UNKNOWN, "无法读取 PackageInfo", "PackageManager")
        } else {
            items += HealthItem(
                "app.version",
                "版本",
                HealthState.PASS,
                "${info.versionName} (${info.versionCode})",
                "PackageManager",
            )
        }

        val signed = runCatching {
            val pkg = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            pkg.signingInfo?.apkContentsSigners?.isNotEmpty() == true ||
                pkg.signingInfo?.hasMultipleSigners() == true
        }.getOrDefault(false)
        items += HealthItem(
            "app.signing",
            "APK 签名",
            if (signed) HealthState.PASS else HealthState.UNKNOWN,
            if (signed) "已签名（不输出证书指纹）" else "无法确认签名信息",
            "PackageManager.signingInfo",
        )

        val baselineCount = runCatching {
            context.assets.open(CORE_BASELINE_ASSET).bufferedReader().useLines { lines ->
                lines.count { it.isNotBlank() }
            }
        }.getOrNull()
        items += HealthItem(
            "core.baseline",
            "核心文件基线",
            if (baselineCount == EXPECTED_CORE_COUNT) HealthState.PASS else HealthState.UNKNOWN,
            if (baselineCount == null) {
                "未找到构建基线"
            } else {
                "$baselineCount 个文件（构建期固化；运行时以 CI 校验为准）"
            },
            "assets/core_baseline.sha256",
        )
        return items
    }

    fun checkShizuku(engine: Engine): List<HealthItem> {
        val state = engine.shizukuState()
        val items = ArrayList<HealthItem>()
        val installed = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        items += HealthItem(
            "shizuku.installed",
            "Shizuku 安装",
            if (installed) HealthState.PASS else HealthState.UNSUPPORTED,
            if (installed) "已安装" else "未安装 Shizuku",
            "PackageManager",
        )
        val phase = state.phase
        items += HealthItem(
            "shizuku.service",
            "Shizuku 服务",
            when (phase) {
                ShizukuPhase.NOT_INSTALLED -> HealthState.UNSUPPORTED
                ShizukuPhase.NOT_RUNNING -> HealthState.DEGRADED
                ShizukuPhase.UNKNOWN -> HealthState.UNKNOWN
                else -> HealthState.PASS
            },
            state.detail,
            "Shizuku API",
        )
        items += HealthItem(
            "shizuku.identity",
            "执行身份",
            when (phase) {
                ShizukuPhase.CONNECTED_SHELL -> HealthState.PASS
                ShizukuPhase.CONNECTED_ROOT -> HealthState.PASS
                ShizukuPhase.NOT_AUTHORIZED -> HealthState.DEGRADED
                ShizukuPhase.NOT_RUNNING -> HealthState.DEGRADED
                ShizukuPhase.NOT_INSTALLED -> HealthState.UNSUPPORTED
                ShizukuPhase.UNKNOWN -> HealthState.UNKNOWN
            },
            state.detail,
            "Shizuku.getUid()",
        )
        return items
    }

    fun checkPermissions(): List<HealthItem> {
        val items = ArrayList<HealthItem>()

        val usageAccess = runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        items += HealthItem(
            "perm.usage",
            "使用情况访问",
            if (usageAccess) HealthState.PASS else HealthState.DEGRADED,
            if (usageAccess) "已授权" else "未授权：请到系统设置 → 特殊权限 → 使用情况访问 开启",
            "AppOpsManager",
        )

        val overlay = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        items += HealthItem(
            "perm.overlay",
            "悬浮窗权限",
            if (overlay) HealthState.PASS else HealthState.DEGRADED,
            if (overlay) "已授权" else "未授权：请到系统设置 → 特殊权限 → 显示在其他应用上层 开启",
            "Settings.canDrawOverlays",
        )

        val thermalSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        items += HealthItem(
            "api.thermal",
            "Thermal API",
            if (thermalSupported) HealthState.PASS else HealthState.UNSUPPORTED,
            if (thermalSupported) "支持 PowerManager.getCurrentThermalStatus" else "Android 10 以下不支持",
            "PowerManager",
        )

        if (thermalSupported) {
            val status = runCatching {
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                power.currentThermalStatus
            }.getOrNull()
            items += HealthItem(
                "thermal.status",
                "当前热状态",
                if (status != null) HealthState.PASS else HealthState.UNKNOWN,
                thermalName(status),
                "PowerManager.getCurrentThermalStatus",
            )
        }

        val fgsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        items += HealthItem(
            "api.fgs",
            "前台服务类型要求",
            if (fgsSupported) HealthState.DEGRADED else HealthState.UNSUPPORTED,
            if (fgsSupported) "Android 14+：悬浮窗服务需声明 specialUse 类型" else "无需声明特殊前台服务类型",
            "Build.VERSION.SDK_INT",
        )
        return items
    }

    private fun thermalName(status: Int?): String = when (status) {
        0 -> "NONE"
        1 -> "LIGHT"
        2 -> "MODERATE"
        3 -> "SEVERE"
        4 -> "CRITICAL"
        5 -> "EMERGENCY"
        6 -> "SHUTDOWN"
        else -> "—"
    }

    private companion object {
        const val EXPECTED_APP_ID = "com.huanlab.engine"
        const val CORE_BASELINE_ASSET = "core_baseline.sha256"
        const val EXPECTED_CORE_COUNT = 25
    }
}
