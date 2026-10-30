package com.huanlab.engine.capability

enum class ProbeId {
    SHIZUKU_INSTALLED,
    SHIZUKU_BINDER,
    SHIZUKU_VERSION,
    SHIZUKU_AUTHORIZATION,
    IDENTITY,
    ROM_PROFILE,
    BATTERY_UNRESTRICTED,
    PERMISSION_MONITOR,
}

enum class ProbeReadability { DIRECT, PARTIAL, NONE_KNOWN }

data class ProbeEvidence(
    val probeId: ProbeId,
    val source: String,
    val exitCode: Int?,
    val detail: String,
) {
    init {
        require(source.isNotBlank()) { "evidence.source must not be blank" }
    }
}

/**
 * 四态结果。任何“读不到”都必须落入 Unreadable 或 ManualRequired，
 * 禁止在调用方折叠为 Boolean 默认值。
 */
sealed interface ProbeOutcome {
    val probeId: ProbeId

    data class Satisfied(
        override val probeId: ProbeId,
        val evidence: ProbeEvidence,
    ) : ProbeOutcome

    data class NotSatisfied(
        override val probeId: ProbeId,
        val reason: String,
        val evidence: ProbeEvidence? = null,
    ) : ProbeOutcome {
        init {
            require(reason.isNotBlank()) { "NotSatisfied.reason must not be blank" }
        }
    }

    data class Unreadable(
        override val probeId: ProbeId,
        val reason: String,
        val evidence: ProbeEvidence? = null,
    ) : ProbeOutcome {
        init {
            require(reason.isNotBlank()) { "Unreadable.reason must not be blank" }
        }
    }

    data class ManualRequired(
        override val probeId: ProbeId,
        val itemId: ManualCheckItemId,
        val reason: String,
        val evidence: ProbeEvidence? = null,
    ) : ProbeOutcome {
        init {
            require(reason.isNotBlank()) { "ManualRequired.reason must not be blank" }
        }
    }
}

internal fun boundEvidence(raw: String): String {
    val normalized = raw.replace('\n', ' ').replace('\r', ' ').trim()
    if (normalized.isEmpty()) return "(empty)"
    return if (normalized.length <= CapabilityConstants.MAX_EVIDENCE_CHARS) {
        normalized
    } else {
        normalized.take(CapabilityConstants.MAX_EVIDENCE_CHARS) + "...(truncated)"
    }
}
