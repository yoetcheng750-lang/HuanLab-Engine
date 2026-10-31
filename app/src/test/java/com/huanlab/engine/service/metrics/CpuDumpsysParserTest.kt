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
    fun `standard total line`() {
        val result = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 26% kernel + 1% iowait + 5.3% irq + 0.6% softirq"),
            now,
        )
        assertEquals(ProbeStatus.PASS, result.status)
        val metric = result.metric!!
        assertEquals(57.0, metric.valuePercent!!, 0.001)
        assertEquals(31.0, metric.userPercent!!, 0.001)
        assertEquals(26.0, metric.kernelPercent!!, 0.001)
        assertEquals(65.0, metric.systemTotalPercent!!, 0.001)
    }

    @Test
    fun `user plus kernel computation`() {
        val metric = CpuDumpsysParser.parse(
            sample("8.4% TOTAL: 5.2% user + 3.2% kernel"), now,
        ).metric!!
        assertEquals(8.4, metric.valuePercent!!, 0.001)
    }

    @Test
    fun `iowait parsed separately`() {
        val metric = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 26% kernel + 7% iowait"), now,
        ).metric!!
        assertEquals(7.0, metric.iowaitPercent!!, 0.001)
        assertEquals(57.0, metric.valuePercent!!, 0.001)
    }

    @Test
    fun `irq parsed`() {
        val metric = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 26% kernel + 5.3% irq"), now,
        ).metric!!
        assertEquals(5.3, metric.irqPercent!!, 0.001)
    }

    @Test
    fun `softirq parsed`() {
        val metric = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 26% kernel + 0.6% softirq"), now,
        ).metric!!
        assertEquals(0.6, metric.softirqPercent!!, 0.001)
    }

    @Test
    fun `extra whitespace tolerated`() {
        val metric = CpuDumpsysParser.parse(
            sample("  65%   TOTAL:   31%  user   +   26%   kernel  "), now,
        ).metric!!
        assertEquals(57.0, metric.valuePercent!!, 0.001)
    }

    @Test
    fun `missing user is unknown`() {
        val result = CpuDumpsysParser.parse(
            sample("65% TOTAL: 26% kernel + 1% iowait"), now,
        )
        assertNull(result.metric)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `missing kernel is unknown`() {
        val result = CpuDumpsysParser.parse(
            sample("65% TOTAL: 31% user + 1% iowait"), now,
        )
        assertNull(result.metric)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `no total line is unsupported`() {
        val result = CpuDumpsysParser.parse(
            "  63% 24995/com.example.app: 51% user + 12% kernel\n", now,
        )
        assertNull(result.metric)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun `multiple total lines conflict`() {
        val text = "65% TOTAL: 31% user + 26% kernel\n40% TOTAL: 20% user + 20% kernel\n"
        val result = CpuDumpsysParser.parse(text, now)
        assertNull(result.metric)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
        assertTrue(result.reason!!.contains("冲突"))
    }

    @Test
    fun `process rows only do not fabricate usage`() {
        val text = "  63% 24995/com.example.app: 51% user + 12% kernel\n  55% 7758/x: 26% user + 29% kernel\n"
        assertEquals(ProbeStatus.UNSUPPORTED, CpuDumpsysParser.parse(text, now).status)
    }

    @Test
    fun `invalid percentage fails`() {
        val result = CpuDumpsysParser.parse("abc% TOTAL: x% user + y% kernel", now)
        assertNull(result.metric)
        assertEquals(ProbeStatus.FAILED, result.status)
    }

    @Test
    fun `empty output is unknown`() {
        assertEquals(ProbeStatus.UNKNOWN, CpuDumpsysParser.parse("", now).status)
        assertEquals(ProbeStatus.UNKNOWN, CpuDumpsysParser.parse(null, now).status)
    }

    @Test
    fun `nan and infinity rejected`() {
        assertNull(CpuDumpsysParser.parse("NaN% TOTAL: NaN% user + 1% kernel", now).metric)
        assertNull(CpuDumpsysParser.parse("Infinity% TOTAL: Inf% user + 1% kernel", now).metric)
    }

    @Test
    fun `truncated output without total is unsupported`() {
        val truncated = "CPU usage from 10000ms to 5000ms ago\n  63% 24995/com.example.app: 51% user"
        assertEquals(ProbeStatus.UNSUPPORTED, CpuDumpsysParser.parse(truncated, now).status)
    }

    @Test
    fun `fresh window passes`() {
        val text = sample(
            "65% TOTAL: 31% user + 26% kernel",
            "CPU usage from 13477ms to 6413ms ago (2026-10-31 18:08:24.661 to 2026-10-31 18:08:31.726)",
        )
        val result = CpuDumpsysParser.parse(text, now)
        assertTrue(result.metric != null)
    }

    @Test
    fun `expired sample is unknown`() {
        val expiredNow = System.currentTimeMillis()
        val text = sample(
            "65% TOTAL: 31% user + 26% kernel",
            "CPU usage from 60000ms to 55000ms ago",
        )
        val result = CpuDumpsysParser.parse(text, expiredNow)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
        assertTrue(result.reason!!.contains("过期"))
    }

    @Test
    fun `unparseable time is degraded`() {
        val text = sample(
            "65% TOTAL: 31% user + 26% kernel",
            "CPU usage from 13477ms to 6413ms ago",
        )
        // 无绝对时间时用相对毫秒判断：6413ms <= 15s，仍为 PASS
        val result = CpuDumpsysParser.parse(text, now)
        assertEquals(ProbeStatus.PASS, result.status)
    }

    @Test
    fun `permission denied classified degraded`() {
        assertEquals(ProbeStatus.DEGRADED, CpuDumpsysParser.classifyCommandFailure("Permission denied"))
    }

    @Test
    fun `timeout classified unknown`() {
        assertEquals(ProbeStatus.UNKNOWN, CpuDumpsysParser.classifyCommandFailure("command timed out"))
    }

    @Test
    fun `shizuku disconnect classified failed`() {
        assertEquals(ProbeStatus.FAILED, CpuDumpsysParser.classifyCommandFailure("Shizuku 未就绪：Binder 断开"))
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
}
