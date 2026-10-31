package com.huanlab.engine.action

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.InputStream

/** 一次 shell 命令的真实结果；失败也保留 stderr 供 UI 展示。 */
data class ExecResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0

    fun shortMessage(): String = if (ok) {
        "OK"
    } else {
        val detail = stderr.ifBlank { stdout }.replace('\n', ' ').trim()
        "exit=$exitCode ${detail.take(160)}"
    }
}

class ActionExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 通过 Shizuku 远程进程执行严格白名单的 argv。
 *
 * Shizuku API 13.1.5 的 Shizuku.newProcess 是 private static，因此反射调用；
 * 返回的 ShizukuRemoteProcess 继承 java.lang.Process。执行层只接受结构化的 argv，
 * 不接受自由 shell 字符串，避免命令注入。
 */
class ActionExecutor {

    suspend fun run(argv: List<String>): ExecResult = withContext(Dispatchers.IO) {
        require(argv.isNotEmpty()) { "argv must not be empty" }
        val process = try {
            startRemoteProcess(argv.toTypedArray())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            throw ActionExecutionException(
                "Shizuku.newProcess failed: ${throwable.javaClass.simpleName}",
                throwable,
            )
        }

        try {
            coroutineScope {
                val stdout = async(Dispatchers.IO) { readLimited(process.inputStream) }
                val stderr = async(Dispatchers.IO) { readLimited(process.errorStream) }
                val exitCode = process.waitFor()
                ExecResult(
                    command = argv.joinToString(" "),
                    exitCode = exitCode,
                    stdout = stdout.await(),
                    stderr = stderr.await(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            throw ActionExecutionException(
                "reading remote process failed: ${throwable.javaClass.simpleName}",
                throwable,
            )
        } finally {
            runCatching { process.destroy() }
        }
    }

    suspend fun runAll(commands: List<List<String>>): List<ExecResult> {
        val results = ArrayList<ExecResult>(commands.size)
        for (command in commands) {
            results += try {
                run(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                ExecResult(
                    command = command.joinToString(" "),
                    exitCode = -1,
                    stdout = "",
                    stderr = throwable.message ?: throwable.javaClass.simpleName,
                )
            }
        }
        return results
    }

    private fun readLimited(stream: InputStream): String {
        val limit = 32 * 1024
        val builder = StringBuilder(1024)
        stream.bufferedReader().use { reader ->
            val buffer = CharArray(2048)
            while (builder.length < limit) {
                val remaining = limit - builder.length
                val read = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                builder.append(buffer, 0, read)
            }
        }
        return builder.toString()
    }

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

    private companion object {
        private const val METHOD_NEW_PROCESS = "newProcess"
    }
}
