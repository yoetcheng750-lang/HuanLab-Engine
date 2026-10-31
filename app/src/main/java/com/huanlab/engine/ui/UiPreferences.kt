package com.huanlab.engine.ui

import android.content.Context

/**
 * 仅用于 UI 层偏好的独立存储；不触碰 capability/shared 中的任何核心状态。
 * 使用独立文件名，避免与未来的业务存储混用。
 */
class UiPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "huanlab_ui_prefs"
        const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
    }
}
