package com.huanlab.engine.capability

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ManualCheckItemId {
    PERMISSION_MONITOR,
    BATTERY_UNRESTRICTED,
}

data class ManualCheckItem(
    val id: ManualCheckItemId,
    val probeId: ProbeId,
    val title: String,
    val expectedState: String,
    val steps: List<String>,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(expectedState.isNotBlank()) { "expectedState must not be blank" }
        require(steps.isNotEmpty() && steps.none { it.isBlank() }) {
            "steps must be non-empty and contain no blank entries"
        }
    }
}

object ManualCheckRegistry {
    private val items: Map<ManualCheckItemId, ManualCheckItem> = listOf(
        ManualCheckItem(
            id = ManualCheckItemId.PERMISSION_MONITOR,
            probeId = ProbeId.PERMISSION_MONITOR,
            title = "ColorOS 权限监控",
            expectedState = "你需要在系统设置中确认“权限监控 / 权限使用监控”处于你预期的状态；该状态无法由机器验证。",
            steps = listOf(
                "打开系统设置 → 权限与隐私 → 权限管理（具体路径待 ColorOS 17 实机确认）",
                "找到“权限监控 / 权限使用监控”入口，确认其当前状态",
                "确认后回到本应用，点击“我已完成人工核对”",
            ),
        ),
        ManualCheckItem(
            id = ManualCheckItemId.BATTERY_UNRESTRICTED,
            probeId = ProbeId.BATTERY_UNRESTRICTED,
            title = "电池耗电管理：无限制",
            expectedState = "幻实验室在系统电池设置中显示为“无限制 / 允许后台活动”。",
            steps = listOf(
                "打开系统设置 → 电池 → 应用耗电管理（具体路径待 ColorOS 17 实机确认）",
                "找到“幻实验室”，将耗电策略设为“无限制 / 允许后台活动”",
                "返回本应用，点击“我已完成人工核对”",
            ),
        ),
    ).associateBy { it.id }

    fun item(id: ManualCheckItemId): ManualCheckItem =
        items[id] ?: error("no manual check item registered for $id")

    fun requireAttestable(id: ManualCheckItemId): ManualCheckItem {
        val item = item(id)
        check(ProbeRegistry.isManualizable(item.probeId)) {
            "probe ${item.probeId} is DIRECT and must never be manually attested"
        }
        return item
    }
}

data class ManualCheckAttestation(
    val itemId: ManualCheckItemId,
    val romFingerprint: String,
    val attestedAtElapsedMillis: Long,
) {
    init {
        require(romFingerprint.isNotBlank()) { "romFingerprint must not be blank" }
        require(attestedAtElapsedMillis >= 0L) { "attestedAtElapsedMillis must be >= 0" }
    }
}

interface ManualCheckAttestationStore {
    suspend fun record(itemId: ManualCheckItemId, nowElapsedMillis: Long): ManualCheckAttestation
    suspend fun revoke(itemId: ManualCheckItemId)
    suspend fun clear()
    suspend fun isValid(itemId: ManualCheckItemId, nowElapsedMillis: Long): Boolean
}

/**
 * 仅内存、会话级；进程重启即失效；不写任何文件（当前红线与设计一致）。
 */
class InMemoryManualCheckAttestationStore(
    private val romFingerprintProvider: () -> String,
) : ManualCheckAttestationStore {

    private val mutex = Mutex()
    private val records = mutableMapOf<ManualCheckItemId, ManualCheckAttestation>()

    override suspend fun record(
        itemId: ManualCheckItemId,
        nowElapsedMillis: Long,
    ): ManualCheckAttestation = mutex.withLock {
        val fingerprint = currentFingerprintOrThrow()
        val attestation = ManualCheckAttestation(itemId, fingerprint, nowElapsedMillis)
        records[itemId] = attestation
        attestation
    }

    override suspend fun revoke(itemId: ManualCheckItemId) {
        mutex.withLock { records.remove(itemId) }
    }

    override suspend fun clear() {
        mutex.withLock { records.clear() }
    }

    override suspend fun isValid(
        itemId: ManualCheckItemId,
        nowElapsedMillis: Long,
    ): Boolean = mutex.withLock {
        val record = records[itemId] ?: return@withLock false
        val fingerprint = readFingerprintOrNull() ?: return@withLock false
        if (fingerprint.isBlank()) return@withLock false
        if (record.romFingerprint != fingerprint) return@withLock false
        val age = nowElapsedMillis - record.attestedAtElapsedMillis
        age >= 0L && age <= CapabilityConstants.ATTESTATION_TTL_MS
    }

    private fun currentFingerprintOrThrow(): String {
        val fingerprint = readFingerprintOrNull()
        require(!fingerprint.isNullOrBlank()) {
            "ROM fingerprint unreadable; refusing manual attestation"
        }
        return fingerprint
    }

    private fun readFingerprintOrNull(): String? =
        try {
            romFingerprintProvider()
        } catch (_: Throwable) {
            null
        }
}
