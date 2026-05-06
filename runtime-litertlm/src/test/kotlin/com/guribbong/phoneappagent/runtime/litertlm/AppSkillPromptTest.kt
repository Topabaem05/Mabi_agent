package com.guribbong.phoneappagent.runtime.litertlm

import android.content.Context
import android.content.ContextWrapper
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.runner.PlannerInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSkillPromptTest {
    @Test
    fun openrouterPromptIncludesAppSkillGuidanceAndVisibleNodeSourceOfTruth() {
        val runtime = OpenRouterLocalAgentRuntime(
            context = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
            },
            policyGate = PolicyGate(),
            config = OpenRouterRuntimeConfig(
                apiKey = "test",
                modelName = "google/gemma-4-26b-a4b-it",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                appReferer = "https://example.com",
                appTitle = "Phone App Agent Tests",
            ),
        )
        val method = OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod("buildPrompt", PlannerInput::class.java)
        method.isAccessible = true
        val prompt = method.invoke(runtime, settingsPlannerInput()) as String

        assertTrue(prompt.contains("appSkillGuidance"))
        assertTrue(prompt.contains("search_settings"))
        assertTrue(prompt.contains("visibleNodes"))
        assertFalse(prompt.contains("LiteRT"))
        assertFalse(prompt.contains("OpenCL"))
    }

    @Test
    fun openrouterPromptKeepsCompactSnapshotReferencesIndexPathsAndBounds() {
        val runtime = OpenRouterLocalAgentRuntime(
            context = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
            },
            policyGate = PolicyGate(),
            config = OpenRouterRuntimeConfig(
                apiKey = "test",
                modelName = "google/gemma-4-26b-a4b-it",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                appReferer = "https://example.com",
                appTitle = "Phone App Agent Tests",
            ),
        )
        val method = OpenRouterLocalAgentRuntime::class.java.getDeclaredMethod("buildPrompt", PlannerInput::class.java)
        method.isAccessible = true
        val prompt = method.invoke(
            runtime,
            settingsPlannerInput(
                serializedNodeTree = "ref=@e1 | role=text_field | text= | desc=Search | id= | class=android.widget.EditText | package=com.android.settings | editable=true | clickable=true | idx=0.1 | bounds=1,2,3,4",
            ),
        ) as String

        assertTrue(prompt.contains("ref=@e1"))
        assertTrue(prompt.contains("r=text_field"))
        assertTrue(prompt.contains("idx=0.1"))
        assertTrue(prompt.contains("b=1,2,3,4"))
    }

    @Test
    fun openrouterSystemInstructionMapsPdfSkillAliasesToDslOnly() {
        val instruction = OpenRouterLocalAgentRuntime.SYSTEM_INSTRUCTION

        assertTrue(instruction.contains("snapshotScreen/read screen means use visibleNodes"))
        assertTrue(instruction.contains("findElement means create a selector"))
        assertTrue(instruction.contains("clickElement means tap"))
        assertTrue(instruction.contains("fillField means clear_text then input_text"))
        assertTrue(instruction.contains("scrollView means scroll"))
        assertTrue(instruction.contains("takeScreenshot is QA-only and not a runtime action"))
        assertTrue(instruction.contains("Never execute a skill directly"))
    }

    private fun settingsPlannerInput(
        serializedNodeTree: String = "text=Search settings | desc=Search settings | id= | class=android.widget.TextView | package=com.android.settings | editable=false | clickable=true",
    ): PlannerInput =
        PlannerInput(
            goal = "Open Settings and search for wifi.",
            foregroundPackage = "com.android.settings",
            lastExternalForegroundPackage = "com.android.settings",
            serializedNodeTree = serializedNodeTree,
            recentActionHistory = emptyList(),
            appMemory = emptyList(),
            appSkillGuidance = listOf("app=Settings package=com.android.settings\nprocedure=search_settings"),
            riskHints = emptyList(),
        )
}
