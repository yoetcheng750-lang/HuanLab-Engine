package com.huanlab.engine.capability

/**
 * ColorOS 17 版本探测。
 * UNVERIFIED：属性键名与取值格式未实机核对；任一可读值无法解析都按 UNREADABLE 处理，绝不猜版本。
 */
class RomProfileProbe(private val channel: ProbeChannel) {

    suspend fun probe(): ProbeOutcome {
        val readValues = linkedMapOf<AllowedProperty, String>()
        var lastFailure: String? = null

        for (property in VERSION_PROPERTIES) {
            when (val execution = channel.executeWithinTimeout(ProbeCommand.GetProperty(property))) {
                is CommandExecution.Failed -> {
                    lastFailure = execution.reason
                }
                is CommandExecution.Completed -> {
                    val result = execution.result
                    if (result.exitCode != 0) {
                        lastFailure = "getprop ${property.propertyName} exit=${result.exitCode}"
                    } else {
                        val value = result.stdout.trim()
                        if (value.isNotEmpty()) readValues[property] = value
                    }
                }
            }
        }

        val evidence = ProbeEvidence(
            ProbeId.ROM_PROFILE,
            "getprop(oplusrom/opporom/oplusversion)",
            null,
            boundEvidence(
                buildString {
                    append("props=")
                    if (readValues.isEmpty()) {
                        append("(none)")
                    } else {
                        append(readValues.entries.joinToString("; ") {
                            "${it.key.propertyName}=${it.value}"
                        })
                    }
                    if (lastFailure != null) append("; lastFailure=$lastFailure")
                },
            ),
        )

        if (readValues.isEmpty()) {
            return ProbeOutcome.Unreadable(
                ProbeId.ROM_PROFILE,
                "ROM_VERSION_UNREADABLE: 所有版本属性均不可读",
                evidence,
            )
        }

        val parsed = readValues.map { (property, value) -> property to parseMajor(value) }
        val unparseable = parsed.filter { it.second == null }
        if (unparseable.isNotEmpty()) {
            return ProbeOutcome.Unreadable(
                ProbeId.ROM_PROFILE,
                "ROM_VERSION_UNPARSEABLE: ${unparseable.joinToString { it.first.propertyName }}",
                evidence,
            )
        }

        val distinctMajors = parsed.mapNotNull { it.second }.toSet()
        if (distinctMajors.size != 1) {
            return ProbeOutcome.Unreadable(
                ProbeId.ROM_PROFILE,
                "ROM_VERSION_CONFLICT: $distinctMajors",
                evidence,
            )
        }

        val major = distinctMajors.single()
        return if (major == COLOROS_17_MAJOR) {
            ProbeOutcome.Satisfied(ProbeId.ROM_PROFILE, evidence)
        } else {
            ProbeOutcome.NotSatisfied(
                ProbeId.ROM_PROFILE,
                "ROM_IS_NOT_COLOROS_17: major=$major",
                evidence,
            )
        }
    }
}

private val VERSION_PROPERTIES = listOf(
    AllowedProperty.OPLUS_ROM_VERSION,
    AllowedProperty.OPPO_ROM_VERSION,
    AllowedProperty.OPLUS_VERSION,
)
private const val COLOROS_17_MAJOR = 17
private val ROM_MAJOR_PATTERN = Regex("(\\d+)")

private fun parseMajor(value: String): Int? =
    ROM_MAJOR_PATTERN.find(value)?.groupValues?.get(1)?.toIntOrNull()

/**
 * ColorOS 17 电池“无限制”探测。
 * 双源：deviceidle whitelist + standby bucket。
 * UNVERIFIED：两个来源在 ColorOS 17 上是否真实反映“无限制”，以及 bucket 阈值语义。
 * 任一来源不可读 -> 部分可读 -> 人工核对；两源冲突 -> 人工核对；绝不猜“已无限制”。
 */
class BatteryUnrestrictedProbe(
    private val channel: ProbeChannel,
    private val packageName: String,
) {

    suspend fun probe(): ProbeOutcome {
        val whitelisted = readWhitelist()
        val bucket = readBucket()

        val evidence = ProbeEvidence(
            ProbeId.BATTERY_UNRESTRICTED,
            "dumpsys deviceidle whitelist + am get-standby-bucket",
            null,
            boundEvidence("whitelisted=${whitelisted ?: "UNREADABLE"}; bucket=${bucket ?: "UNREADABLE"}"),
        )

        return when {
            whitelisted == null && bucket == null -> ProbeOutcome.Unreadable(
                ProbeId.BATTERY_UNRESTRICTED,
                "BATTERY_STATE_UNREADABLE: 两个来源均不可读",
                evidence,
            )
            whitelisted == null || bucket == null -> ProbeOutcome.ManualRequired(
                ProbeId.BATTERY_UNRESTRICTED,
                ManualCheckItemId.BATTERY_UNRESTRICTED,
                "BATTERY_STATE_PARTIALLY_READABLE: 仅一个来源可读",
                evidence,
            )
            whitelisted && bucket == STANDBY_BUCKET_ACTIVE -> ProbeOutcome.Satisfied(
                ProbeId.BATTERY_UNRESTRICTED,
                evidence,
            )
            !whitelisted && bucket >= STANDBY_BUCKET_FREQUENT -> ProbeOutcome.NotSatisfied(
                ProbeId.BATTERY_UNRESTRICTED,
                "BATTERY_RESTRICTED: 明确受限制",
                evidence,
            )
            else -> ProbeOutcome.ManualRequired(
                ProbeId.BATTERY_UNRESTRICTED,
                ManualCheckItemId.BATTERY_UNRESTRICTED,
                "BATTERY_STATE_AMBIGUOUS: whitelisted=$whitelisted bucket=$bucket",
                evidence,
            )
        }
    }

    private suspend fun readWhitelist(): Boolean? {
        return when (
            val execution = channel.executeWithinTimeout(ProbeCommand.DumpsysDeviceIdleWhitelist)
        ) {
            is CommandExecution.Failed -> null
            is CommandExecution.Completed -> {
                val result = execution.result
                if (result.exitCode != 0) null else parseWhitelist(result.stdout)
            }
        }
    }

    private suspend fun readBucket(): Int? {
        return when (
            val execution = channel.executeWithinTimeout(
                ProbeCommand.GetStandbyBucket(packageName),
            )
        ) {
            is CommandExecution.Failed -> null
            is CommandExecution.Completed -> {
                val result = execution.result
                if (result.exitCode != 0) null else result.stdout.trim().toIntOrNull()
            }
        }
    }

    private fun parseWhitelist(output: String): Boolean =
        output.lineSequence().any { line ->
            line
                .split(',', ' ', '\t')
                .map { it.trim() }
                .any { it == packageName }
        }
}

private const val STANDBY_BUCKET_ACTIVE = 10
private const val STANDBY_BUCKET_FREQUENT = 30

/**
 * U7 决策：ColorOS 17 权限监控按 NONE_KNOWN 处理。
 * 本探测不执行任何读取命令，直接产出人工核对项，避免把“未读到”写成“已关闭”。
 */
class PermissionMonitorProbe {
    fun probe(): ProbeOutcome = ProbeOutcome.ManualRequired(
        ProbeId.PERMISSION_MONITOR,
        ManualCheckItemId.PERMISSION_MONITOR,
        "PERMISSION_MONITOR_NONE_KNOWN: ColorOS 17 无可靠机器读法（U7 决策）",
    )
}
