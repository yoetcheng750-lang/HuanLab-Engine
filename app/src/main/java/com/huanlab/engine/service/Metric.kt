package com.huanlab.engine.service

/**
 * 可信度显式建模：任何读不到、算不出的指标必须是 Unknown，禁止用 0 或默认值伪装。
 * UI 层必须同时处理 Known 与 Unknown 两种分支。
 */
sealed interface Metric<out T> {
    data class Known<T>(val value: T) : Metric<T>
    data class Unknown(val reason: String) : Metric<Nothing>

    fun valueOrNull(): T? = (this as? Known)?.value

    fun <R> map(transform: (T) -> R): Metric<R> = when (this) {
        is Known -> Known(transform(value))
        is Unknown -> this
    }
}
