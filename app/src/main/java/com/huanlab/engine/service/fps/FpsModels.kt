package com.huanlab.engine.service.fps

enum class FpsState {
    PASS,
    UNKNOWN,
    UNSUPPORTED,
    FAILED,
}

data class LayerMatch(
    val state: LayerMatchState,
    val layer: String?,
    val candidates: List<String>,
    val reason: String,
)

enum class LayerMatchState { UNIQUE, NONE, AMBIGUOUS }

data class FpsResult(
    val state: FpsState,
    val fps: Float?,
    val layer: String?,
    val windowMillis: Long,
    val validFrames: Int,
    val source: String,
    val reason: String,
) {
    companion object {
        fun unknown(reason: String, source: String = "SurfaceFlinger") = FpsResult(
            state = FpsState.UNKNOWN,
            fps = null,
            layer = null,
            windowMillis = 0L,
            validFrames = 0,
            source = source,
            reason = reason,
        )

        fun unsupported(reason: String) = FpsResult(
            state = FpsState.UNSUPPORTED,
            fps = null,
            layer = null,
            windowMillis = 0L,
            validFrames = 0,
            source = "SurfaceFlinger",
            reason = reason,
        )

        fun failed(reason: String) = FpsResult(
            state = FpsState.FAILED,
            fps = null,
            layer = null,
            windowMillis = 0L,
            validFrames = 0,
            source = "SurfaceFlinger",
            reason = reason,
        )
    }
}
