package com.huanlab.engine.capability

import com.huanlab.engine.shared.CheckPoint

/**
 * 每个检查点独立构建的快照；不缓存、不跨 ENQUEUE/PRE_EXECUTE 复用（Q9）。
 */
data class CapabilitySnapshot(
    val snapshotId: String,
    val checkPoint: CheckPoint,
    val takenAtElapsedMillis: Long,
    val epoch: Long,
    val monitorState: ShizukuMonitorState,
    val identity: IdentityResolution?,
    val probeOutcomes: Map<ProbeId, ProbeOutcome>,
    val attestedManualItems: Set<ManualCheckItemId>,
) {
    init {
        require(snapshotId.isNotBlank()) { "snapshotId must not be blank" }
        require(takenAtElapsedMillis >= 0L) { "takenAtElapsedMillis must be >= 0" }
    }
}
