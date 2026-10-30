package com.huanlab.engine.capability

import android.content.Context

class CapabilityProbes(
    context: Context,
    channel: ProbeChannel,
    eventSource: ShizukuEventSource = DefaultShizukuEventSource,
) {
    private val appContext: Context = context.applicationContext
    private val packageName: String = appContext.packageName

    private val installProbe = ShizukuInstallProbe(appContext)
    private val binderProbe = ShizukuBinderProbe(eventSource)
    private val versionProbe = ShizukuVersionProbe()
    private val authorizationProbe = ShizukuAuthorizationProbe()
    private val identityProbe = IdentityProbe()
    private val romProfileProbe = RomProfileProbe(channel)
    private val batteryProbe = BatteryUnrestrictedProbe(channel, packageName)
    private val permissionMonitorProbe = PermissionMonitorProbe()

    suspend fun probeInstallation(): ProbeOutcome = installProbe.probe()

    suspend fun probeBinder(): ProbeOutcome = binderProbe.probe()

    suspend fun probeVersion(): ShizukuVersionResult = versionProbe.probe()

    suspend fun probeAuthorization(): ProbeOutcome = authorizationProbe.probe()

    suspend fun resolveIdentity(): IdentityResolution = identityProbe.resolve()

    suspend fun runConditionProbe(probeId: ProbeId): ProbeOutcome {
        require(probeId in ProbeRegistry.conditionProbes) {
            "probe $probeId is not a condition probe"
        }
        return when (probeId) {
            ProbeId.ROM_PROFILE -> romProfileProbe.probe()
            ProbeId.BATTERY_UNRESTRICTED -> batteryProbe.probe()
            ProbeId.PERMISSION_MONITOR -> permissionMonitorProbe.probe()
            else -> error("baseline probe $probeId cannot be run as a condition probe")
        }
    }
}
