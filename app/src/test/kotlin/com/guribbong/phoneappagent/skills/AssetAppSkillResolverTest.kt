package com.guribbong.phoneappagent.skills

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetAppSkillResolverTest {
    @Test
    fun `skill context renders compact planner guidance`() {
        val context = AppSkillContext(
            packageName = "com.android.settings",
            appName = "Settings",
            procedures = listOf(
                AppSkillProcedure(
                    name = "search_settings",
                    goalPatterns = listOf("search", "wifi"),
                    steps = listOf(
                        AppSkillStepHint(
                            intent = "Open Settings search",
                            preferredSelectors = listOf(
                                SkillSelectorHint(
                                    resourceId = "com.android.settings:id/search_action_bar",
                                    contentDescription = "Search settings",
                                    clickable = true,
                                ),
                            ),
                            expectedObservation = "Search field is visible",
                        ),
                    ),
                    riskPoints = listOf("Do not toggle Wi-Fi without confirm_user."),
                ),
            ),
            learnedMemories = listOf("success: search field resource id worked"),
        )

        val rendered = context.toPlannerText()

        assertTrue(rendered.contains("Settings"))
        assertTrue(rendered.contains("search_settings"))
        assertTrue(rendered.contains("com.android.settings:id/search_action_bar"))
        assertTrue(rendered.contains("Do not toggle Wi-Fi without confirm_user."))
        assertTrue(rendered.contains("success: search field resource id worked"))
    }

    @Test
    fun `resolver filters procedures by package and goal`() {
        val resolver = AssetAppSkillResolver(
            definitions = listOf(
                AppSkillDefinition(
                    packageNames = listOf("com.android.settings", "com.google.android.settings.intelligence"),
                    appName = "Settings",
                    procedures = listOf(
                        AppSkillProcedure(
                            name = "search_settings",
                            goalPatterns = listOf("settings", "wifi", "search"),
                            steps = listOf(
                                AppSkillStepHint(
                                    intent = "Tap search",
                                    preferredSelectors = listOf(SkillSelectorHint(contentDescription = "Search settings")),
                                    expectedObservation = "Search field visible",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = runBlocking {
            resolver.resolve(
                goal = "Open Settings and search for wifi.",
                packageNames = listOf("com.android.settings"),
                learnedMemories = mapOf("com.android.settings" to listOf("success: search worked")),
            )
        }

        assertEquals(1, result.size)
        assertEquals("Settings", result.single().appName)
        assertEquals(listOf("success: search worked"), result.single().learnedMemories)
    }
}
