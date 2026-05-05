package com.guribbong.phoneappagent.core.policy

import com.guribbong.phoneappagent.core.dsl.AgentAction

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class PolicyDecision(
    val allowed: Boolean,
    val needsConfirmation: Boolean,
    val reason: String,
)

class PolicyGate {
    fun deriveRiskLevel(goal: String): RiskLevel {
        val lower = goal.lowercase()
        return when {
            criticalWords.any { it in lower } -> RiskLevel.CRITICAL
            highRiskWords.any { it in lower } -> RiskLevel.HIGH
            mediumRiskWords.any { it in lower } -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun evaluate(
        goal: String,
        actions: List<AgentAction>,
        runtimeRiskLevel: RiskLevel,
    ): PolicyDecision {
        val derivedRisk = maxOf(runtimeRiskLevel, deriveRiskLevel(goal), deriveActionRisk(actions))
        return when (derivedRisk) {
            RiskLevel.HIGH, RiskLevel.CRITICAL -> PolicyDecision(
                allowed = false,
                needsConfirmation = true,
                reason = "High-risk goal must stop at confirm_user before commit actions.",
            )

            RiskLevel.LOW, RiskLevel.MEDIUM -> PolicyDecision(
                allowed = true,
                needsConfirmation = false,
                reason = "Plan can proceed without manual confirmation.",
            )
        }
    }

    private fun deriveActionRisk(actions: List<AgentAction>): RiskLevel {
        if (actions.any { it is AgentAction.ConfirmUser }) {
            return RiskLevel.HIGH
        }
        if (
            actions.any {
                it is AgentAction.InputText ||
                    it is AgentAction.SubmitInput ||
                    it is AgentAction.ClearText
            }
        ) {
            return RiskLevel.MEDIUM
        }
        return RiskLevel.LOW
    }

    private companion object {
        val mediumRiskWords = listOf(
            "login",
            "sign in",
            "submit",
            "apply",
            "confirm",
        )
        val highRiskWords = listOf(
            "delete",
            "remove",
            "post",
            "share",
            "grant permission",
            "permission",
            "install",
            "logout",
            "삭제",
            "공유",
            "설치",
            "권한 허용",
            "업로드",
        )
        val criticalWords = listOf(
            "send",
            "pay",
            "purchase",
            "buy",
            "transfer",
            "wire money",
            "전송",
            "송금",
            "결제",
        )
    }
}
