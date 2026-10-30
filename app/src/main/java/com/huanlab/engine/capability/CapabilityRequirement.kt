package com.huanlab.engine.capability

import com.huanlab.engine.shared.ActionId

/**
 * 由 C 构造，B 只消费。
 * expectedIdentity 为空表示“需要特权通道但不限定 uid 类别”；
 * requiredProbes 不允许包含基线探测项，基线链由 Gate 自行执行；
 * requiredRemotePermissions 通过 Shizuku.checkRemotePermission(permission) 在执行前校验。
 */
data class CapabilityRequirement(
    val actionId: ActionId,
    val expectedIdentity: Set<IdentityKind> = emptySet(),
    val requiredProbes: Set<ProbeId> = emptySet(),
    val allowManualAttestation: Boolean = false,
    val requiredRemotePermissions: Set<String> = emptySet(),
) {
    init {
        require(requiredProbes.none { ProbeRegistry.isBaseline(it) }) {
            "requiredProbes must not contain baseline probes: $requiredProbes"
        }
        require(requiredRemotePermissions.all { it.isNotBlank() }) {
            "requiredRemotePermissions must not contain blank permissions"
        }
    }

    val needsPrivilegedChannel: Boolean
        get() = expectedIdentity.isNotEmpty() ||
            requiredProbes.any { ProbeRegistry.descriptor(it).requiresPrivilegedChannel } ||
            requiredRemotePermissions.isNotEmpty()
}
