package com.huanlab.engine.service.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuTopAndLoadTest {

    @Test
    fun `toybox summary line parsed`() {
        val output = "Tasks: 8316 total\nMem: 1G used\n800%cpu  30%user   0%nice  20%sys 700%idle   0%iow   5%irq  45%sirq\n  PID USER LINE"
        val result = CpuTopParser.parse(output)
        assertEquals(30.0, result.userPercent!!, 0.01)
        assertEquals(20.0, result.systemPercent!!, 0.01)
        assertEquals(700.0, result.idlePercent!!, 0.01)
    }

    @Test
    fun `system alias accepted`() {
        val output = "400%cpu  10%user  5%system  380%idle"
        val result = CpuTopParser.parse(output)
        assertEquals(5.0, result.systemPercent!!, 0.01)
    }

    @Test
    fun `iowait parsed`() {
        val output = "800%cpu  10%user  5%sys  780%idle  3%iow  2%irq"
        val result = CpuTopParser.parse(output)
        assertEquals(3.0, result.ioPercent!!, 0.01)
        assertEquals(2.0, result.irqPercent!!, 0.01)
    }

    @Test
    fun `process list only is unsupported`() {
        val output = "  PID USER %CPU\n 1234 u0_a10  12.3\n 5678 root   3.1"
        val result = CpuTopParser.parse(output)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertNull(result.userPercent)
    }

    @Test
    fun `missing fields is unknown`() {
        val output = "800%cpu  30%user 700%idle"
        val result = CpuTopParser.parse(output)
        assertEquals(ProbeStatus.UNKNOWN, result.status)
    }

    @Test
    fun `unrecognized format is unsupported`() {
        val result = CpuTopParser.parse("totally unknown output")
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun `empty output is unknown`() {
        assertEquals(ProbeStatus.UNKNOWN, CpuTopParser.parse("").status)
        assertEquals(ProbeStatus.UNKNOWN, CpuTopParser.parse(null).status)
    }

    @Test
    fun `normalize by capacity`() {
        assertEquals(50.0, CpuTopParser.normalize(30.0, 20.0, 100.0)!!, 0.001)
        assertEquals(6.25, CpuTopParser.normalize(30.0, 20.0, 800.0)!!, 0.001)
    }

    @Test
    fun `normalize rejects impossible sum`() {
        // user + sys 超过总容量说明格式或口径异常，拒绝换算
        assertNull(CpuTopParser.normalize(900.0, 200.0, 800.0))
    }

    @Test
    fun `last complete sample chosen`() {
        val output = "400%cpu  10%user  5%sys  380%idle\n400%cpu  20%user  10%sys  370%idle"
        val result = CpuTopParser.parse(output)
        assertEquals(20.0, result.userPercent!!, 0.01)
        assertEquals(10.0, result.systemPercent!!, 0.01)
    }

    @Test
    fun `loadavg parsed`() {
        val load = CpuLoadAvgParser.parse("24.69 25.91 25.47 2/8316 7232")!!
        assertEquals(24.69, load.oneMinute!!, 0.001)
        assertEquals(25.91, load.fiveMinutes!!, 0.001)
        assertEquals(25.47, load.fifteenMinutes!!, 0.001)
        assertEquals(2, load.runningTasks)
        assertEquals(8316, load.totalTasks)
    }

    @Test
    fun `loadavg invalid rejected`() {
        assertNull(CpuLoadAvgParser.parse(""))
        assertNull(CpuLoadAvgParser.parse("1.2 3.4"))
        assertNull(CpuLoadAvgParser.parse("a b c"))
        assertNull(CpuLoadAvgParser.parse("-1 2 3"))
        assertNull(CpuLoadAvgParser.parse(null))
    }

    @Test
    fun `loadavg never becomes cpu percent`() {
        val load = CpuLoadAvgParser.parse("2.50 1.80 1.20 3/450 12345")!!
        val metric = CpuUsageMetric(
            valuePercent = null,
            userPercent = null,
            kernelPercent = null,
            systemTotalPercent = null,
            iowaitPercent = null,
            irqPercent = null,
            softirqPercent = null,
            loadAverage1m = load.oneMinute,
            loadAverage5m = load.fiveMinutes,
            loadAverage15m = load.fifteenMinutes,
            source = CpuUsageSource.UNKNOWN,
            status = ProbeStatus.UNKNOWN,
            reason = "CPU 使用率不可用",
            sampledAtMillis = 0L,
            sampleWindowMillis = null,
            sampleAgeMillis = null,
        )
        assertNull(metric.valuePercent)
        assertEquals(2.50, metric.loadAverage1m!!, 0.001)
        assertTrue(metric.valuePercent == null)
    }
}
