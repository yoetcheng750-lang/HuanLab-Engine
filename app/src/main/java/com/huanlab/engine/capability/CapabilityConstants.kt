package com.huanlab.engine.capability

/**
 * 阶段 B 候选常量（Q7）。
 * 全部为设计阶段提议值，未经设备实测；不得把这里的数值当作已确认事实。
 */
object CapabilityConstants {
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    /** UNVERIFIED：候选最低 API 版本，需按实际依赖的 Shizuku API 版本确认。 */
    const val SHIZUKU_MIN_API_VERSION = 11

    const val BINDER_PING_TIMEOUT_MS = 2_000L
    const val PROBE_TIMEOUT_MS = 3_000L
    const val RECONNECT_TIMEOUT_MS = 5_000L
    const val GRANT_TTL_MS = 3_000L
    const val ATTESTATION_TTL_MS = 30 * 60 * 1_000L

    const val MAX_COMMAND_OUTPUT_CHARS = 64 * 1024
    const val MAX_EVIDENCE_CHARS = 512
}
