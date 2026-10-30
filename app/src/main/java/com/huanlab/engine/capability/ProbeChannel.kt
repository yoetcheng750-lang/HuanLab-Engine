package com.huanlab.engine.capability

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class AllowedProperty(val propertyName: String) {
    OPLUS_ROM_VERSION("ro.build.version.oplusrom"),
    OPPO_ROM_VERSION("ro.build.version.opporom"),
    OPLUS_VERSION("ro.oplus.version"),
}

/**
 * 枚举白名单只读命令；argv 固定，不接受自由字符串拼接。
 * 身份探测已改用 Shizuku.getUid()，因此这里不再提供 `id` 命令。
 */
sealed interface ProbeCommand {
    fun argv(): List<String>

    data class GetProperty(val property: AllowedProperty) : ProbeCommand {
        override fun argv(): List<String> = listOf("getprop", property.propertyName)
    }

    data object DumpsysDeviceIdleWhitelist : ProbeCommand {
        override fun argv(): List<String> = listOf("dumpsys", "deviceidle", "whitelist")
    }

    data class GetStandbyBucket(val packageName: String) : ProbeCommand {
        init {
            require(PACKAGE_NAME_PATTERN.matches(packageName)) {
                "invalid package name for standby bucket probe"
            }
        }

        override fun argv(): List<String> = listOf("am", "get-standby-bucket", packageName)
    }
}

private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

data class CommandResult(
    val commandLabel: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface ProbeChannel {
    suspend fun execute(command: ProbeCommand): CommandResult
}

class ProbeChannelException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface CommandExecution {
    data class Completed(val result: CommandResult) : CommandExecution
    data class Failed(val reason: String, val cause: Throwable? = null) : CommandExecution {
        init {
            require(reason.isNotBlank()) { "Failed.reason must not be blank" }
        }
    }
}

/**
 * 统一的探测执行包装：超时/异常都转成 Failed，由 probe 决定映射为 Unreadable。
 * 注意：阻塞式 IO/Binder 调用无法被协程真正取消；超时后可能留下迟到回复，均按 UNKNOWN 处理。
 */
suspend fun ProbeChannel.executeWithinTimeout(
    command: ProbeCommand,
    timeoutMillis: Long = CapabilityConstants.PROBE_TIMEOUT_MS,
): CommandExecution = try {
    CommandExecution.Completed(withTimeout(timeoutMillis) { execute(command) })
} catch (timeout: TimeoutCancellationException) {
    CommandExecution.Failed("COMMAND_TIMEOUT(${timeoutMillis}ms)", timeout)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (channel: ProbeChannelException) {
    CommandExecution.Failed("CHANNEL_ERROR: ${channel.message ?: channel.javaClass.simpleName}", channel)
} catch (throwable: Throwable) {
    CommandExecution.Failed("CHANNEL_THROWABLE: ${throwable.javaClass.simpleName}", throwable)
}
