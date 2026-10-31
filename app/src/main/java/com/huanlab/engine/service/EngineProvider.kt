package com.huanlab.engine.service

import android.content.Context
import com.huanlab.engine.core.Engine

/**
 * 应用级共享 Engine。Shizuku Binder 监听在进程内只能注册一次，
 * 多个 ViewModel 必须复用同一实例，否则后创建的监控器收不到事件。
 */
object EngineProvider {

    @Volatile
    private var instance: Engine? = null

    fun get(context: Context): Engine {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: Engine(context.applicationContext).also {
                it.start()
                instance = it
            }
        }
    }
}
