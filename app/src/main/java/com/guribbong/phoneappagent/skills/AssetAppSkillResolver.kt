package com.guribbong.phoneappagent.skills

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AssetAppSkillResolver(
    context: Context? = null,
    private val definitions: List<AppSkillDefinition> = context?.loadSkillDefinitions().orEmpty(),
) : SkillResolver {
    override suspend fun resolve(
        goal: String,
        packageNames: List<String>,
        learnedMemories: Map<String, List<String>>,
    ): List<AppSkillContext> {
        val normalizedGoal = goal.lowercase()
        val normalizedPackages = packageNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (normalizedPackages.isEmpty()) return emptyList()
        return definitions
            .filter { definition -> definition.packageNames.any { it in normalizedPackages } }
            .mapNotNull { definition ->
                val procedures = definition.procedures.filter { procedure ->
                    procedure.goalPatterns.isEmpty() ||
                        procedure.goalPatterns.any { pattern -> pattern.lowercase() in normalizedGoal }
                }.ifEmpty {
                    definition.procedures.take(1)
                }
                if (procedures.isEmpty()) return@mapNotNull null
                val packageName = definition.packageNames.firstOrNull { it in normalizedPackages }
                    ?: definition.packageNames.first()
                AppSkillContext(
                    packageName = packageName,
                    appName = definition.appName,
                    procedures = procedures.take(3),
                    learnedMemories = learnedMemories[packageName].orEmpty().take(4),
                )
            }
            .take(4)
    }
}

private fun Context.loadSkillDefinitions(): List<AppSkillDefinition> =
    assets.list("agent-skills").orEmpty()
        .filter { it.endsWith(".json") }
        .sorted()
        .mapNotNull { fileName ->
            runCatching {
                assets.open("agent-skills/$fileName").bufferedReader().use { reader ->
                    parseSkillDefinition(JSONObject(reader.readText()))
                }
            }.getOrNull()
        }

private fun parseSkillDefinition(json: JSONObject): AppSkillDefinition =
    AppSkillDefinition(
        appName = json.getString("appName"),
        packageNames = json.getJSONArray("packageNames").toStringList(),
        procedures = json.getJSONArray("procedures").toProcedureList(),
    )

private fun JSONArray.toProcedureList(): List<AppSkillProcedure> =
    (0 until length()).map { index ->
        val item = getJSONObject(index)
        AppSkillProcedure(
            name = item.getString("name"),
            goalPatterns = item.optJSONArray("goalPatterns")?.toStringList().orEmpty(),
            riskPoints = item.optJSONArray("riskPoints")?.toStringList().orEmpty(),
            queryAliases = item.optJSONObject("queryAliases")?.toStringMap().orEmpty(),
            steps = item.getJSONArray("steps").toStepList(),
        )
    }

private fun JSONArray.toStepList(): List<AppSkillStepHint> =
    (0 until length()).map { index ->
        val item = getJSONObject(index)
        AppSkillStepHint(
            intent = item.getString("intent"),
            expectedObservation = item.getString("expectedObservation"),
            fallbackStrategy = item.optString("fallbackStrategy").takeIf { it.isNotBlank() },
            preferredSelectors = item.optJSONArray("preferredSelectors")?.toSelectorList().orEmpty(),
        )
    }

private fun JSONArray.toSelectorList(): List<SkillSelectorHint> =
    (0 until length()).map { index ->
        val item = getJSONObject(index)
        SkillSelectorHint(
            text = item.optNullableString("text"),
            contentDescription = item.optNullableString("contentDescription"),
            resourceId = item.optNullableString("resourceId"),
            className = item.optNullableString("className"),
            editable = item.optNullableBoolean("editable"),
            clickable = item.optNullableBoolean("clickable"),
            packageName = item.optNullableString("packageName"),
            nearText = item.optNullableString("nearText"),
        )
    }

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

private fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence()
        .mapNotNull { key ->
            optString(key)
                .takeIf { it.isNotBlank() }
                ?.let { value -> key to value }
        }
        .toMap()

private fun JSONObject.optNullableString(name: String): String? =
    optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (has(name)) getBoolean(name) else null
