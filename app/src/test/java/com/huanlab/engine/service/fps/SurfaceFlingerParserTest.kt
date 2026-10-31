package com.huanlab.engine.service.fps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceFlingerParserTest {

    private val startNs = 1_000_000_000L
    private val endNs = 1_100_000_000L

    @Test
    fun `normal frames compute expected fps`() {
        // 60fps: 16.67ms 间隔，11 帧覆盖 166.7ms
        val output = buildString {
            appendLine("16666666")
            repeat(11) { index ->
                val ts = startNs + index * 16_666_667L
                appendLine("$ts $ts $ts")
            }
        }
        val timestamps = SurfaceFlingerParser.parsePresentTimestamps(output)
        assertEquals(11, timestamps.size)
        val result = SurfaceFlingerParser.computeFps(timestamps, startNs, endNs)
        assertEquals(11, result.validFrames)
        val fps = result.fps ?: error("expected fps")
        assertTrue("fps=$fps", fps in 58f..62f)
    }

    @Test
    fun `empty output yields unknown`() {
        val result = SurfaceFlingerParser.computeFps(emptyList(), startNs, endNs)
        assertNull(result.fps)
        assertEquals(0, result.validFrames)
    }

    @Test
    fun `invalid timestamps are filtered`() {
        val output = buildString {
            appendLine("16666666")
            appendLine("0 0 0")
            appendLine("-1 -1 -1")
            appendLine("${Long.MAX_VALUE} ${Long.MAX_VALUE} ${Long.MAX_VALUE}")
            repeat(12) { index ->
                val ts = startNs + index * 16_666_667L
                appendLine("$ts $ts $ts")
            }
        }
        val timestamps = SurfaceFlingerParser.parsePresentTimestamps(output)
        assertEquals(12, timestamps.size)
        assertTrue(timestamps.none { it == Long.MAX_VALUE || it <= 0L })
    }

    @Test
    fun `duplicate timestamps are deduplicated`() {
        val output = buildString {
            appendLine("16666666")
            repeat(3) { appendLine("${startNs} ${startNs} ${startNs}") }
            repeat(10) { index ->
                val ts = startNs + (index + 1) * 16_666_667L
                appendLine("$ts $ts $ts")
            }
        }
        val timestamps = SurfaceFlingerParser.parsePresentTimestamps(output)
        val result = SurfaceFlingerParser.computeFps(timestamps, startNs, endNs)
        assertTrue(result.note.contains("有效帧") || result.note == "OK")
    }

    @Test
    fun `truncated lines are ignored`() {
        val output = buildString {
            appendLine("16666666")
            appendLine("123")
            appendLine("456 789")
            appendLine("abc def ghi")
            repeat(11) { index ->
                val ts = startNs + index * 16_666_667L
                appendLine("$ts $ts $ts")
            }
        }
        val timestamps = SurfaceFlingerParser.parsePresentTimestamps(output)
        assertEquals(11, timestamps.size)
    }

    @Test
    fun `insufficient frames yields null`() {
        val timestamps = (0 until 5).map { startNs + it * 16_666_667L }
        val result = SurfaceFlingerParser.computeFps(timestamps, startNs, endNs)
        assertNull(result.fps)
        assertEquals(5, result.validFrames)
        assertTrue(result.note.contains("有效帧不足"))
    }

    @Test
    fun `multiple layers are ambiguous`() {
        val layers = listOf(
            "SurfaceView[com.example.game/com.example.GameActivity]#0",
            "SurfaceView[com.example.game/com.example.GameActivity]#1",
        )
        val match = LayerResolver.resolve(layers, "com.example.game")
        assertEquals(LayerMatchState.AMBIGUOUS, match.state)
        assertNull(match.layer)
        assertEquals(2, match.candidates.size)
    }

    @Test
    fun `surface view disambiguates single layer`() {
        val layers = listOf(
            "com.example.game/com.example.GameActivity",
            "SurfaceView[com.example.game/com.example.GameActivity]#0",
        )
        val match = LayerResolver.resolve(layers, "com.example.game")
        assertEquals(LayerMatchState.UNIQUE, match.state)
        assertTrue(match.layer!!.contains("SurfaceView"))
    }

    @Test
    fun `no matching layer yields none`() {
        val match = LayerResolver.resolve(listOf("com.other.app/Main"), "com.example.game")
        assertEquals(LayerMatchState.NONE, match.state)
        assertNull(match.layer)
    }

    @Test
    fun `backwards timestamps are dropped`() {
        val descending = (0 until 12).map { endNs - it * 16_666_667L }
        val result = SurfaceFlingerParser.computeFps(descending, startNs, endNs)
        assertTrue(result.validFrames > 0)
        assertTrue("fps must not be negative", (result.fps ?: 0f) >= 0f)
    }

    @Test
    fun `failed and unsupported models keep null fps`() {
        assertEquals(FpsState.FAILED, FpsResult.failed("Shizuku 未就绪").state)
        assertNull(FpsResult.failed("Shizuku 未就绪").fps)
        assertEquals(FpsState.UNSUPPORTED, FpsResult.unsupported("ROM 封锁").state)
        assertNull(FpsResult.unsupported("ROM 封锁").fps)
        assertEquals(FpsState.UNKNOWN, FpsResult.unknown("Layer 不唯一").state)
        assertNull(FpsResult.unknown("Layer 不唯一").fps)
    }

    @Test
    fun `layer list parser ignores display lines`() {
        val output = """
            Display 0 HWC layers:
            SurfaceView[com.example.game/com.example.GameActivity]#0
            com.android.systemui/StatusBar
        """.trimIndent()
        val layers = SurfaceFlingerParser.parseLayerList(output)
        assertEquals(2, layers.size)
        assertTrue(layers.none { it.startsWith("Display") })
    }
}
