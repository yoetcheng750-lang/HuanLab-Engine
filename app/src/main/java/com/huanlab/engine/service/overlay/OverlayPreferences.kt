package com.huanlab.engine.service.overlay

import android.content.Context

/** 悬浮窗位置 / 透明度 / 展开状态持久化；复用轻量 SharedPreferences，不引入新依赖。 */
class OverlayPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var x: Int
        get() = prefs.getInt(KEY_X, 24)
        set(value) = prefs.edit().putInt(KEY_X, value).apply()

    var y: Int
        get() = prefs.getInt(KEY_Y, 220)
        set(value) = prefs.edit().putInt(KEY_Y, value).apply()

    var alpha: Float
        get() = prefs.getFloat(KEY_ALPHA, 0.88f)
        set(value) = prefs.edit().putFloat(KEY_ALPHA, value).apply()

    var expanded: Boolean
        get() = prefs.getBoolean(KEY_EXPANDED, true)
        set(value) = prefs.edit().putBoolean(KEY_EXPANDED, value).apply()

    private companion object {
        const val PREFS_NAME = "yoe_overlay"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_ALPHA = "alpha"
        const val KEY_EXPANDED = "expanded"
    }
}
