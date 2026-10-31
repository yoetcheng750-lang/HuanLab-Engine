package com.huanlab.engine.service.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.huanlab.engine.core.Engine
import com.huanlab.engine.core.ExecutionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 温度真实采样。
 *
 * 1. 优先扫描 /sys/class/thermal/thermal_zone*；
 * 2. ColorOS 实测：thermal_zone 目录整体 Permission denied，此时返回 DEGRADED 并停止逐项探测，
 *    避免 73 个 zone × 2 的无意义 Shizuku 调用；
 * 3. 电池温度走 Android 标准 BatteryManager 广播（真实数据，无需特权）。
 */
class ThermalMonitor(
    private val context: Context,
    private val engine: Engine,
) {

    suspend fun sample(): ThermalSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sensors = ArrayList<ThermalMetric>()

        val batteryTemp = readBatteryTemperature()
        if (batteryTemp != null) {
            sensors += ThermalMetric(
                zoneId = "battery",
                rawType = "battery",
                category = SensorCategory.BATTERY,
                rawValue = batteryTemp.first,
                temperatureCelsius = batteryTemp.second,
                source = ThermalSource.ANDROID_BATTERY_API,
                status = ProbeStatus.PASS,
                reason = null,
                sampledAtMillis = now,
            )
        } else {
            sensors += ThermalMetric(
                zoneId = "battery",
                rawType = "battery",
                category = SensorCategory.BATTERY,
                rawValue = null,
                temperatureCelsius = null,
                source = ThermalSource.ANDROID_BATTERY_API,
                status = ProbeStatus.UNKNOWN,
                reason = "系统未提供电池温度广播",
                sampledAtMillis = now,
            )
        }

        val zoneReport = engine.execute(listOf(listOf("ls", "/sys/class/thermal")))
        val zoneList = (zoneReport as? ExecutionReport.Executed)?.results?.firstOrNull()
        if (zoneList == null || !zoneList.ok) {
            val detail = zoneList?.stderr ?: "无输出"
            val restricted = detail.lowercase().contains("permission denied")
            val status = if (restricted) ProbeStatus.DEGRADED else ProbeStatus.UNKNOWN
            sensors += blockedZoneMetric(now, status, detail)
            return@withContext ThermalSnapshot(
                sensors = sensors,
                status = if (batteryTemp != null) ProbeStatus.DEGRADED else status,
                reason = if (restricted) "thermal_zone 被系统权限限制：$detail" else "无法列出 thermal_zone：$detail",
                sampledAtMillis = now,
            )
        }

        val zones = ThermalParser.parseZoneList(zoneList.stdout)
        if (zones.isEmpty()) {
            sensors += blockedZoneMetric(now, ProbeStatus.UNSUPPORTED, "设备未提供 thermal_zone")
            return@withContext ThermalSnapshot(
                sensors = sensors,
                status = if (batteryTemp != null) ProbeStatus.DEGRADED else ProbeStatus.UNSUPPORTED,
                reason = "设备未提供 thermal_zone 节点",
                sampledAtMillis = now,
            )
        }

        val probeZone = zones.first()
        val probeReport = engine.execute(
            listOf(
                listOf("cat", "/sys/class/thermal/$probeZone/type"),
                listOf("cat", "/sys/class/thermal/$probeZone/temp"),
            ),
        )
        val probeResults = (probeReport as? ExecutionReport.Executed)?.results
        val probeType = probeResults?.getOrNull(0)
        val probeTemp = probeResults?.getOrNull(1)
        if (probeType == null || !probeType.ok || probeTemp == null || !probeTemp.ok) {
            val detail = (probeType?.stderr ?: probeTemp?.stderr ?: "无输出").ifBlank { "无输出" }
            val restricted = detail.lowercase().contains("permission denied")
            val status = if (restricted) ProbeStatus.DEGRADED else ProbeStatus.UNKNOWN
            sensors += blockedZoneMetric(now, status, detail)
            return@withContext ThermalSnapshot(
                sensors = sensors,
                status = if (batteryTemp != null) ProbeStatus.DEGRADED else status,
                reason = if (restricted) "thermal_zone 被系统权限限制：$detail" else detail,
                sampledAtMillis = now,
            )
        }

        val targets = zones.take(MAX_ZONES)
        val commands = ArrayList<List<String>>(targets.size * 2)
        targets.forEach { zone ->
            commands += listOf("cat", "/sys/class/thermal/$zone/type")
            commands += listOf("cat", "/sys/class/thermal/$zone/temp")
        }
        val report = engine.execute(commands)
        if (report !is ExecutionReport.Executed) {
            sensors += blockedZoneMetric(now, ProbeStatus.FAILED, (report as? ExecutionReport.Rejected)?.summary ?: "未知拒绝")
            return@withContext ThermalSnapshot(
                sensors = sensors,
                status = ProbeStatus.FAILED,
                reason = "Shizuku 未就绪：" + ((report as? ExecutionReport.Rejected)?.summary ?: "未知拒绝"),
                sampledAtMillis = now,
            )
        }

        var index = 0
        for (zone in targets) {
            val typeResult = report.results.getOrNull(index++)
            val tempResult = report.results.getOrNull(index++)
            val rawType = typeResult?.takeIf { it.ok }?.stdout?.trim().orEmpty()
            if (rawType.isEmpty()) continue
            val category = ThermalParser.classify(rawType)
            val parsed = if (tempResult?.ok == true) {
                ThermalParser.parseMilliCelsius(tempResult.stdout)
            } else {
                ThermalParser.TempResult(
                    null,
                    ProbeStatus.DEGRADED,
                    tempResult?.stderr ?: "读取失败",
                )
            }
            sensors += ThermalMetric(
                zoneId = zone,
                rawType = rawType,
                category = category,
                rawValue = tempResult?.takeIf { it.ok }?.stdout?.trim()?.toLongOrNull(),
                temperatureCelsius = parsed.celsius,
                source = ThermalSource.THERMAL_ZONE,
                status = parsed.status,
                reason = parsed.reason,
                sampledAtMillis = now,
            )
        }

        val zoneStatuses = sensors.filter { it.source == ThermalSource.THERMAL_ZONE }
        val overall = when {
            zoneStatuses.isEmpty() -> ProbeStatus.UNSUPPORTED
            zoneStatuses.all { it.status == ProbeStatus.PASS } -> ProbeStatus.PASS
            zoneStatuses.any { it.status == ProbeStatus.PASS } -> ProbeStatus.DEGRADED
            else -> zoneStatuses.first().status
        }
        ThermalSnapshot(
            sensors = sensors,
            status = overall,
            reason = if (overall == ProbeStatus.PASS) null else zoneStatuses.firstOrNull { it.status != ProbeStatus.PASS }?.reason,
            sampledAtMillis = now,
        )
    }

    private fun blockedZoneMetric(now: Long, status: ProbeStatus, detail: String): ThermalMetric =
        ThermalMetric(
            zoneId = "thermal_zone",
            rawType = "thermal_zone",
            category = SensorCategory.OTHER,
            rawValue = null,
            temperatureCelsius = null,
            source = ThermalSource.THERMAL_ZONE,
            status = status,
            reason = CpuFrequencyParser.reasonFor(status, detail),
            sampledAtMillis = now,
        )

    private fun readBatteryTemperature(): Pair<Long, Double>? = runCatching {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: return null
        if (tenths == Int.MIN_VALUE || tenths <= 0) return null
        val celsius = tenths / 10.0
        if (!ThermalParser.isPlausible(celsius)) return null
        tenths.toLong() to celsius
    }.getOrNull()

    private companion object {
        const val MAX_ZONES = 40
    }
}
