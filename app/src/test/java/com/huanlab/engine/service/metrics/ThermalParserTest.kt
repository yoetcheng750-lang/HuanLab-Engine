package com.huanlab.engine.service.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalParserTest {

    @Test
    fun `cpu sensor classification`() {
        assertEquals(SensorCategory.CPU, ThermalParser.classify("cpu-thermal"))
        assertEquals(SensorCategory.CPU, ThermalParser.classify("cpu_big"))
        assertEquals(SensorCategory.CPU, ThermalParser.classify("mtktscpu"))
    }

    @Test
    fun `gpu sensor classification`() {
        assertEquals(SensorCategory.GPU, ThermalParser.classify("gpu-thermal"))
        assertEquals(SensorCategory.GPU, ThermalParser.classify("mtktsgpu"))
    }

    @Test
    fun `battery sensor classification`() {
        assertEquals(SensorCategory.BATTERY, ThermalParser.classify("battery"))
        assertEquals(SensorCategory.BATTERY, ThermalParser.classify("battery-thermal"))
    }

    @Test
    fun `soc is not classified as cpu`() {
        assertEquals(SensorCategory.SOC, ThermalParser.classify("soc-thermal"))
        assertEquals(SensorCategory.SOC, ThermalParser.classify("soc"))
    }

    @Test
    fun `skin sensor classification`() {
        assertEquals(SensorCategory.SKIN, ThermalParser.classify("skin-thermal"))
    }

    @Test
    fun `unknown type falls back to other`() {
        assertEquals(SensorCategory.OTHER, ThermalParser.classify("xyz-sensor"))
        assertEquals(SensorCategory.OTHER, ThermalParser.classify(""))
    }

    @Test
    fun `valid millidegree conversion`() {
        val result = ThermalParser.parseMilliCelsius("42000")
        assertEquals(42.0, result.celsius!!, 0.001)
        assertEquals(ProbeStatus.PASS, result.status)
    }

    @Test
    fun `empty temperature is unknown`() {
        val result = ThermalParser.parseMilliCelsius("")
        assertNull(result.celsius)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `non numeric temperature is failed`() {
        val result = ThermalParser.parseMilliCelsius("hot")
        assertNull(result.celsius)
        assertEquals(ProbeStatus.FAILED, result.status)
    }

    @Test
    fun `zero temperature is unknown`() {
        val result = ThermalParser.parseMilliCelsius("0")
        assertNull(result.celsius)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `negative temperature is unknown`() {
        val result = ThermalParser.parseMilliCelsius("-5000")
        assertNull(result.celsius)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `extreme temperature is unknown`() {
        val result = ThermalParser.parseMilliCelsius("999000")
        assertNull(result.celsius)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `plausible range check`() {
        assertTrue(ThermalParser.isPlausible(35.9))
        assertTrue(!ThermalParser.isPlausible(500.0))
        assertTrue(!ThermalParser.isPlausible(Double.NaN))
    }

    @Test
    fun `zone list parsing sorted`() {
        val zones = ThermalParser.parseZoneList("thermal_zone10\nthermal_zone2\nthermal_zone0\ncooling_device0")
        assertEquals(listOf("thermal_zone0", "thermal_zone2", "thermal_zone10"), zones)
    }

    @Test
    fun `multiple cpu zones stay separate`() {
        val zones = ThermalParser.parseZoneList("thermal_zone3\nthermal_zone4")
        assertEquals(2, zones.size)
    }

    @Test
    fun `battery source is distinct from thermal zone`() {
        val battery = ThermalMetric(
            "battery", "battery", SensorCategory.BATTERY, 359L, 35.9,
            ThermalSource.ANDROID_BATTERY_API, ProbeStatus.PASS, null, 0L,
        )
        val cpu = ThermalMetric(
            "thermal_zone0", "cpu-thermal", SensorCategory.CPU, 42000L, 42.0,
            ThermalSource.THERMAL_ZONE, ProbeStatus.PASS, null, 0L,
        )
        assertEquals(ThermalSource.ANDROID_BATTERY_API, battery.source)
        assertEquals(ThermalSource.THERMAL_ZONE, cpu.source)
    }

    @Test
    fun `duplicate tokens do not misclassify`() {
        // "soc" 出现在 battery 名称中时不得被判为 SOC
        assertEquals(SensorCategory.BATTERY, ThermalParser.classify("battery_soc"))
    }

    @Test
    fun `boundary tokens are respected`() {
        // "cpufreq" 含 cpu 但属于 CPU 类别；"gpu_cpu" 优先 GPU
        assertEquals(SensorCategory.GPU, ThermalParser.classify("gpu_cpu"))
    }
}
