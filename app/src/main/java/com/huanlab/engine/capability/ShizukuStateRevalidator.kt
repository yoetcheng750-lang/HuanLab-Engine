package com.huanlab.engine.capability

sealed interface ShizukuRevalidation {
    data class Ready(
        val identity: IdentityResolution.Resolved,
        val apiVersion: Int,
    ) : ShizukuRevalidation

    data class NotAuthorized(val reason: String) : ShizukuRevalidation {
        init { require(reason.isNotBlank()) }
    }

    data class Incompatible(
        val requiredVersion: Int,
        val actualVersion: Int,
    ) : ShizukuRevalidation

    data class Unreadable(val reason: String) : ShizukuRevalidation {
        init { require(reason.isNotBlank()) }
    }
}

interface ShizukuStateRevalidator {
    suspend fun revalidate(): ShizukuRevalidation
}

/**
 * 重连后的强制复验：binder -> 版本 -> 授权 -> 身份。
 * 任何一步读不到都返回 Unreadable，不允许沿用旧结论。
 */
class DefaultShizukuStateRevalidator(
    private val probes: CapabilityProbes,
) : ShizukuStateRevalidator {

    override suspend fun revalidate(): ShizukuRevalidation {
        when (val binder = probes.probeBinder()) {
            is ProbeOutcome.Satisfied -> Unit
            is ProbeOutcome.NotSatisfied -> return ShizukuRevalidation.Unreadable(
                "BINDER_NOT_ALIVE_DURING_REVALIDATION: ${binder.reason}",
            )
            is ProbeOutcome.Unreadable -> return ShizukuRevalidation.Unreadable(
                "BINDER_UNREADABLE_DURING_REVALIDATION: ${binder.reason}",
            )
            is ProbeOutcome.ManualRequired -> return ShizukuRevalidation.Unreadable(
                "BINDER_MANUAL_REQUIRED_DURING_REVALIDATION: ${binder.reason}",
            )
        }

        val apiVersion = when (val version = probes.probeVersion()) {
            is ShizukuVersionResult.Supported -> version.apiVersion
            is ShizukuVersionResult.TooOld -> return ShizukuRevalidation.Incompatible(
                version.requiredVersion,
                version.actualVersion,
            )
            is ShizukuVersionResult.Unreadable -> return ShizukuRevalidation.Unreadable(
                "VERSION_UNREADABLE: ${version.reason}",
            )
        }

        when (val authorization = probes.probeAuthorization()) {
            is ProbeOutcome.Satisfied -> Unit
            is ProbeOutcome.NotSatisfied -> return ShizukuRevalidation.NotAuthorized(
                authorization.reason,
            )
            is ProbeOutcome.Unreadable -> return ShizukuRevalidation.Unreadable(
                "AUTHORIZATION_UNREADABLE: ${authorization.reason}",
            )
            is ProbeOutcome.ManualRequired -> return ShizukuRevalidation.Unreadable(
                "AUTHORIZATION_MANUAL_REQUIRED: ${authorization.reason}",
            )
        }

        return when (val identity = probes.resolveIdentity()) {
            is IdentityResolution.Unresolved -> ShizukuRevalidation.Unreadable(
                "IDENTITY_UNRESOLVED: ${identity.reason}",
            )
            is IdentityResolution.Resolved -> ShizukuRevalidation.Ready(
                identity,
                apiVersion,
            )
        }
    }
}
