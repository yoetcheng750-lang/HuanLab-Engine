package com.huanlab.engine

import android.app.Application
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃黑匣子。
 *
 * 目标路径：/storage/emulated/0/YOET_crash.log（需要「所有文件访问」权限）。
 * 未授权时自动回退到应用外部私有目录，并在日志首行注明实际路径，绝不因写文件失败而二次崩溃。
 */
class YoeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val file = resolveCrashFile() ?: return
        val text = buildString {
            appendLine("=== YOET crash ${timestamp()} ===")
            appendLine("file: ${file.absolutePath}")
            appendLine("thread: ${thread.name}")
            appendLine(
                "device: ${Build.MANUFACTURER} ${Build.MODEL} / Android " +
                    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
            appendLine("app: $packageName")
            appendLine(throwable.stackTraceToString())
            appendLine()
        }
        rotateIfNeeded(file)
        file.appendText(text)
    }

    private fun resolveCrashFile(): File? {
        if (isPublicStorageWritable()) {
            val public = File(PUBLIC_LOG_PATH)
            if (canWrite(public)) return public
        }
        val dir = getExternalFilesDir(null) ?: filesDir
        val fallback = File(dir, FALLBACK_NAME)
        return if (canWrite(fallback)) fallback else null
    }

    private fun isPublicStorageWritable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }

    private fun canWrite(file: File): Boolean = runCatching {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
        file.canWrite()
    }.getOrDefault(false)

    private fun rotateIfNeeded(file: File) {
        runCatching {
            if (file.exists() && file.length() > MAX_BYTES) {
                val backup = File(file.parentFile, file.name + ".1")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private companion object {
        const val PUBLIC_LOG_PATH = "/storage/emulated/0/YOET_crash.log"
        const val FALLBACK_NAME = "YOET_crash.log"
        const val MAX_BYTES = 512L * 1024L
    }
}
