package com.guribbong.phoneappagent.core.runner

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.policy.RiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {
    private val validator = PlanValidator()

    @Test
    fun rejectsBlankLaunchAppPackage() {
        assertInvalid(AgentAction.LaunchApp(" "), "LaunchApp packageName")
    }

    @Test
    fun rejectsBlankWaitForAppPackage() {
        assertInvalid(AgentAction.WaitForApp("\t"), "WaitForApp packageName")
    }

    @Test
    fun rejectsEmptyTapSelector() {
        assertInvalid(AgentAction.Tap(NodeSelector()), "Tap selector")
    }

    @Test
    fun rejectsEmptyInputTextSelector() {
        assertInvalid(
            AgentAction.InputText(
                selector = NodeSelector(),
                text = "hello",
            ),
            "InputText selector",
        )
    }

    @Test
    fun rejectsEmptyAssertVisibleSelector() {
        assertInvalid(AgentAction.AssertVisible(NodeSelector()), "AssertVisible selector")
    }

    @Test
    fun rejectsEmptyWaitForNodeSelector() {
        assertInvalid(
            AgentAction.WaitForNode(
                selector = NodeSelector(),
                timeoutMs = 1_000L,
            ),
            "WaitForNode selector",
        )
    }

    @Test
    fun rejectsNonPositiveWaitForAppTimeout() {
        assertInvalid(AgentAction.WaitForApp("com.android.settings", timeoutMs = 0L), "timeout")
    }

    @Test
    fun rejectsNonPositiveWaitForNodeTimeout() {
        assertInvalid(
            AgentAction.WaitForNode(
                selector = validSelector(),
                timeoutMs = -1L,
            ),
            "timeout",
        )
    }

    @Test
    fun rejectsBlankInputTextTargetText() {
        assertInvalid(
            AgentAction.InputText(
                selector = validSelector(),
                text = "\n ",
            ),
            "InputText text",
        )
    }

    @Test
    fun rejectsBlankRawPlanJson() {
        val result = validator.validate(
            plan(
                step(AgentAction.Stop),
                rawPlanJson = " ",
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("rawPlanJson") })
    }

    @Test
    fun rejectsEmptySteps() {
        val result = validator.validate(plan())

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("steps") })
    }

    @Test
    fun acceptsStopOnlySafeCheckpoint() {
        val result = validator.validate(plan(step(AgentAction.Stop)))

        assertTrue(result.errors.joinToString(), result.isValid)
    }

    private fun assertInvalid(
        action: AgentAction,
        expectedErrorToken: String,
    ) {
        val result = validator.validate(plan(step(action)))

        assertFalse(result.isValid)
        assertTrue(
            "Expected error containing '$expectedErrorToken' but got ${result.errors}",
            result.errors.any { it.contains(expectedErrorToken) },
        )
    }

    private fun validSelector(): NodeSelector = NodeSelector(resourceId = "com.android.settings:id/title")

    private fun step(action: AgentAction): ExecutionStep =
        ExecutionStep(
            action = action,
            expectedObservation = "Expected observation.",
        )

    private fun plan(
        vararg steps: ExecutionStep,
        rawPlanJson: String = """{"steps":[{"action":"stop"}]}""",
    ): PlanDraft =
        PlanDraft(
            summary = "Test plan",
            steps = steps.toList(),
            riskLevel = RiskLevel.LOW,
            needsConfirmation = false,
            targetPackageCandidates = emptyList(),
            rawModelOutput = "model output",
            rawPlanJson = rawPlanJson,
        )
}
