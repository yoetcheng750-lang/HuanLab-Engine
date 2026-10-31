package com.huanlab.engine.service.metrics

/** 探测状态统一语义；UI 对非 PASS 状态一律显示「—」并附真实原因。 */
enum class ProbeStatus {
    PASS,
    DEGRADED,
    UNSUPPORTED,
    UNKNOWN,
    FAILED,
}
