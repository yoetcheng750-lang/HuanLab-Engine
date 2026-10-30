package com.huanlab.engine.shared

/** 本文件均为预留类型（用户已豁免“无调用即删除”），仅声明字段，无逻辑。 */
sealed interface ParameterValue {
    data class Readable(val value: String) : ParameterValue

    data class Unreadable(val reason: String) : ParameterValue {
        init {
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }
}

data class BaselineOwnership(
    val parameter: ParameterKey,
    val ownerTransactionId: TransactionId,
    val baseline: ParameterValue,
)

/** baseline=A，lastApplied=B，current=C。 */
data class RollbackConflict(
    val transactionId: TransactionId,
    val parameter: ParameterKey,
    val baseline: ParameterValue,
    val lastApplied: ParameterValue,
    val current: ParameterValue,
)
