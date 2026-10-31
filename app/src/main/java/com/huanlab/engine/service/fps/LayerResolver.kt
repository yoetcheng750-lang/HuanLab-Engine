package com.huanlab.engine.service.fps

/**
 * Layer 识别：只有能唯一归属到目标应用时才允许采样。
 * 多候选一律返回 AMBIGUOUS，绝不随便取第一个。
 */
object LayerResolver {

    fun resolve(layers: List<String>, packageName: String): LayerMatch {
        if (packageName.isBlank()) {
            return LayerMatch(LayerMatchState.NONE, null, emptyList(), "包名为空")
        }
        val byPackage = layers.filter { it.contains(packageName, ignoreCase = true) }
        if (byPackage.isEmpty()) {
            return LayerMatch(LayerMatchState.NONE, null, emptyList(), "未找到目标应用 Layer")
        }
        if (byPackage.size == 1) {
            return LayerMatch(LayerMatchState.UNIQUE, byPackage.first(), byPackage, "唯一匹配")
        }
        val surfaceView = byPackage.filter {
            it.contains("SurfaceView", ignoreCase = true)
        }
        if (surfaceView.size == 1) {
            return LayerMatch(LayerMatchState.UNIQUE, surfaceView.first(), surfaceView, "SurfaceView 唯一匹配")
        }
        val candidates = if (surfaceView.isEmpty()) byPackage else surfaceView
        return LayerMatch(
            LayerMatchState.AMBIGUOUS,
            null,
            candidates,
            "存在 ${candidates.size} 个候选 Layer，无法唯一确认",
        )
    }
}
