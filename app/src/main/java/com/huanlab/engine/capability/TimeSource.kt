package com.huanlab.engine.capability

import android.os.SystemClock

fun interface TimeSource {
    fun elapsedRealtimeMillis(): Long
}

object SystemTimeSource : TimeSource {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
