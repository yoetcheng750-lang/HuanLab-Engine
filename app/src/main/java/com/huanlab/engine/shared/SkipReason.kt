package com.huanlab.engine.shared

/** 能力检查的两个时点：加入执行队列之前 / 实际执行之前。 */
enum class CheckPoint {
    ENQUEUE,
    PRE_EXECUTE,
}

data class SkipReason(
    val checkPoint: CheckPoint,
    val detail: String,
) {
    init {
        require(detail.isNotBlank()) { "detail must not be blank" }
    }
}
