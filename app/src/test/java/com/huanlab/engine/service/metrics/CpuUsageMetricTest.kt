package com.huanlab.engine.service.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单一数据源契约：UNKNOWN 必须是 null，而不是 0.0；真正的 0% 与读取失败必须可区分。
 */
class CpuUsageMetricTest {

    @Test
    fun `unknown carries null value and no source`() {
        val metric = CpuUsageMetric.unknown("dumpsys 执行失败", 123L)
        assertNull(metric.valuePercent)
        assertNull(metric.source)
        assertEquals(ProbeStatus.UNKNOWN, metric.status)
        assertEquals(123L, metric.sampleTimestamp)
    }

    @Test
    fun `valid carries dumpsys source`() {
        val metric = CpuUsageMetric.valid(19.8, 6400L, 1000L)
        assertEquals(19.8, metric.valuePercent!!, 0.001)
        assertEquals(CpuUsageSource.DUMPSYS_CPUINFO, metric.source)
        assertEquals("dumpsys cpuinfo", metric.source!!.label)
        assertEquals(ProbeStatus.PASS, metric.status)
    }

    @Test
    fun `real zero is not unknown`() {
        val zero = CpuUsageMetric.valid(0.0, 1000L, 2000L)
        assertTrue(zero.valuePercent != null)
        assertEquals(0.0, zero.valuePercent!!, 0.0001)
        assertEquals(ProbeStatus.PASS, zero.status)
        // 与 UNKNOWN 必须不同
        assertNull(CpuUsageMetric.unknown("x").valuePercent)
    }

    @Test
    fun `only dumpsys source exists`() {
        assertEquals(1, CpuUsageSource.entries.size)
        assertEquals(CpuUsageSource.DUMPSYS_CPUINFO, CpuUsageSource.entries.first())
    }
}
