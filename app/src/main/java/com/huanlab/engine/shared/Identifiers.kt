package com.huanlab.engine.shared

/** 首字符字母数字，其后字母数字或 _ . : -，总长 ≤ 128；不含空白、换行、NUL。 */
internal val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")

@JvmInline
value class ActionId(val value: String) {
    init {
        require(IDENTIFIER_PATTERN.matches(value)) { "invalid ActionId" }
    }
}

@JvmInline
value class TransactionId(val value: String) {
    init {
        require(IDENTIFIER_PATTERN.matches(value)) { "invalid TransactionId" }
    }
}

@JvmInline
value class ParameterKey(val value: String) {
    init {
        require(IDENTIFIER_PATTERN.matches(value)) { "invalid ParameterKey" }
    }
}
