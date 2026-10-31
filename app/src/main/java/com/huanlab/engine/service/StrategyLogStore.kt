package com.huanlab.engine.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StrategyLogEntry(
    val timestampMillis: Long,
    val source: String,
    val message: String,
    val success: Boolean?,
) {
    fun format(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
        val mark = when (success) {
            true -> "OK"
            false -> "FAIL"
            null -> "--"
        }
        return "$time [$source][$mark] $message"
    }
}

/** 统一的策略执行记录中心：自动化、性能切换、后台管控共用。 */
class StrategyLogStore {

    private val _entries = MutableStateFlow<List<StrategyLogEntry>>(emptyList())
    val entries: StateFlow<List<StrategyLogEntry>> = _entries.asStateFlow()

    fun record(source: String, message: String, success: Boolean? = null) {
        _entries.update { current ->
            (listOf(StrategyLogEntry(System.currentTimeMillis(), source, message, success)) + current)
                .take(MAX_ENTRIES)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 120
    }
}
