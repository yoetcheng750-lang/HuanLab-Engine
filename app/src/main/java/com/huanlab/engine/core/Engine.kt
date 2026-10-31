package com.huanlab.engine.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.huanlab.engine.action.ActionExecutor
import com.huanlab.engine.action.ExecResult
import com.huanlab.engine.capability.CapabilityProbes
import com.huanlab.engine.capability.CapabilityRequirement
import com.huanlab.engine.capability.DefaultShizukuStateRevalidator
import com.huanlab.engine.capability.EnqueueResult
import com.huanlab.engine.capability.GrantConsumption
import com.huanlab.engine.capability.IdentityKind
import com.huanlab.engine.capability.InMemoryManualCheckAttestationStore
import com.huanlab.engine.capability.PermissionCapabilityManager
import com.huanlab.engine.capability.PreExecuteResult
import com.huanlab.engine.capability.Rejection
import com.huanlab.engine.capability.ShizukuBinderMonitor
import com.huanlab.engine.capability.ShizukuProcessProbeChannel
import com.huanlab.engine.capability.SystemTimeSource
import com.huanlab.engine.shared.ActionId
import com.huanlab.engine.shared.ThermalLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

enum class ShizukuPhase {
    NOT_INSTALLED,
    NOT_RUNNING,
    NOT_AUTHORIZED,
    CONNECTED_SHELL,
    CONNECTED_ROOT,
    UNKNOWN,
}

data class ShizukuUiState(
    val phase: ShizukuPhase,
    val detail: String,
)

data class MemoryStat(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedRatio: Float
        get() = if (totalBytes <= 0L) 0f else {
            ((totalBytes - availableBytes).toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

sealed interface ExecutionReport {
    data class Executed(val results: List<ExecResult>) : ExecutionReport {
        val allOk: Boolean get() = results.isNotEmpty() && results.all { it.ok }
        val summary: String
            get() = results.joinToString("\n") { "${it.command} -> ${it.shortMessage()}" }
    }

    data class Rejected(
        val stage: String,
        val code: String,
        val reason: String,
    ) : ExecutionReport {
        val summary: String get() = "$stage 拒绝[$code]: $reason"
    }
}

/**
 * 幻实验室运行时：装配阶段 B 的权限门面，并把 UI 动作真实下发到 Shizuku 远程进程。
 * 每次动作都重新走 ENQUEUE -> PRE_EXECUTE -> 一次性 grant 消费，复用旧 grant 会被拒绝。
 */
class Engine(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timeSource = SystemTimeSource
    private val channel = ShizukuProcessProbeChannel()
    private val probes = CapabilityProbes(appContext, channel)
    private val monitor = ShizukuBinderMonitor(
        scope,
        DefaultShizukuStateRevalidator(probes),
        timeSource,
    )
    private val attestationStore = InMemoryManualCheckAttestationStore { Build.FINGERPRINT ?: "unknown" }
    private val manager = PermissionCapabilityManager(probes, monitor, attestationStore, timeSource)
    private val executor = ActionExecutor()

    private val requirement = CapabilityRequirement(
        actionId = ActionId("huanlab.device.action"),
        expectedIdentity = setOf(IdentityKind.SHELL_UID_2000, IdentityKind.ROOT_UID_0),
    )

    fun start() {
        manager.start()
    }

    fun close() {
        manager.close()
        scope.cancel()
    }

    fun refreshShizuku() {
        scope.launch { runCatching { manager.retryShizukuDetection() } }
    }

    fun shizukuState(): ShizukuUiState {
        val installed = runCatching {
            appContext.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        if (!installed) {
            return ShizukuUiState(ShizukuPhase.NOT_INSTALLED, "未安装 Shizuku")
        }
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return ShizukuUiState(ShizukuPhase.NOT_RUNNING, "Shizuku 未运行")
        }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return ShizukuUiState(ShizukuPhase.NOT_AUTHORIZED, "等待授权")
        }
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        return when (uid) {
            0 -> ShizukuUiState(ShizukuPhase.CONNECTED_ROOT, "root (uid 0)")
            2000 -> ShizukuUiState(ShizukuPhase.CONNECTED_SHELL, "shell (uid 2000)")
            else -> ShizukuUiState(ShizukuPhase.UNKNOWN, "uid=$uid")
        }
    }

    fun thermalLevel(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.UNKNOWN
        return runCatching {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            ThermalLevel.fromStatus(power.currentThermalStatus)
        }.getOrDefault(ThermalLevel.UNKNOWN)
    }

    /** 两次采样 /proc/stat 计算总 CPU 占用，避免读取瞬时值造成误判。 */
    fun cpuUsagePercent(): Float = runCatching {
        val first = readCpuTicks()
        Thread.sleep(120L)
        val second = readCpuTicks()
        val totalDelta = second.total - first.total
        val idleDelta = second.idle - first.idle
        if (totalDelta <= 0L) 0f else {
            (((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0)
                .toFloat().coerceIn(0f, 100f)
        }
    }.getOrDefault(0f)

    fun memory(): MemoryStat = runCatching {
        var total = 0L
        var available = 0L
        File("/proc/meminfo").forEachLine { line ->
            when {
                line.startsWith("MemTotal:") -> total = parseKb(line)
                line.startsWith("MemAvailable:") -> available = parseKb(line)
            }
        }
        MemoryStat(totalBytes = total, availableBytes = available)
    }.getOrDefault(MemoryStat(0L, 0L))

    suspend fun execute(commands: List<List<String>>): ExecutionReport {
        when (val enqueue = manager.checkForEnqueue(requirement)) {
            is EnqueueResult.Rejected -> return reject("ENQUEUE", enqueue.rejection)
            is EnqueueResult.Allowed -> Unit
        }
        return when (val preExecute = manager.checkForPreExecute(requirement)) {
            is PreExecuteResult.Rejected -> reject("PRE_EXECUTE", preExecute.rejection)
            is PreExecuteResult.Allowed -> {
                val grant = preExecute.grant
                when (grant.tryConsume(timeSource.elapsedRealtimeMillis())) {
                    GrantConsumption.CONSUMED -> ExecutionReport.Executed(executor.runAll(commands))
                    GrantConsumption.EXPIRED -> ExecutionReport.Rejected(
                        "GRANT",
                        "EXPIRED",
                        "一次性授权在消费前过期，请重试",
                    )
                    GrantConsumption.ALREADY_CONSUMED -> ExecutionReport.Rejected(
                        "GRANT",
                        "ALREADY_CONSUMED",
                        "该授权已被使用，禁止重放",
                    )
                }
            }
        }
    }

    private fun reject(stage: String, rejection: Rejection): ExecutionReport.Rejected =
        ExecutionReport.Rejected(
            stage = stage,
            code = rejection.code.name,
            reason = rejection.skipReason.detail,
        )

    private fun parseKb(line: String): Long {
        val digits = line.filter { it.isDigit() }
        return digits.toLongOrNull()?.times(1024L) ?: 0L
    }

    private data class CpuTicks(val total: Long, val idle: Long)

    private fun readCpuTicks(): CpuTicks {
        val first = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return CpuTicks(0L, 0L)
        val parts = first.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return CpuTicks(0L, 0L)
        val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
        return CpuTicks(total = parts.sum(), idle = idle)
    }
}
