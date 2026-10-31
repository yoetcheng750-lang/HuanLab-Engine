package com.huanlab.engine.service.health

/** 统一状态定义：PASS / DEGRADED / UNSUPPORTED / UNKNOWN / FAILED 语义严格区分。 */
enum class HealthState {
    PASS,
    DEGRADED,
    UNSUPPORTED,
    UNKNOWN,
    FAILED,
}

data class HealthItem(
    val id: String,
    val title: String,
    val state: HealthState,
    val detail: String,
    val source: String,
)

data class HealthSummary(
    val items: List<HealthItem>,
    val overall: HealthState,
    val generatedAtMillis: Long,
) {
    companion object {
        fun aggregate(items: List<HealthItem>, now: Long): HealthSummary {
            val overall = when {
                items.any { it.state == HealthState.FAILED } -> HealthState.FAILED
                items.any { it.state == HealthState.DEGRADED } -> HealthState.DEGRADED
                items.any { it.state == HealthState.UNKNOWN } -> HealthState.UNKNOWN
                items.any { it.state == HealthState.UNSUPPORTED } -> HealthState.UNSUPPORTED
                else -> HealthState.PASS
            }
            return HealthSummary(items, overall, now)
        }
    }
}
