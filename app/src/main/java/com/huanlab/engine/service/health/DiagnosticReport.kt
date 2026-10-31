package com.huanlab.engine.service.health

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断报告：仅按白名单字段生成，绝不包含 Token、证书指纹、签名文件、账号凭证或完整文件系统路径。
 */
class DiagnosticReport(private val context: Context) {

    fun write(summary: HealthSummary): Uri {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "YOET_diagnostics_$stamp.txt")
        file.writeText(build(summary))
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun build(summary: HealthSummary): String {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        }.getOrDefault("UNVERIFIED")

        return buildString {
            appendLine("YOET 诊断报告")
            appendLine("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(summary.generatedAtMillis))}")
            appendLine("applicationId: ${context.packageName}")
            appendLine("应用版本: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("安全补丁: ${Build.VERSION.SECURITY_PATCH.ifBlank { "UNVERIFIED" }}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("整体状态: ${summary.overall}")
            appendLine()
            appendLine("== 检查项 ==")
            summary.items.forEach { item ->
                appendLine("[${item.state}] ${item.title}: ${item.detail}  <- ${item.source}")
            }
            appendLine()
            appendLine("== 说明 ==")
            appendLine("1. 本报告仅包含白名单字段，不含 Token、证书指纹、签名文件内容或完整文件路径。")
            appendLine("2. 核心文件完整性由构建期 CI 校验；运行时仅展示固化基线数量。")
            appendLine("3. 未验证的设备能力会明确标记 UNKNOWN / UNSUPPORTED。")
        }
    }
}
