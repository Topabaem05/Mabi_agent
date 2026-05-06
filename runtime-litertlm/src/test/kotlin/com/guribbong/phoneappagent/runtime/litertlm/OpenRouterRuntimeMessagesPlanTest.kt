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
import org.junit.Assert.assertFalse
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
    fun `object stop step is accepted during validation`() {
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
                goal = "Open Settings.",
                foregroundPackage = "com.android.settings",
                lastExternalForegroundPackage = "com.android.settings",
                serializedNodeTree = "text=설정 | desc= | id= | class=android.widget.TextView | package=com.android.settings | editable=false | clickable=false",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Settings", packageName = "com.android.settings")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 2,
            ),
            """
            {
              "summary": "Settings is already visible.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.android.settings"],
              "steps": [
                {"stop": true}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        assertEquals(1, plan.steps.size)
        assertTrue(plan.steps.first().action is AgentAction.Stop)
    }

    @Test
    fun `untargeted files launch infers samsung my files package from candidates`() {
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
                goal = "Open My Files and show the main screen. Read only.",
                foregroundPackage = "com.guribbong.phoneappagent",
                lastExternalForegroundPackage = null,
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(
                    AppCandidate(label = "내 파일", packageName = "com.sec.android.app.myfiles"),
                    AppCandidate(label = "Voice Recorder", packageName = "com.sec.android.app.voicenote"),
                ),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.INITIAL,
                stepBudget = 2,
            ),
            """
            {
              "summary": "Open Files.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.sec.android.app.myfiles"],
              "steps": [
                {"type":"launch_app"},
                {"type":"wait_for_app"},
                {"type":"stop"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val actions = plan.steps.map { it.action }
        assertEquals(AgentAction.LaunchApp("com.sec.android.app.myfiles"), actions[0])
        assertEquals("com.sec.android.app.myfiles", (actions[1] as AgentAction.WaitForApp).packageName)
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

    @Test
    fun `contacts search goal strips read-only safety suffix from query`() {
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
                goal = "Open Contacts and search for Test. Read only; do not call or message anyone.",
                foregroundPackage = "com.samsung.android.app.contacts",
                lastExternalForegroundPackage = "com.samsung.android.app.contacts",
                serializedNodeTree = "text=검색 | desc= | id=com.samsung.android.app.contacts:id/search_src_text | class=android.widget.AutoCompleteTextView | package=com.samsung.android.app.contacts | editable=true | clickable=true",
                recentActionHistory = listOf(
                    AgentAction.LaunchApp("com.samsung.android.app.contacts").historyKey(),
                    AgentAction.WaitForApp("com.samsung.android.app.contacts").historyKey(),
                    AgentAction.Tap(
                        selector = NodeSelector(resourceId = "com.samsung.android.app.contacts:id/menu_search"),
                        label = "Open Contacts search",
                    ).historyKey(),
                    AgentAction.WaitForNode(
                        selector = NodeSelector(resourceId = "com.samsung.android.app.contacts:id/search_src_text"),
                    ).historyKey(),
                ),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Contacts", packageName = "com.samsung.android.app.contacts")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Type the contact search query.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.samsung.android.app.contacts"],
              "steps": [
                {"type":"input_text","selector":{"resourceId":"com.samsung.android.app.contacts:id/search_src_text"},"text":"Test"},
                {"type":"stop"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val input = plan.steps.map { it.action }.filterIsInstance<AgentAction.InputText>().first()
        assertEquals("Test", input.text)
    }

    @Test
    fun `camera open goal drops nonexistent viewfinder selector and stops after foreground`() {
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
                goal = "Open the Camera app only.",
                foregroundPackage = "com.guribbong.phoneappagent",
                lastExternalForegroundPackage = null,
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Camera", packageName = "com.sec.android.app.camera")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.INITIAL,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Launch Camera and wait for preview.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.sec.android.app.camera"],
              "steps": [
                {"type":"launch_app","packageName":"com.sec.android.app.camera"},
                {"type":"wait_for_app","packageName":"com.sec.android.app.camera"},
                {"type":"wait_for_node","selector":{"resourceId":"com.sec.android.app.camera:id/camera_viewfinder","packageName":"com.sec.android.app.camera"}}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val actions = plan.steps.map { it.action }
        assertEquals(3, actions.size)
        assertEquals(AgentAction.LaunchApp("com.sec.android.app.camera"), actions[0])
        assertEquals("com.sec.android.app.camera", (actions[1] as AgentAction.WaitForApp).packageName)
        assertTrue(actions[2] is AgentAction.Stop)
        assertFalse(plan.rawPlanJson.contains("camera_viewfinder"))
    }

    @Test
    fun `selector id alias is accepted and matched against visible resource id suffix`() {
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
                goal = "Wait for the demo screen.",
                foregroundPackage = "com.example.booking",
                lastExternalForegroundPackage = "com.example.booking",
                serializedNodeTree = "id=com.example.booking:id/sv_main_booking | package=com.example.booking",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Booking", packageName = "com.example.booking")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Wait for KorailTalk booking screen.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.example.booking"],
              "steps": [
                {"type":"wait_for_node","selector":{"id":"sv_main_booking"}}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        assertTrue(plan.steps.single().action is AgentAction.Stop)
    }

    @Test
    fun `selector package class and description aliases are accepted`() {
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
                goal = "Wait for the demo app search field.",
                foregroundPackage = "com.example.demo",
                lastExternalForegroundPackage = "com.example.demo",
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Demo", packageName = "com.example.demo")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Wait for demo search.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.example.demo"],
              "steps": [
                {"type":"wait_for_node","selector":{"desc":"Search demo","class":"android.widget.EditText","package":"com.example.demo"}}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val selector = (plan.steps.single().action as AgentAction.WaitForNode).selector
        assertEquals("Search demo", selector.contentDescription)
        assertEquals("android.widget.EditText", selector.className)
        assertEquals("com.example.demo", selector.packageName)
    }

    @Test
    fun `single key action object is accepted`() {
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
                goal = "Tap the demo search tab.",
                foregroundPackage = "com.example.demo",
                lastExternalForegroundPackage = "com.example.demo",
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Demo", packageName = "com.example.demo")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Tap demo search.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.example.demo"],
              "steps": [
                {"tap":{"selector":{"text":"Search","package":"com.example.demo"}},"comment":"single-key action object"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val tap = plan.steps.single().action as AgentAction.Tap
        assertEquals("Search", tap.selector.text)
        assertEquals("com.example.demo", tap.selector.packageName)
    }

    @Test
    fun `korailtalk train lookup taps departure station on default route screen`() {
        val plan = validateKorailPlan(
            serializedNodeTree = """
                id=com.korail.talk:id/v_departure_station | package=com.korail.talk
                text=서울 | id=com.korail.talk:id/tv_departure_station | package=com.korail.talk
                text=부산 | id=com.korail.talk:id/tv_arrival_station | package=com.korail.talk
            """.trimIndent(),
        )

        val tap = plan.steps.map { it.action }.filterIsInstance<AgentAction.Tap>().single()
        assertEquals("com.korail.talk:id/v_departure_station", tap.selector.resourceId)
    }

    @Test
    fun `korailtalk train lookup selects dongdaegu from station sheet`() {
        val plan = validateKorailPlan(
            serializedNodeTree = """
                id=com.korail.talk:id/stationNameEdit | package=com.korail.talk
                text=서울 | id=com.korail.talk:id/tv_departure_station | package=com.korail.talk
                text=동대구 | id=com.korail.talk:id/stationNameTxt | package=com.korail.talk
            """.trimIndent(),
        )

        val tap = plan.steps.map { it.action }.filterIsInstance<AgentAction.Tap>().single()
        assertEquals("동대구", tap.selector.text)
        assertEquals("com.korail.talk:id/stationNameTxt", tap.selector.resourceId)
    }

    @Test
    fun `korailtalk train lookup taps train search after route is set`() {
        val plan = validateKorailPlan(
            serializedNodeTree = """
                text=동대구 | id=com.korail.talk:id/tv_departure_station | package=com.korail.talk
                text=서울 | id=com.korail.talk:id/tv_arrival_station | package=com.korail.talk
                text=열차조회 | id=com.korail.talk:id/btn_right | package=com.korail.talk
            """.trimIndent(),
        )

        val tap = plan.steps.map { it.action }.filterIsInstance<AgentAction.Tap>().single()
        assertEquals("com.korail.talk:id/btn_right", tap.selector.resourceId)
    }

    @Test
    fun `korailtalk booking goal stops at confirm gate before reservation selector`() {
        val plan = validateKorailPlan(
            goal = "Run 코레일톡 and find KTX trains from Daegu to Seoul, then prepare to book one evening option. Stop before payment.",
            serializedNodeTree = """
                text=열차 조회 | id=com.korail.talk:id/titleTxt | package=com.korail.talk
                text=동대구 | id=com.korail.talk:id/departureTxt | package=com.korail.talk
                text=서울 | id=com.korail.talk:id/arrivalTxt | package=com.korail.talk
                text=KTX 220 | id=com.korail.talk:id/trainNameTxt | package=com.korail.talk
                text=예매 | package=com.korail.talk
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        val confirmIndex = actions.indexOfFirst { it is AgentAction.ConfirmUser }
        val reservationTapIndex = actions.indexOfFirst { action ->
            val tap = action as? AgentAction.Tap ?: return@indexOfFirst false
            tap.selector.resourceId == "com.korail.talk:id/standardReserveButton"
        }

        assertTrue(confirmIndex >= 0)
        assertTrue(reservationTapIndex > confirmIndex)
        val reservationTap = actions[reservationTapIndex] as AgentAction.Tap
        assertEquals("43,500원", reservationTap.selector.contentDescription)
    }

    @Test
    fun `korailtalk booking goal confirms only after train results wait`() {
        val plan = validateKorailPlan(
            goal = "Run 코레일톡 and find KTX trains from Daegu to Seoul, then prepare to book one evening option. Stop before payment.",
            serializedNodeTree = """
                text=동대구 | id=com.korail.talk:id/tv_departure_station | package=com.korail.talk
                text=서울 | id=com.korail.talk:id/tv_arrival_station | package=com.korail.talk
                text=열차조회 | id=com.korail.talk:id/btn_right | package=com.korail.talk
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        val searchTapIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.Tap)?.selector?.resourceId == "com.korail.talk:id/btn_right"
        }
        val resultsWaitIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.WaitForNode)?.selector?.resourceId == "com.korail.talk:id/titleTxt"
        }
        val confirmIndex = actions.indexOfFirst { it is AgentAction.ConfirmUser }
        val reservationTapIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.Tap)?.selector?.resourceId == "com.korail.talk:id/standardReserveButton"
        }

        assertTrue(searchTapIndex >= 0)
        assertTrue(resultsWaitIndex > searchTapIndex)
        assertTrue(confirmIndex > resultsWaitIndex)
        assertTrue(reservationTapIndex > confirmIndex)
    }

    @Test
    fun `korailtalk booking replan continues from selected fare to booking button`() {
        val reservationSelector = NodeSelector(
            contentDescription = "43,500원",
            resourceId = "com.korail.talk:id/standardReserveButton",
            packageName = "com.korail.talk",
            clickable = true,
        )
        val plan = validateKorailPlan(
            goal = "Run 코레일톡 and find KTX trains from Daegu to Seoul, then prepare to book one evening option. Stop before payment.",
            serializedNodeTree = """
                text=열차 조회 | id=com.korail.talk:id/titleTxt | package=com.korail.talk
                text=동대구 | id=com.korail.talk:id/departureTxt | package=com.korail.talk
                text=서울 | id=com.korail.talk:id/arrivalTxt | package=com.korail.talk
                text=KTX 220 | id=com.korail.talk:id/trainNameTxt | package=com.korail.talk
                text=예매 | id=com.korail.talk:id/bookingBtn | package=com.korail.talk | clickable=true
            """.trimIndent(),
            recentActionHistory = listOf(
                AgentAction.ConfirmUser("User confirmation required before entering KorailTalk booking.").historyKey(),
                AgentAction.WaitForNode(reservationSelector).historyKey(),
                AgentAction.Tap(
                    selector = reservationSelector,
                    label = "Open KorailTalk reservation",
                ).historyKey(),
            ),
        )

        val actions = plan.steps.map { it.action }
        val bookingWaitIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.WaitForNode)?.selector?.resourceId == "com.korail.talk:id/bookingBtn"
        }
        val bookingTapIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.Tap)?.selector?.resourceId == "com.korail.talk:id/bookingBtn"
        }

        assertFalse(actions.any { it is AgentAction.ConfirmUser })
        assertTrue(bookingTapIndex >= 0)
        if (bookingWaitIndex >= 0) {
            assertTrue(bookingTapIndex > bookingWaitIndex)
        }
    }

    @Test
    fun `korailtalk booking button still requires confirmation without prior gate`() {
        val plan = validateKorailPlan(
            goal = "Run 코레일톡 and find KTX trains from Daegu to Seoul, then prepare to book one evening option. Stop before payment.",
            serializedNodeTree = """
                text=예매 | id=com.korail.talk:id/bookingBtn | package=com.korail.talk | clickable=true
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        val confirmIndex = actions.indexOfFirst { it is AgentAction.ConfirmUser }
        val bookingTapIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.Tap)?.selector?.resourceId == "com.korail.talk:id/bookingBtn"
        }

        assertTrue(confirmIndex >= 0)
        assertTrue(bookingTapIndex > confirmIndex)
    }

    @Test
    fun `play store install goal launches play store first`() {
        val plan = validatePlayStorePlan(
            foregroundPackage = "com.guribbong.phoneappagent",
            lastExternalForegroundPackage = null,
            serializedNodeTree = "",
        )

        val actions = plan.steps.map { it.action }
        assertEquals(
            AgentAction.OpenUri("market://details?id=com.anthropic.claude", "com.android.vending"),
            actions[0],
        )
        assertEquals("com.android.vending", (actions[1] as AgentAction.WaitForApp).packageName)
        assertFalse(actions.any { it is AgentAction.ConfirmUser })
    }

    @Test
    fun `play store install goal does not treat last external package as foreground`() {
        val plan = validatePlayStorePlan(
            foregroundPackage = "com.guribbong.phoneappagent",
            lastExternalForegroundPackage = "com.android.vending",
            serializedNodeTree = """
                text=어떤 도움이 필요하신가요? | id= | class=android.widget.TextView | package=com.guribbong.phoneappagent | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        assertEquals(
            AgentAction.OpenUri("market://details?id=com.anthropic.claude", "com.android.vending"),
            actions[0],
        )
        assertEquals("com.android.vending", (actions[1] as AgentAction.WaitForApp).packageName)
    }

    @Test
    fun `play store install goal opens exact learned app listing when package is known`() {
        val plan = validatePlayStorePlan(
            serializedNodeTree = """
                text=앱 및 게임 검색 | id= | class=android.widget.EditText | package=com.android.vending | editable=true | clickable=true
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        assertEquals(
            AgentAction.OpenUri("market://details?id=com.anthropic.claude", "com.android.vending"),
            actions.first(),
        )
        val installWait = actions.filterIsInstance<AgentAction.WaitForNode>().single()
        assertEquals("설치", installWait.selector.text)
        assertEquals("com.android.vending", installWait.selector.packageName)
        assertFalse(actions.any { it is AgentAction.ConfirmUser })
    }

    @Test
    fun `play store install goal extracts last install target from learning prompt`() {
        val plan = validatePlayStorePlan(
            goal = "Learn the Google Play Store install flow from web search, then install Claude from Google Play Store.",
            serializedNodeTree = """
                text=앱 및 게임 검색 | id= | class=android.widget.EditText | package=com.android.vending | editable=true | clickable=true
            """.trimIndent(),
        )

        val openUri = plan.steps.map { it.action }.filterIsInstance<AgentAction.OpenUri>().single()
        assertEquals("market://details?id=com.anthropic.claude", openUri.uri)
        assertEquals("com.android.vending", openUri.packageName)
    }

    @Test
    fun `play store install goal keeps unknown app query unchanged`() {
        val plan = validatePlayStorePlan(
            goal = "Install ChatGPT from Google Play Store.",
            serializedNodeTree = """
                text=앱 및 게임 검색 | id= | class=android.widget.EditText | package=com.android.vending | editable=true | clickable=true
            """.trimIndent(),
        )

        val input = plan.steps.map { it.action }.filterIsInstance<AgentAction.InputText>().single()
        assertEquals("ChatGPT", input.text)
        assertTrue(plan.steps.map { it.action }.any { action ->
            (action as? AgentAction.Tap)?.selector?.contentDescription == "\"ChatGPT\" 검색 "
        })
    }

    @Test
    fun `play store install goal does not treat search suggestion as target app result`() {
        val plan = validatePlayStorePlan(
            goal = "Learn the Google Play Store install flow from web search, then install Claude from Google Play Store.",
            serializedNodeTree = """
                desc="Claude" 검색 | id= | class=android.view.View | package=com.android.vending | editable=false | clickable=true
                text=앱 및 게임 검색 | id= | class=android.widget.EditText | package=com.android.vending | editable=true | clickable=true
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        assertEquals(
            AgentAction.OpenUri("market://details?id=com.anthropic.claude", "com.android.vending"),
            actions.first(),
        )
        assertFalse(actions.any { action ->
            (action as? AgentAction.Tap)?.selector?.contentDescription == "\"Claude\" 검색"
        })
    }

    @Test
    fun `play store install goal focuses search bar before waiting for editable field`() {
        val plan = validatePlayStorePlan(
            goal = "Install ChatGPT from Google Play Store.",
            serializedNodeTree = """
                desc=Google Play 검색 | id= | class=android.view.View | package=com.android.vending | editable=false | clickable=false
                text=앱 및 게임 검색 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        val searchBarTap = actions.filterIsInstance<AgentAction.Tap>().single()
        assertEquals("Google Play 검색", searchBarTap.selector.contentDescription)
        assertEquals("com.android.vending", searchBarTap.selector.packageName)
        val editWait = actions.filterIsInstance<AgentAction.WaitForNode>().last()
        assertEquals("android.widget.EditText", editWait.selector.className)
        assertEquals(true, editWait.selector.editable)
        assertFalse(actions.any { it is AgentAction.ConfirmUser })
    }

    @Test
    fun `play store install goal ignores unrelated install buttons before target app is visible`() {
        val plan = validatePlayStorePlan(
            serializedNodeTree = """
                desc=이환 N2E 12세 이상 별표 평점: 4.1 | id= | class=android.view.View | package=com.android.vending | editable=false | clickable=false
                text=설치 | desc=설치 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
                text=검색 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        assertFalse(actions.any { it is AgentAction.ConfirmUser })
        assertEquals(
            AgentAction.OpenUri("market://details?id=com.anthropic.claude", "com.android.vending"),
            actions.first(),
        )
    }

    @Test
    fun `play store install goal confirms before install tap and stops after`() {
        val plan = validatePlayStorePlan(
            serializedNodeTree = """
                desc=Claude by Anthropic Anthropic PBC | id= | class=android.view.View | package=com.android.vending | editable=false | clickable=false
                text=설치 | desc=설치 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        val confirmIndex = actions.indexOfFirst { it is AgentAction.ConfirmUser }
        val installTapIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.Tap)?.selector?.text == "설치"
        }
        val stopIndex = actions.indexOfFirst { it is AgentAction.Stop }

        assertTrue(confirmIndex >= 0)
        assertTrue(installTapIndex > confirmIndex)
        assertTrue(stopIndex > installTapIndex)
    }

    @Test
    fun `play store install confirmation reopens exact listing before install tap`() {
        val plan = validatePlayStorePlan(
            goal = "Install Claude from Google Play Store.",
            serializedNodeTree = """
                text=Claude by Anthropic | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
                text=Anthropic PBC | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
                text=설치 | desc=설치 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        val confirmIndex = actions.indexOfFirst { it is AgentAction.ConfirmUser }
        val reopenIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.OpenUri)?.uri == "market://details?id=com.anthropic.claude"
        }
        val installTapIndex = actions.indexOfFirst { action ->
            (action as? AgentAction.Tap)?.selector?.text == "설치"
        }

        assertTrue(confirmIndex >= 0)
        assertTrue(reopenIndex > confirmIndex)
        assertTrue(installTapIndex > reopenIndex)
    }

    @Test
    fun `play store current screen install gate overrides model stop on target detail`() {
        val plan = validatePlayStorePlan(
            goal = "Learn the Google Play Store install flow from web search, then install Claude from Google Play Store. Stop before payment, login, permission, or account prompts.",
            serializedNodeTree = """
                desc=Claude by Anthropic Anthropic PBC | id= | class=android.view.View | package=com.android.vending | editable=false | clickable=false
                text=설치 | desc=설치 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        assertTrue(actions.first() is AgentAction.ConfirmUser)
        assertEquals("설치", (actions.filterIsInstance<AgentAction.Tap>().single()).selector.text)
    }

    @Test
    fun `play store install goal rejects model stop before confirmation gate`() {
        val plan = validatePlayStorePlan(
            goal = "Learn the Google Play Store install flow from web search, then install Claude from Google Play Store.",
            serializedNodeTree = """
                text=검색 | id= | class=android.widget.TextView | package=com.android.vending | editable=false | clickable=false
            """.trimIndent(),
        )

        val actions = plan.steps.map { it.action }
        assertFalse(actions.singleOrNull() is AgentAction.Stop)
        assertTrue(actions.any { action ->
            (action as? AgentAction.OpenUri)?.uri == "market://details?id=com.anthropic.claude"
        })
    }

    @Test
    fun `target package candidates accept object payloads`() {
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
                goal = "Open the demo app.",
                foregroundPackage = "com.guribbong.phoneappagent",
                lastExternalForegroundPackage = null,
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Demo", packageName = "com.example.demo")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.INITIAL,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Launch demo.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": [{"label":"Demo","packageName":"com.example.demo"}],
              "steps": [
                {"type":"launch_app","packageName":"com.example.demo"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        assertEquals(listOf("com.example.demo"), plan.targetPackageCandidates)
    }

    @Test
    fun `action parameters payload is accepted`() {
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
                goal = "Open KorailTalk.",
                foregroundPackage = "com.guribbong.phoneappagent",
                lastExternalForegroundPackage = null,
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "코레일톡", packageName = "com.korail.talk")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.INITIAL,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Launch KorailTalk.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.korail.talk"],
              "steps": [
                {"action":"launch_app","parameters":{"packageName":"com.korail.talk"}},
                {"action":"wait_for_app","parameters":{"packageName":"com.korail.talk"}}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        assertEquals(AgentAction.LaunchApp("com.korail.talk"), plan.steps[0].action)
        assertEquals("com.korail.talk", (plan.steps[1].action as AgentAction.WaitForApp).packageName)
    }

    @Test
    fun `camera capture goal uses shutter selector only after confirm gate`() {
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
                goal = "Open the Camera app and take a photo.",
                foregroundPackage = "com.guribbong.phoneappagent",
                lastExternalForegroundPackage = null,
                serializedNodeTree = "",
                recentActionHistory = emptyList(),
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Camera", packageName = "com.sec.android.app.camera")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.INITIAL,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Launch Camera before taking a photo.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.sec.android.app.camera"],
              "steps": [
                {"type":"launch_app","packageName":"com.sec.android.app.camera"},
                {"type":"wait_for_app","packageName":"com.sec.android.app.camera"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft

        val actions = plan.steps.map { it.action }
        assertEquals(5, actions.size)
        assertEquals(AgentAction.LaunchApp("com.sec.android.app.camera"), actions[0])
        assertEquals("com.sec.android.app.camera", (actions[1] as AgentAction.WaitForApp).packageName)
        assertEquals(
            "com.sec.android.app.camera:id/normal_center_button",
            (actions[2] as AgentAction.WaitForNode).selector.resourceId,
        )
        assertTrue(actions[3] is AgentAction.ConfirmUser)
        val shutterTap = actions[4] as AgentAction.Tap
        assertEquals("com.sec.android.app.camera:id/normal_center_button", shutterTap.selector.resourceId)
        assertEquals("사진 촬영", shutterTap.selector.contentDescription)
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

    private fun validateKorailPlan(
        serializedNodeTree: String,
        goal: String = "Run the 코레일톡 app to check KTX evening trains from Daegu to Seoul. Read only.",
        recentActionHistory: List<String> = emptyList(),
    ): com.guribbong.phoneappagent.core.runner.PlanDraft {
        val validateMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "validatePlan",
                PlannerInput::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }
        return validateMethod.invoke(
            runtime,
            PlannerInput(
                goal = goal,
                foregroundPackage = "com.korail.talk",
                lastExternalForegroundPackage = "com.korail.talk",
                serializedNodeTree = serializedNodeTree,
                recentActionHistory = recentActionHistory,
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "코레일톡", packageName = "com.korail.talk")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Remote model returned a weak Korail plan.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.korail.talk"],
              "steps": [
                {"type":"stop"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft
    }

    private fun validatePlayStorePlan(
        serializedNodeTree: String,
        foregroundPackage: String = "com.android.vending",
        lastExternalForegroundPackage: String? = "com.android.vending",
        recentActionHistory: List<String> = emptyList(),
        goal: String = "Install Claude from Google Play Store.",
    ): com.guribbong.phoneappagent.core.runner.PlanDraft {
        val validateMethod =
            OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod(
                "validatePlan",
                PlannerInput::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }
        return validateMethod.invoke(
            runtime,
            PlannerInput(
                goal = goal,
                foregroundPackage = foregroundPackage,
                lastExternalForegroundPackage = lastExternalForegroundPackage,
                serializedNodeTree = serializedNodeTree,
                recentActionHistory = recentActionHistory,
                riskHints = emptyList(),
                candidateApps = listOf(AppCandidate(label = "Google Play Store", packageName = "com.android.vending")),
                planningMode = com.guribbong.phoneappagent.core.runner.PlanningMode.STEPWISE_REPLAN,
                stepBudget = 4,
            ),
            """
            {
              "summary": "Remote model returned a weak Play Store plan.",
              "riskLevel": "low",
              "needsConfirmation": false,
              "targetPackageCandidates": ["com.android.vending"],
              "steps": [
                {"type":"stop"}
              ]
            }
            """.trimIndent(),
        ) as com.guribbong.phoneappagent.core.runner.PlanDraft
    }
}
