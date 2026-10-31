package com.huanlab.engine.service.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuFrequencyParserTest {

    @Test
    fun `valid cpuinfo value`() {
        val result = CpuFrequencyParser.parseKhz("2200000")
        assertEquals(2_200_000L, result.khz)
        assertEquals(ProbeStatus.PASS, result.status)
        assertNull(result.reason)
    }

    @Test
    fun `valid scaling value`() {
        val result = CpuFrequencyParser.parseKhz("1800000\n")
        assertEquals(1_800_000L, result.khz)
        assertEquals(ProbeStatus.PASS, result.status)
    }

    @Test
    fun `empty output is unknown`() {
        val result = CpuFrequencyParser.parseKhz("")
        assertNull(result.khz)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `non numeric is failed`() {
        val result = CpuFrequencyParser.parseKhz("N/A")
        assertNull(result.khz)
        assertEquals(ProbeStatus.FAILED, result.status)
    }

    @Test
    fun `negative is rejected`() {
        val result = CpuFrequencyParser.parseKhz("-1")
        assertNull(result.khz)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `zero is rejected`() {
        val result = CpuFrequencyParser.parseKhz("0")
        assertNull(result.khz)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `extreme value is rejected`() {
        val result = CpuFrequencyParser.parseKhz("999999999")
        assertNull(result.khz)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `khz to mhz conversion`() {
        assertEquals(2.8, CpuFrequencyParser.toMhz(2_800_000L), 0.0001)
    }

    @Test
    fun `mhz to ghz conversion`() {
        assertEquals(2.8, CpuFrequencyParser.toGhz(2800.0), 0.0001)
    }

    @Test
    fun `scaling source label differs from hardware label`() {
        val scaling = CpuFrequencyMetric(
            "policy0", listOf(0, 1), 1_800_000L, 1800.0,
            CpuFreqSource.SCALING_CUR_FREQ, ProbeStatus.PASS, null, 0L,
        )
        val hardware = CpuFrequencyMetric(
            "policy0", listOf(0, 1), 1_800_000L, 1800.0,
            CpuFreqSource.CPUINFO_CUR_FREQ, ProbeStatus.PASS, null, 0L,
        )
        assertEquals("CPU 频率（内核报告）", scaling.label)
        assertEquals("CPU 当前频率（硬件反馈）", hardware.label)
    }

    @Test
    fun `cpu list parsing and dedup`() {
        assertEquals(listOf(0, 1, 2, 3), CpuFrequencyParser.parseCpuList("0 1 2 3"))
        assertEquals(listOf(4, 5, 6), CpuFrequencyParser.parseCpuList("4 5 6 5 4"))
    }

    @Test
    fun `policy list parsing sorted`() {
        val policies = CpuFrequencyParser.parsePolicyList("policy7\npolicy0\npolicy4\ncpuinfo_cur_freq")
        assertEquals(listOf("policy0", "policy4", "policy7"), policies)
    }

    @Test
    fun `permission denied classified as degraded`() {
        assertEquals(
            ProbeStatus.DEGRADED,
            CpuFrequencyParser.classifyRead(false, "cat: Permission denied"),
        )
    }

    @Test
    fun `missing node classified as unsupported`() {
        assertEquals(
            ProbeStatus.UNSUPPORTED,
            CpuFrequencyParser.classifyRead(false, "No such file or directory"),
        )
    }

    @Test
    fun `timeout classified as unknown`() {
        assertEquals(
            ProbeStatus.UNKNOWN,
            CpuFrequencyParser.classifyRead(false, "command timed out"),
        )
    }

    @Test
    fun `duplicate paths are deduplicated`() {
        val list = CpuFrequencyParser.parseCpuList("0 0 0 1 1 2")
        assertEquals(listOf(0, 1, 2), list)
    }

    @Test
    fun `invalid cpu list yields empty`() {
        assertTrue(CpuFrequencyParser.parseCpuList("abc").isEmpty())
    }
}
