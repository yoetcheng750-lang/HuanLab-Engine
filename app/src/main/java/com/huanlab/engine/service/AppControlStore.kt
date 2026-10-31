package com.huanlab.engine.service

import android.content.Context
import org.json.JSONObject

/** 用户主动锁定时记录的原始系统状态，解除时用于尽量还原。 */
data class OriginalState(
    val runInBackground: String?,
    val wakeLock: String?,
    val standbyBucket: Int?,
)

/** 后台管控的持久化：只保存用户意图与原始状态，不保存任何系统命令输出全文。 */
class AppControlStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lockedPackages(): Set<String> =
        prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()

    fun pendingPackages(): Set<String> =
        prefs.getStringSet(KEY_PENDING, emptySet())?.toSet() ?: emptySet()

    fun originalState(packageName: String): OriginalState? {
        val raw = prefs.getString(KEY_ORIGINAL_PREFIX + packageName, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            OriginalState(
                runInBackground = json.optString("runInBackground").takeIf { it.isNotEmpty() },
                wakeLock = json.optString("wakeLock").takeIf { it.isNotEmpty() },
                standbyBucket = if (json.has("standbyBucket")) json.optInt("standbyBucket") else null,
            )
        }.getOrNull()
    }

    fun recordLocked(packageName: String, original: OriginalState?) {
        val locked = lockedPackages() + packageName
        val pending = pendingPackages() - packageName
        prefs.edit()
            .putStringSet(KEY_LOCKED, locked)
            .putStringSet(KEY_PENDING, pending)
            .apply()
        if (original != null) {
            val json = JSONObject()
                .put("runInBackground", original.runInBackground ?: "")
                .put("wakeLock", original.wakeLock ?: "")
            original.standbyBucket?.let { json.put("standbyBucket", it) }
            prefs.edit().putString(KEY_ORIGINAL_PREFIX + packageName, json.toString()).apply()
        }
    }

    fun recordUnlocked(packageName: String) {
        prefs.edit()
            .putStringSet(KEY_LOCKED, lockedPackages() - packageName)
            .putStringSet(KEY_PENDING, pendingPackages() - packageName)
            .remove(KEY_ORIGINAL_PREFIX + packageName)
            .apply()
    }

    fun recordPending(packageName: String) {
        prefs.edit()
            .putStringSet(KEY_LOCKED, lockedPackages() + packageName)
            .putStringSet(KEY_PENDING, pendingPackages() + packageName)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "yoe_app_control"
        const val KEY_LOCKED = "locked_packages"
        const val KEY_PENDING = "pending_packages"
        const val KEY_ORIGINAL_PREFIX = "original_"
    }
}
