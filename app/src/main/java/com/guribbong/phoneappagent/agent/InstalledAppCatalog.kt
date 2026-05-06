package com.guribbong.phoneappagent.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.guribbong.phoneappagent.core.runner.AppCandidate

class InstalledAppCatalog(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val launcherApps: List<AppCandidate> by lazy(::queryLauncherApps)
    private val aliasSpecs = listOf(
        AliasSpec(
            key = "settings",
            label = "Settings",
            preferredPackages = listOf("com.android.settings"),
            preferredLabelTokens = listOf("settings", "설정"),
        ),
        AliasSpec(
            key = "chrome",
            label = "Chrome",
            preferredPackages = listOf("com.android.chrome"),
            preferredLabelTokens = listOf("chrome", "크롬"),
        ),
        AliasSpec(
            key = "contacts",
            label = "Contacts",
            preferredPackages = listOf(
                "com.samsung.android.app.contacts",
                "com.google.android.contacts",
            ),
            preferredLabelTokens = listOf("contacts", "연락처"),
        ),
        AliasSpec(
            key = "maps",
            label = "Maps",
            preferredPackages = listOf("com.google.android.apps.maps"),
            preferredLabelTokens = listOf("maps", "지도"),
        ),
        AliasSpec(
            key = "messages",
            label = "Messages",
            preferredPackages = listOf(
                "com.samsung.android.messaging",
                "com.google.android.apps.messaging",
            ),
            preferredLabelTokens = listOf("messages", "message", "메시지", "sms"),
        ),
        AliasSpec(
            key = "clock",
            label = "Clock",
            preferredPackages = listOf(
                "com.sec.android.app.clockpackage",
                "com.google.android.deskclock",
            ),
            preferredLabelTokens = listOf("clock", "시계"),
        ),
        AliasSpec(
            key = "calculator",
            label = "Calculator",
            preferredPackages = listOf(
                "com.sec.android.app.popupcalculator",
                "com.android.calculator2",
            ),
            preferredLabelTokens = listOf("calculator", "계산기"),
        ),
        AliasSpec(
            key = "files",
            label = "Files",
            preferredPackages = listOf(
                "com.sec.android.app.myfiles",
                "com.google.android.documentsui",
            ),
            preferredLabelTokens = listOf("files", "파일", "내 파일"),
        ),
        AliasSpec(
            key = "play store",
            label = "Google Play Store",
            preferredPackages = listOf("com.android.vending"),
            preferredLabelTokens = listOf("play store", "google play", "플레이 스토어", "스토어"),
        ),
        AliasSpec(
            key = "google play",
            label = "Google Play Store",
            preferredPackages = listOf("com.android.vending"),
            preferredLabelTokens = listOf("play store", "google play", "플레이 스토어", "스토어"),
        ),
        AliasSpec(
            key = "install",
            label = "Google Play Store",
            preferredPackages = listOf("com.android.vending"),
            preferredLabelTokens = listOf("play store", "google play", "플레이 스토어", "스토어"),
        ),
        AliasSpec(
            key = "설치",
            label = "Google Play Store",
            preferredPackages = listOf("com.android.vending"),
            preferredLabelTokens = listOf("play store", "google play", "플레이 스토어", "스토어"),
        ),
        AliasSpec(
            key = "코레일톡",
            label = "KorailTalk",
            preferredPackages = listOf("com.korail.talk"),
            preferredLabelTokens = listOf("코레일톡", "korail", "ktx", "letskorail"),
        ),
        AliasSpec(
            key = "korail",
            label = "KorailTalk",
            preferredPackages = listOf("com.korail.talk"),
            preferredLabelTokens = listOf("코레일톡", "korail", "ktx", "letskorail"),
        ),
        AliasSpec(
            key = "ktx",
            label = "KorailTalk",
            preferredPackages = listOf("com.korail.talk"),
            preferredLabelTokens = listOf("코레일톡", "korail", "ktx", "letskorail"),
        ),
    )
    private val staticAliases: Map<String, AppCandidate> =
        buildMap {
            aliasSpecs.forEach { spec ->
                resolvePreferredCandidate(spec)?.let { candidate ->
                    put(spec.key, candidate)
                }
            }
        }

    fun candidateAppsForGoal(
        goal: String,
        limit: Int = 8,
    ): List<AppCandidate> {
        val lower = goal.lowercase()
        val candidates = linkedMapOf<String, AppCandidate>()

        staticAliases
            .filterKeys { alias -> alias in lower }
            .values
            .forEach { candidate -> candidates[candidate.packageName] = candidate }

        launcherApps
            .sortedBy { candidate ->
                score(lower, candidate)
            }.reversed()
            .take(limit)
            .forEach { candidate ->
            if (score(lower, candidate) > 0) {
                candidates.putIfAbsent(candidate.packageName, candidate)
            }
        }
        return candidates.values.take(limit)
    }

    fun missingLaunchableAliasReason(goal: String): String? {
        val lower = goal.lowercase()
        val missingAlias = aliasSpecs.firstOrNull { spec ->
            spec.key in lower && spec.key !in staticAliases
        } ?: return null
        return "No launchable ${missingAlias.label} app found on this device."
    }

    fun goalAliasLabel(goal: String): String? =
        aliasSpecs.firstOrNull { spec ->
            spec.key in goal.lowercase()
        }?.label

    fun displayName(packageName: String?): String =
        packageName?.let { pkg ->
            runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrElse { pkg }
        } ?: "Pending target app"

    private fun score(
        lowerGoal: String,
        candidate: AppCandidate,
    ): Int {
        val label = candidate.label.lowercase()
        val packageName = candidate.packageName.lowercase()
        val meaningfulTokens =
            lowerGoal
                .split(' ')
                .map { token -> token.trim() }
                .filter { token ->
                    token.length >= 3 &&
                        token !in GENERIC_GOAL_TOKENS
                }
        return when {
            lowerGoal.contains(label) -> 100
            label.contains(lowerGoal) -> 90
            lowerGoal.contains(packageName) -> 85
            meaningfulTokens.any { token -> token in label || token in packageName } -> 60
            else -> 0
        }
    }

    private fun resolvePreferredCandidate(spec: AliasSpec): AppCandidate? {
        val launchablePreferred = spec.preferredPackages.firstOrNull(::hasLaunchIntent)
        if (launchablePreferred != null) {
            return AppCandidate(label = spec.label, packageName = launchablePreferred)
        }
        val launchableByLabel =
            launcherApps.firstOrNull { candidate ->
                val lowerLabel = candidate.label.lowercase()
                val lowerPackage = candidate.packageName.lowercase()
                spec.preferredLabelTokens.any { token ->
                    val lowerToken = token.lowercase()
                    lowerToken in lowerLabel || lowerToken in lowerPackage
                }
            }
        return launchableByLabel
    }

    private fun queryLauncherApps(): List<AppCandidate> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("QueryPermissionsNeeded")
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName }
                AppCandidate(label = label, packageName = packageName)
            }.distinctBy { it.packageName }
    }

    private fun isInstalledPackage(packageName: String): Boolean =
        runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess

    private fun hasLaunchIntent(packageName: String): Boolean =
        packageManager.getLaunchIntentForPackage(packageName) != null

    private data class AliasSpec(
        val key: String,
        val label: String,
        val preferredPackages: List<String>,
        val preferredLabelTokens: List<String>,
    )

    private companion object {
        val GENERIC_GOAL_TOKENS =
            setOf(
                "open",
                "launch",
                "enter",
                "verify",
                "check",
                "find",
                "search",
                "type",
                "with",
                "from",
                "into",
                "then",
                "the",
                "and",
                "app",
            )
    }
}
