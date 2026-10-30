package com.huanlab.engine.capability

/**
 * 仅表示通过 Shizuku 通道读回的 uid 分类。
 * uid=2000 不能区分“ADB 启动”与“Shizuku 以 shell 身份启动”，此处不做该推断。
 */
enum class IdentityKind(val expectedUid: Int) {
    SHELL_UID_2000(2000),
    ROOT_UID_0(0),
}

sealed interface IdentityResolution {
    data class Resolved(
        val kind: IdentityKind,
        val uid: Int,
        val idOutput: String,
    ) : IdentityResolution {
        init {
            require(uid == kind.expectedUid) {
                "identity kind/uid mismatch: ${kind.name} vs uid=$uid"
            }
            require(idOutput.isNotBlank()) { "idOutput must not be blank" }
        }
    }

    data class Unresolved(val reason: String) : IdentityResolution {
        init {
            require(reason.isNotBlank()) { "Unresolved.reason must not be blank" }
        }
    }
}
