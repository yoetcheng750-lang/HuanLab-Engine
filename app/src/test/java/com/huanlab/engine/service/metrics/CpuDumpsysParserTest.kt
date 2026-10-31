package com.huanlab.engine.service.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuDumpsysParserTest {

    private val now = 1_800_000_000_000L

    private fun sample(total: String, window: String = "CPU usage from 13477ms to 6413ms ago"): String =
        "$window\n" +
            "  63% 24995/com.example.app: 51% user + 12% kernel\n" +
            "  55% 7758/com.other.app: 26% user + 28% kernel\n" +
            "  $total\n"

    @Test
    fun `standard total line is valid`() {
        val parsed = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 26% kernel + 1% iowait + 5.3% irq + 0.6% softirq"),
            now,
        )
        assertEquals(ProbeStatus.PASS, parsed.status)
        assertEquals(57.0, parsed.valuePercent!!, 0.001)
        assertEquals(65.0, parsed.systemTotalPercent!!, 0.001)
        assertNull(parsed.reason)
    }

    @Test
    fun `user plus kernel only`() {
        val parsed = CpuDumpsysParser.parse(sample("8.4% TOTAL: 5.2% user + 3.2% kernel"), now)
        assertEquals(8.4, parsed.valuePercent!!, 0.001)
    }

    @Test
    fun `extra whitespace tolerated`() {
        val parsed = CpuDumpsysParser.parse(
            sample("  65%   TOTAL:   31%  user   +   26%   kernel  "), now,
        )
        assertEquals(57.0, parsed.valuePercent!!, 0.001)
    }

    @Test
    fun `missing user is unknown`() {
        val parsed = CpuDumpsysParser.parse(sample("65% TOTAL: 26% kernel + 1% iowait"), now)
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.UNKNOWN, parsed.status)
    }

    @Test
    fun `missing kernel is unknown`() {
        val parsed = CpuDumpsysParser.parse(sample("65% TOTAL: 31% user + 1% iowait"), now)
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.UNKNOWN, parsed.status)
    }

    @Test
    fun `no total line is unsupported`() {
        val parsed = CpuDumpsysParser.parse(
            "  63% 24995/com.example.app: 51% user + 12% kernel\n", now,
        )
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.UNSUPPORTED, parsed.status)
    }

    @Test
    fun `multiple total lines conflict`() {
        val text = "65% TOTAL: 31% user + 26% kernel\n40% TOTAL: 20% user + 20% kernel\n"
        val parsed = CpuDumpsysParser.parse(text, now)
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.UNKNOWN, parsed.status)
    }

    @Test
    fun `process rows only do not fabricate usage`() {
        val text = "  63% 24995/com.example.app: 51% user + 12% kernel\n  55% 7758/x: 26% user + 29% kernel\n"
        val parsed = CpuDumpsysParser.parse(text, now)
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.UNSUPPORTED, parsed.status)
    }

    @Test
    fun `invalid percentage fails`() {
        val parsed = CpuDumpsysParser.parse("abc% TOTAL: x% user + y% kernel", now)
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.FAILED, parsed.status)
    }

    @Test
    fun `empty output is unknown`() {
        assertEquals(ProbeStatus.UNKNOWN, CpuDumpsysParser.parse("", now).status)
        assertEquals(ProbeStatus.UNKNOWN, CpuDumpsysParser.parse(null, now).status)
        assertNull(CpuDumpsysParser.parse("", now).valuePercent)
    }

    @Test
    fun `nan and infinity rejected`() {
        assertNull(CpuDumpsysParser.parse("NaN% TOTAL: NaN% user + 1% kernel", now).valuePercent)
        assertNull(CpuDumpsysParser.parse("Infinity% TOTAL: Inf% user + 1% kernel", now).valuePercent)
    }

    @Test
    fun `expired sample yields null value`() {
        val parsed = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 26% kernel", "CPU usage from 60000ms to 55000ms ago"),
            now,
        )
        assertNull(parsed.valuePercent)
        assertEquals(ProbeStatus.UNKNOWN, parsed.status)
        assertTrue(parsed.reason!!.contains("过期"))
    }

    @Test
    fun `window and age parsed`() {
        val info = CpuDumpsysParser.parseWindow(
            "CPU usage from 13477ms to 6413ms ago (2026-10-31 18:08:24.661 to 2026-10-31 18:08:31.726)",
            now,
        )
        assertEquals(7064L, info.windowMillis!!)
        assertTrue(info.ageMillis != null)
    }

    @Test
    fun `permission denied classified degraded`() {
        assertEquals(ProbeStatus.DEGRADED, CpuDumpsysParser.classifyCommandFailure("Permission denied"))
    }

    @Test
    fun `timeout classified unknown`() {
        assertEquals(ProbeStatus.UNKNOWN, CpuDumpsysParser.classifyCommandFailure("command timed out"))
    }
}
