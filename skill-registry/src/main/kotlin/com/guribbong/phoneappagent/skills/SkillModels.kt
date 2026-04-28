package com.guribbong.phoneappagent.skills

data class SkillSelectorHint(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val editable: Boolean? = null,
    val clickable: Boolean? = null,
    val packageName: String? = null,
    val nearText: String? = null,
)

data class AppSkillStepHint(
    val intent: String,
    val preferredSelectors: List<SkillSelectorHint> = emptyList(),
    val expectedObservation: String,
    val fallbackStrategy: String? = null,
)

data class AppSkillProcedure(
    val name: String,
    val goalPatterns: List<String>,
    val steps: List<AppSkillStepHint>,
    val riskPoints: List<String> = emptyList(),
)

data class AppSkillContext(
    val packageName: String,
    val appName: String,
    val procedures: List<AppSkillProcedure>,
    val learnedMemories: List<String> = emptyList(),
) {
    fun toPlannerText(): String =
        buildString {
            append("app=").append(appName).append(" package=").append(packageName)
            procedures.forEach { procedure ->
                append("\nprocedure=").append(procedure.name)
                append(" patterns=").append(procedure.goalPatterns.joinToString("|"))
                procedure.steps.forEachIndexed { index, step ->
                    append("\n step[").append(index).append("]=").append(step.intent)
                    append(" expect=").append(step.expectedObservation)
                    step.fallbackStrategy?.let { append(" fallback=").append(it) }
                    step.preferredSelectors.take(3).forEach { selector ->
                        append(" selector=").append(selector.toPlannerText())
                    }
                }
                if (procedure.riskPoints.isNotEmpty()) {
                    append("\n risks=").append(procedure.riskPoints.joinToString("; "))
                }
            }
            if (learnedMemories.isNotEmpty()) {
                append("\n learned=").append(learnedMemories.take(4).joinToString(" || "))
            }
        }.take(1_200)
}

data class AppSkillDefinition(
    val packageNames: List<String>,
    val appName: String,
    val procedures: List<AppSkillProcedure>,
)

interface SkillResolver {
    suspend fun resolve(
        goal: String,
        packageNames: List<String>,
        learnedMemories: Map<String, List<String>> = emptyMap(),
    ): List<AppSkillContext>
}

private fun SkillSelectorHint.toPlannerText(): String =
    listOfNotNull(
        text?.let { "text=$it" },
        contentDescription?.let { "desc=$it" },
        resourceId?.let { "id=$it" },
        className?.let { "class=$it" },
        editable?.let { "editable=$it" },
        clickable?.let { "clickable=$it" },
        packageName?.let { "package=$it" },
        nearText?.let { "near=$it" },
    ).joinToString(",")
