package com.huanlab.engine.capability

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.InputStream

/**
 * 通过 Shizuku 远程进程执行白名单只读 argv。
 *
 * P0 修复：
 * 1. stdout/stderr 并发读取，避免任一管道写满导致死锁；
 * 2. 读取过程实施硬性长度上限，达到上限立即停止读取，不再“全部读入内存后 take()”。
 *
 * 已核对（Shizuku API 13.1.5）：Shizuku.newProcess 为 private static，需反射调用；
 * 其返回类型 ShizukuRemoteProcess 继承 java.lang.Process。
 */
class ShizukuProcessProbeChannel : ProbeChannel {
    override suspend fun execute(command: ProbeCommand): CommandResult = withContext(Dispatchers.IO) {
        val argv = command.argv()
        val process = try {
            startRemoteProcess(argv.toTypedArray())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            throw ProbeChannelException(
                "Shizuku.newProcess failed: ${throwable.javaClass.simpleName}",
                throwable,
            )
        }

        try {
            coroutineScope {
                val stdoutDeferred = async(Dispatchers.IO) {
                    readLimited(process.inputStream)
                }
                val stderrDeferred = async(Dispatchers.IO) {
                    readLimited(process.errorStream)
                }
                val exitCode = process.waitFor()
                CommandResult(
                    commandLabel = argv.joinToString(" "),
                    exitCode = exitCode,
                    stdout = stdoutDeferred.await(),
                    stderr = stderrDeferred.await(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            throw ProbeChannelException(
                "reading Shizuku remote process failed: ${throwable.javaClass.simpleName}",
                throwable,
            )
        } finally {
            runCatching { process.destroy() }
        }
    }

    /**
     * 硬上限读取：最多保留 (MAX - marker) 个字符；达到上限即停止读取并追加截断标记。
     * 不会在内存里保留超过上限的完整输出。
     */
    private fun readLimited(stream: InputStream): String {
        val maxChars = CapabilityConstants.MAX_COMMAND_OUTPUT_CHARS
        val marker = TRUNCATION_MARKER
        val contentLimit = (maxChars - marker.length).coerceAtLeast(0)
        val builder = StringBuilder(minOf(contentLimit, 8 * 1024))
        val reader = stream.bufferedReader()
        var truncated = false

        try {
            val buffer = CharArray(4 * 1024)
            while (builder.length < contentLimit) {
                val remaining = contentLimit - builder.length
                val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                builder.append(buffer, 0, read)
            }
            if (builder.length >= contentLimit) {
                truncated = true
            }
        } finally {
            runCatching { reader.close() }
        }

        return if (truncated) builder.append(marker).toString() else builder.toString()
    }

    /**
     * Shizuku API 13.1.5 未公开 newProcess：它在本版本中是 private static 方法，
     * 因此通过反射调用；返回的 ShizukuRemoteProcess 继承 java.lang.Process。
     * 反射目标是应用自带的 rikka.shizuku.Shizuku，不属于 Android 隐藏 API 限制范围。
     */
    private fun startRemoteProcess(argv: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            METHOD_NEW_PROCESS,
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val result = method.invoke(null, *arrayOf<Any?>(argv, null, null)) as? Process
        return result
            ?: throw IllegalStateException("Shizuku.newProcess returned unexpected type")
    }

    companion object {
        private const val METHOD_NEW_PROCESS = "newProcess"
    }
}

private const val TRUNCATION_MARKER = "...(truncated)"
