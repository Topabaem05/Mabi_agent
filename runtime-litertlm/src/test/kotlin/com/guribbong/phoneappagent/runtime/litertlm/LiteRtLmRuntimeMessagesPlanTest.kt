package com.guribbong.phoneappagent.runtime.litertlm

import android.content.Context
import android.content.ContextWrapper
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmRuntimeMessagesPlanTest {
    private val runtime =
        LiteRtLmLocalAgentRuntime(
            context = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
            },
            policyGate = PolicyGate(),
            config = LiteRtLmRuntimeConfig(),
        )

    @Test
    fun `messages goal normalizes to samsung selectors and confirm gate`() {
        val actions = normalizePlan(
            listOf(
                ExecutionStep(
                    action = AgentAction.LaunchApp("com.samsung.android.messaging"),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.WaitForApp("com.samsung.android.messaging"),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/compose_button",
                        ),
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/recipient_name_edit_text",
                        ),
                        text = "12345",
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/recipient_item",
                        ),
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/message_edit_text",
                        ),
                        text = "hello",
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.ConfirmUser("User confirmation required before commit actions."),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/send_button",
                        ),
                    ),
                    expectedObservation = "",
                ),
            ),
        ).map { it.action }

        assertEquals(14, actions.size)
        assertEquals(
            "com.samsung.android.messaging:id/fab",
            (actions[2] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertEquals(
            "com.samsung.android.messaging:id/chat_fab",
            (actions[4] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertEquals(
            "com.samsung.android.messaging:id/chat_fab",
            (actions[5] as AgentAction.Tap).selector.resourceId,
        )
        assertEquals(
            "com.samsung.android.messaging:id/search_src_text",
            (actions[6] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertEquals(
            "12345",
            (actions[7] as AgentAction.InputText).text,
        )
        assertEquals(
            "com.samsung.android.messaging:id/chat_with_button",
            (actions[8] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertEquals(
            "com.samsung.android.messaging:id/chat_with_button",
            (actions[9] as AgentAction.Tap).selector.resourceId,
        )
        assertEquals("com.samsung.android.messaging:id/message_edit_text", (actions[10] as AgentAction.WaitForNode).selector.resourceId)
        assertEquals("hello", (actions[11] as AgentAction.InputText).text)
        assertTrue(actions[12] is AgentAction.ConfirmUser)
        assertEquals("com.samsung.android.messaging:id/send_button", (actions[13] as AgentAction.Tap).selector.resourceId)
        assertEquals("com.samsung.android.messaging", (actions[13] as AgentAction.Tap).selector.packageName)
    }

    private fun normalizePlan(steps: List<ExecutionStep>): List<ExecutionStep> {
        val method =
            LiteRtLmLocalAgentRuntime::class.java.getDeclaredMethod(
                "normalizeSamsungMessagesSendPlan",
                String::class.java,
                List::class.java,
            ).apply {
                isAccessible = true
            }
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            runtime,
            "Open Messages and send hello to 12345",
            steps,
        ) as List<ExecutionStep>
    }
}
