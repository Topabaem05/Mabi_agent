package com.guribbong.phoneappagent.runtime.litertlm

import android.content.Context
import android.content.ContextWrapper
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.historyKey
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.runner.AppCandidate
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.core.runner.PlannerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterRuntimeMessagesPlanTest {
    private val runtime =
        OpenRouterLocalAgentRuntime(
            context = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
            },
            policyGate = PolicyGate(),
            config = OpenRouterRuntimeConfig(
                apiKey = "test-key",
                modelName = "google/gemma-4-26b-a4b-it",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                appReferer = "https://example.com",
                appTitle = "Phone App Agent Tests",
            ),
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
                        selector = com.guribbong.phoneappagent.core.dsl.NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/compose_button",
                        ),
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = com.guribbong.phoneappagent.core.dsl.NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/recipient_name_edit_text",
                        ),
                        text = "12345",
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = com.guribbong.phoneappagent.core.dsl.NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/recipient_item",
                        ),
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = com.guribbong.phoneappagent.core.dsl.NodeSelector(
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
                        selector = com.guribbong.phoneappagent.core.dsl.NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/send_button",
                        ),
                    ),
                    expectedObservation = "",
                ),
            ),
        ).map { it.action }

        assertEquals(14, actions.size)
        assertEquals(
            AgentAction.LaunchApp("com.samsung.android.messaging"),
            actions[0],
        )
        assertEquals(
            AgentAction.WaitForApp("com.samsung.android.messaging"),
            actions[1],
        )
        assertEquals(
            "com.samsung.android.messaging:id/fab",
            (actions[2] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertEquals(
            "com.samsung.android.messaging:id/fab",
            (actions[3] as AgentAction.Tap).selector.resourceId,
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
        assertEquals(
            "com.samsung.android.messaging:id/message_edit_text",
            (actions[10] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertEquals("hello", (actions[11] as AgentAction.InputText).text)
        assertTrue(actions[12] is AgentAction.ConfirmUser)
        assertEquals("com.samsung.android.messaging:id/send_button", (actions[13] as AgentAction.Tap).selector.resourceId)
        assertEquals("com.samsung.android.messaging", (actions[13] as AgentAction.Tap).selector.packageName)
    }

    @Test
    fun `settings search replan canonicalizes samsung intelligence selectors`() {
        val normalizeMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "normalizeSamsungSettingsSearchPlan",
                String::class.java,
                List::class.java,
            ).apply {
                isAccessible = true
            }
        val adaptMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "adaptStepsToCurrentProgress",
                PlannerInput::class.java,
                List::class.java,
            ).apply {
                isAccessible = true
            }

        val normalizedSteps = normalizeMethod.invoke(
            runtime,
            "Open Settings and search for wifi",
            listOf(
                ExecutionStep(
                    action = AgentAction.WaitForNode(
                        selector = NodeSelector(
                            resourceId = "com.android.settings:id/search_results",
                            packageName = "com.android.settings",
                        ),
                    ),
                    expectedObservation = "",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = NodeSelector(
                            text = "Wi-Fi",
                            packageName = "com.android.settings",
                        ),
                    ),
                    expectedObservation = "",
                ),
            ),
        ) as List<ExecutionStep>

        val adaptedSteps = adaptMethod.invoke(
            runtime,
            PlannerInput(
                goal = "Open Settings and search for wifi",
                foregroundPackage = "com.android.settings.intelligence",
                lastExternalForegroundPackage = "com.android.settings.intelligence",
                serializedNodeTree =
                    listOf(
                        "text=Search settings | desc= | id=com.android.settings.intelligence:id/search_src_text | class=android.widget.EditText | package=com.android.settings.intelligence | editable=true | clickable=true",
                    ).joinToString(separator = "\n"),
                recentActionHistory = listOf(
                    AgentAction.LaunchApp("com.android.settings").historyKey(),
                    AgentAction.WaitForApp("com.android.settings").historyKey(),
                    AgentAction.WaitForNode(
                        NodeSelector(
                            contentDescription = "설정 검색",
                            packageName = "com.android.settings",
                        ),
                    ).historyKey(),
                    AgentAction.Tap(
                        selector = NodeSelector(
                            contentDescription = "설정 검색",
                            packageName = "com.android.settings",
                        ),
                    ).historyKey(),
                    AgentAction.WaitForNode(
                        NodeSelector(
                            resourceId = "com.android.settings.intelligence:id/search_src_text",
                            packageName = "com.android.settings.intelligence",
                        ),
                    ).historyKey(),
                    AgentAction.InputText(
                        selector = NodeSelector(
                            resourceId = "com.android.settings.intelligence:id/search_src_text",
                            packageName = "com.android.settings.intelligence",
                        ),
                        text = "Wi-Fi",
                    ).historyKey(),
                    AgentAction.SubmitInput(
                        NodeSelector(
                            resourceId = "com.android.settings.intelligence:id/search_src_text",
                            packageName = "com.android.settings.intelligence",
                        ),
                    ).historyKey(),
                ),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Settings", packageName = "com.android.settings")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            normalizedSteps,
        ) as List<ExecutionStep>

        assertEquals(2, adaptedSteps.size)
        val firstAction = adaptedSteps.first().action as AgentAction.WaitForNode
        assertEquals("Wi-Fi", firstAction.selector.text)
        assertEquals("com.android.settings.intelligence", firstAction.selector.packageName)
        assertTrue(adaptedSteps[1].action is AgentAction.Stop)
    }

    @Test
    fun `wifi settings goal normalizes to samsung search flow instead of brittle search result ids`() {
        val validateMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "validatePlan",
                PlannerInput::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }

        val plan = validateMethod.invoke(
            runtime,
            PlannerInput(
                goal = "Open Settings and go to the Wi-Fi or network settings page. Read only; do not change any setting.",
                foregroundPackage = "com.android.settings",
                lastExternalForegroundPackage = "com.android.settings",
                serializedNodeTree = "text=연결 | desc= | id=android:id/title | class=android.widget.TextView | package=com.android.settings | editable=false | clickable=false\n" +
                    "text=Wi-Fi  •  블루투스  •  비행기 탑승 모드 | desc= | id=android:id/summary | class=android.widget.TextView | package=com.android.settings | editable=false | clickable=false",
                recentActionHistory = listOf(
                    AgentAction.LaunchApp("com.android.settings").historyKey(),
                    AgentAction.WaitForApp("com.android.settings").historyKey(),
                ),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Settings", packageName = "com.android.settings")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Search for Wi-Fi.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.android.settings"],
              "steps": [
                {"type":"wait_for_node","selector":{"resourceId":"com.android.settings:id/search_result_container"}},
                {"type":"tap","selector":{"text":"Wi-Fi","resourceId":"com.android.settings:id/search_result_item_title"}}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val actions = plan.steps.map { it.action }
        assertTrue(actions.toString(), actions[0] is AgentAction.WaitForNode)
        assertEquals("설정 검색", (actions[0] as AgentAction.WaitForNode).selector.contentDescription)
        assertEquals("설정 검색", (actions[1] as AgentAction.Tap).selector.contentDescription)
        assertEquals("com.android.settings.intelligence:id/search_src_text", (actions[2] as AgentAction.WaitForNode).selector.resourceId)
        assertEquals("Wi-Fi", (actions[3] as AgentAction.InputText).text)
    }

    @Test
    fun `string stop step is accepted during validation`() {
        val validateMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "validatePlan",
                PlannerInput::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }

        val plan = validateMethod.invoke(
            runtime,
            PlannerInput(
                goal = "Open Settings",
                foregroundPackage = "com.android.settings",
                lastExternalForegroundPackage = "com.android.settings",
                serializedNodeTree = "",
                recentActionHistory = listOf(
                    AgentAction.LaunchApp("com.android.settings").historyKey(),
                    AgentAction.WaitForApp("com.android.settings").historyKey(),
                ),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Settings", packageName = "com.android.settings")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 2,
            ),
            """
            {
              "summary": "Settings is already open.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.android.settings"],
              "steps": ["stop"]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        assertEquals(1, plan.steps.size)
        assertTrue(plan.steps.first().action is AgentAction.Stop)
    }

    @Test
    fun `prepare message without send normalizes to confirm gated samsung messages flow`() {
        val validateMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "validatePlan",
                PlannerInput::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }

        val plan = validateMethod.invoke(
            runtime,
            PlannerInput(
                goal = "Open Messages and prepare a text message to Test Contact saying hello, but do not send it. Stop before any send action and ask for confirmation.",
                foregroundPackage = "com.guribbong.phoneappagent",
                lastExternalForegroundPackage = null,
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Messages", packageName = "com.samsung.android.messaging")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.INITIAL,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Launch Messages first.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.samsung.android.messaging"],
              "steps": [
                {"type":"launch_app","packageName":"com.samsung.android.messaging"},
                {"type":"wait_for_app","packageName":"com.samsung.android.messaging"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val actions = plan.steps.map { it.action }
        assertEquals(4, actions.size)
        assertEquals(AgentAction.LaunchApp("com.samsung.android.messaging"), actions[0])
        assertEquals("com.samsung.android.messaging", (actions[1] as AgentAction.WaitForApp).packageName)
        assertEquals("com.samsung.android.messaging:id/fab", (actions[2] as AgentAction.WaitForNode).selector.resourceId)
        assertEquals("com.samsung.android.messaging:id/fab", (actions[3] as AgentAction.Tap).selector.resourceId)
    }

    private fun normalizePlan(steps: List<ExecutionStep>): List<ExecutionStep> {
        val method =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
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
