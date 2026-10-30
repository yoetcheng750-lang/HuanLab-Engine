package com.huanlab.engine.shared

sealed interface Capability {
    data object Available : Capability
    data object NeedsShizuku : Capability
    data object RootOnly : Capability

    data class Unavailable(val reason: String) : Capability {
        init {
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }
}
