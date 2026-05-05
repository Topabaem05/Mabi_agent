package com.guribbong.phoneappagent.core.runner

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector

data class PlanValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
)

class PlanValidator {
    fun validate(plan: PlanDraft): PlanValidationResult {
        val errors = buildList {
            if (plan.rawPlanJson.isBlank()) {
                add("PlanDraft rawPlanJson must not be blank.")
            }
            if (plan.steps.isEmpty()) {
                add("PlanDraft steps must not be empty.")
            }

            plan.steps.forEachIndexed { index, step ->
                validateAction(index, step.action)
            }
        }

        return PlanValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }

    private fun MutableList<String>.validateAction(
        index: Int,
        action: AgentAction,
    ) {
        val prefix = "Step $index"
        when (action) {
            is AgentAction.LaunchApp -> {
                if (action.packageName.isBlank()) {
                    add("$prefix LaunchApp packageName must not be blank.")
                }
            }

            is AgentAction.WaitForApp -> {
                if (action.packageName.isBlank()) {
                    add("$prefix WaitForApp packageName must not be blank.")
                }
                if (action.timeoutMs <= 0L) {
                    add("$prefix WaitForApp timeout must be positive.")
                }
            }

            is AgentAction.WaitForNode -> {
                validateSelector(prefix, "WaitForNode", action.selector)
                if (action.timeoutMs <= 0L) {
                    add("$prefix WaitForNode timeout must be positive.")
                }
            }

            is AgentAction.Tap -> validateSelector(prefix, "Tap", action.selector)
            is AgentAction.InputText -> {
                validateSelector(prefix, "InputText", action.selector)
                if (action.text.isBlank()) {
                    add("$prefix InputText text must not be blank.")
                }
            }

            is AgentAction.AssertVisible -> validateSelector(prefix, "AssertVisible", action.selector)
            is AgentAction.SubmitInput,
            is AgentAction.ClearText,
            is AgentAction.Scroll,
            is AgentAction.PressGlobal,
            is AgentAction.WaitForCondition,
            is AgentAction.ConfirmUser,
            AgentAction.Stop,
            -> Unit
        }
    }

    private fun MutableList<String>.validateSelector(
        prefix: String,
        actionName: String,
        selector: NodeSelector,
    ) {
        if (selector.historyKey().isBlank()) {
            add("$prefix $actionName selector must not be empty.")
        }
    }
}
