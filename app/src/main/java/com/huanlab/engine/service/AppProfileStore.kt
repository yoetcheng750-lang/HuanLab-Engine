package com.huanlab.engine.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppProfile(
    val packageName: String,
    val label: String,
    val performanceMode: Boolean,
    val refreshRate: Int?,
    val enabled: Boolean,
)

/** 应用专属性能方案；独立存储，不触碰已有核心状态。 */
class AppProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<AppProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val packageName = json.optString("packageName")
                if (packageName.isEmpty()) return@mapNotNull null
                AppProfile(
                    packageName = packageName,
                    label = json.optString("label", packageName),
                    performanceMode = json.optBoolean("performanceMode", false),
                    refreshRate = if (json.has("refreshRate")) json.optInt("refreshRate") else null,
                    enabled = json.optBoolean("enabled", true),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun get(packageName: String): AppProfile? = all().firstOrNull { it.packageName == packageName }

    fun upsert(profile: AppProfile) {
        val updated = all().filterNot { it.packageName == profile.packageName } + profile
        persist(updated)
    }

    fun delete(packageName: String) {
        persist(all().filterNot { it.packageName == packageName })
    }

    private fun persist(profiles: List<AppProfile>) {
        val array = JSONArray()
        profiles.sortedBy { it.packageName }.forEach { profile ->
            val json = JSONObject()
                .put("packageName", profile.packageName)
                .put("label", profile.label)
                .put("performanceMode", profile.performanceMode)
                .put("enabled", profile.enabled)
            profile.refreshRate?.let { json.put("refreshRate", it) }
            array.put(json)
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "yoe_app_profiles"
        const val KEY_PROFILES = "profiles"
    }
}
