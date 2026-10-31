package com.huanlab.engine.service

import android.content.Context

class AutomationSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "yoe_automation"
        const val KEY_ENABLED = "automation_enabled"
    }
}
