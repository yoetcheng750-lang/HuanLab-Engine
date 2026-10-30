package com.huanlab.engine.capability

data class ProbeDescriptor(
    val id: ProbeId,
    val readability: ProbeReadability,
    val requiresPrivilegedChannel: Boolean,
)

object ProbeRegistry {
    private val descriptors: Map<ProbeId, ProbeDescriptor> = listOf(
        ProbeDescriptor(ProbeId.SHIZUKU_INSTALLED, ProbeReadability.DIRECT, false),
        ProbeDescriptor(ProbeId.SHIZUKU_BINDER, ProbeReadability.DIRECT, false),
        ProbeDescriptor(ProbeId.SHIZUKU_VERSION, ProbeReadability.DIRECT, false),
        ProbeDescriptor(ProbeId.SHIZUKU_AUTHORIZATION, ProbeReadability.DIRECT, false),
        ProbeDescriptor(ProbeId.IDENTITY, ProbeReadability.DIRECT, true),
        ProbeDescriptor(ProbeId.ROM_PROFILE, ProbeReadability.DIRECT, true),
        ProbeDescriptor(ProbeId.BATTERY_UNRESTRICTED, ProbeReadability.PARTIAL, true),
        ProbeDescriptor(ProbeId.PERMISSION_MONITOR, ProbeReadability.NONE_KNOWN, false),
    ).associateBy { it.id }

    val baselineProbes: Set<ProbeId> = linkedSetOf(
        ProbeId.SHIZUKU_INSTALLED,
        ProbeId.SHIZUKU_BINDER,
        ProbeId.SHIZUKU_VERSION,
        ProbeId.SHIZUKU_AUTHORIZATION,
        ProbeId.IDENTITY,
    )

    val conditionProbes: Set<ProbeId> = linkedSetOf(
        ProbeId.ROM_PROFILE,
        ProbeId.BATTERY_UNRESTRICTED,
        ProbeId.PERMISSION_MONITOR,
    )

    init {
        check(descriptors.size == ProbeId.entries.size) {
            "probe registry must cover every ProbeId"
        }
    }

    fun descriptor(id: ProbeId): ProbeDescriptor =
        descriptors[id] ?: error("no descriptor for $id")

    fun isBaseline(id: ProbeId): Boolean = id in baselineProbes

    fun isManualizable(id: ProbeId): Boolean {
        val readability = descriptor(id).readability
        return readability == ProbeReadability.PARTIAL || readability == ProbeReadability.NONE_KNOWN
    }
}
