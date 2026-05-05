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

    private fun settingsPlannerInput(): PlannerInput =
        PlannerInput(
            goal = "Open Settings and search for wifi.",
            foregroundPackage = "com.android.settings",
            lastExternalForegroundPackage = "com.android.settings",
            serializedNodeTree = "text=Search settings | desc=Search settings | id= | class=android.widget.TextView | package=com.android.settings | editable=false | clickable=true",
            recentActionHistory = emptyList(),
            appMemory = emptyList(),
            appSkillGuidance = listOf("app=Settings package=com.android.settings\nprocedure=search_settings"),
            riskHints = emptyList(),
        )
}
