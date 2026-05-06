package com.guribbong.phoneappagent.execution

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.guribbong.phoneappagent.accessibility.AccessibilityBridge
import com.guribbong.phoneappagent.accessibility.AccessibilityCommandBridge
import com.guribbong.phoneappagent.agent.AgentPowerController
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.ScreenBounds
import com.guribbong.phoneappagent.driver.accessibility.AccessibilityDriver
import com.guribbong.phoneappagent.driver.accessibility.ActionExecutionResult
import kotlinx.coroutines.delay

private const val EXECUTOR_TAG = "PhoneAppAgentExec"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val SETTINGS_INTELLIGENCE_PACKAGE = "com.android.settings.intelligence"
private val SETTINGS_SEARCH_FIELD_RESOURCE_IDS = setOf(
    "com.android.settings.intelligence:id/search_src_text",
    "com.google.android.settings.intelligence:id/open_search_view_edit_text",
)
private val SETTINGS_SEARCH_BUTTON_RESOURCE_IDS = setOf(
    "com.android.settings:id/search_action_bar",
)
private const val SAMSUNG_MESSAGES_PACKAGE = "com.samsung.android.messaging"
private const val SAMSUNG_MESSAGES_FAB_ID = "com.samsung.android.messaging:id/fab"
private const val SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID = "com.samsung.android.messaging:id/search_src_text"
private const val SAMSUNG_MESSAGES_MESSAGE_EDIT_TEXT_ID = "com.samsung.android.messaging:id/message_edit_text"
private val TRANSIENT_OVERLAY_PACKAGES = setOf("com.android.systemui")
private val SETTINGS_SEARCH_SELECTOR_TERMS = setOf("search settings", "settings search", "설정 검색", "검색")
private val SETTINGS_SEARCH_NODE_TERMS = setOf("search settings", "settings search", "설정 검색", "검색")
private val SETTINGS_HOMEPAGE = ComponentName(
    SETTINGS_PACKAGE,
    "com.android.settings.homepage.SettingsHomepageActivity",
)

class CanonicalPackageResolver {
    private val aliases = mapOf(
        "com.android.settings" to setOf(
            "com.android.settings",
            "com.android.settings.intelligence",
            "com.google.android.settings.intelligence",
        ),
    )

    fun canonical(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return aliases.entries.firstOrNull { (_, variants) -> packageName in variants }?.key ?: packageName
    }

    fun matches(
        expected: String,
        observed: String?,
    ): Boolean = canonical(expected) == canonical(observed)
}

class AndroidAccessibilityDriver(
    context: Context,
    private val packageResolver: CanonicalPackageResolver,
    private val powerController: AgentPowerController,
) : AccessibilityDriver {
    private val appContext = context.applicationContext
    private var lastLaunchPackage: String? = null
    private var lastLaunchAtMs: Long = 0L

    override suspend fun observeForeground(): String =
        AccessibilityBridge.snapshot.value.foregroundPackage.orEmpty()

    override suspend fun execute(action: AgentAction): ActionExecutionResult {
        Log.d(EXECUTOR_TAG, "execute action=${action::class.simpleName} payload=$action")
        return when (action) {
            is AgentAction.LaunchApp -> launchApp(action.packageName)
            is AgentAction.OpenUri -> openUri(action.uri, action.packageName)
            is AgentAction.WaitForApp -> waitForApp(action.packageName, action.timeoutMs)
            is AgentAction.WaitForNode -> waitForNode(action.selector, action.timeoutMs)
            is AgentAction.WaitForCondition,
            is AgentAction.ConfirmUser,
            AgentAction.Stop,
            -> ActionExecutionResult(success = true, detail = "No-op action.")

            else -> executeWithRetry(action)
        }
    }

    private fun launchApp(packageName: String): ActionExecutionResult {
        val launchIntent =
            when (packageName) {
                SETTINGS_PACKAGE -> Intent().apply {
                    component = SETTINGS_HOMEPAGE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                else -> resolveLauncherIntent(packageName)
            } ?: return ActionExecutionResult(
                success = false,
                detail = "No launch intent for $packageName",
            )

        return try {
            appContext.startActivity(launchIntent)
            lastLaunchPackage = packageName
            lastLaunchAtMs = System.currentTimeMillis()
            ActionExecutionResult(
                success = true,
                detail = "Launch intent sent.",
                observedPackage = packageName,
            )
        } catch (_: ActivityNotFoundException) {
            val fallbackIntent = resolveLauncherIntent(packageName)
                ?: return ActionExecutionResult(success = false, detail = "No launch intent for $packageName")
            appContext.startActivity(fallbackIntent)
            lastLaunchPackage = packageName
            lastLaunchAtMs = System.currentTimeMillis()
            ActionExecutionResult(
                success = true,
                detail = "Fallback launch intent sent.",
                observedPackage = packageName,
            )
        }
    }

    private fun openUri(
        uri: String,
        packageName: String?,
    ): ActionExecutionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            packageName?.takeIf { it.isNotBlank() }?.let(::setPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(intent)
            packageName?.let {
                lastLaunchPackage = it
                lastLaunchAtMs = System.currentTimeMillis()
            }
            ActionExecutionResult(
                success = true,
                detail = "URI intent sent.",
                observedPackage = packageName,
            )
        } catch (_: ActivityNotFoundException) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                appContext.startActivity(fallbackIntent)
                ActionExecutionResult(
                    success = true,
                    detail = "Fallback URI intent sent.",
                    observedPackage = packageName,
                )
            } catch (_: ActivityNotFoundException) {
                ActionExecutionResult(
                    success = false,
                    detail = "No activity can open $uri",
                    observedPackage = packageName,
                )
            }
        }
    }

    private suspend fun waitForApp(
        packageName: String,
        timeoutMs: Long,
    ): ActionExecutionResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snapshot = AccessibilityCommandBridge.refreshSnapshot() ?: AccessibilityBridge.snapshot.value
            val observedPackage = snapshot.foregroundPackage
            if (packageResolver.matches(packageName, observedPackage)) {
                return ActionExecutionResult(
                    success = true,
                    detail = "Foreground package matched.",
                    observedPackage = observedPackage,
                )
            }
            if (
                observedPackage in TRANSIENT_OVERLAY_PACKAGES &&
                packageResolver.matches(packageName, snapshot.lastExternalForegroundPackage)
            ) {
                return ActionExecutionResult(
                    success = true,
                    detail = "Foreground app matched behind transient overlay.",
                    observedPackage = snapshot.lastExternalForegroundPackage,
                )
            }
            val visibleTargetPackage = snapshot.visibleNodes
                .firstOrNull { node -> packageResolver.matches(packageName, node.packageName) }
                ?.packageName
            if (visibleTargetPackage != null) {
                return ActionExecutionResult(
                    success = true,
                    detail = "Target app nodes are visible.",
                    observedPackage = visibleTargetPackage,
                )
            }
            if (
                observedPackage.isNullOrBlank() &&
                packageResolver.matches(packageName, lastLaunchPackage) &&
                System.currentTimeMillis() - lastLaunchAtMs in 250L..2_500L
            ) {
                delay(500L)
                return ActionExecutionResult(
                    success = true,
                    detail = "Foreground package inferred from recent launch intent while accessibility foreground was empty.",
                    observedPackage = packageName,
                )
            }
            delay(150L)
        }
        return ActionExecutionResult(
            success = false,
            detail = powerController.runtimeBlocker(AccessibilityBridge.snapshot.value.foregroundPackage)
                ?: "Timed out waiting for $packageName",
            observedPackage = AccessibilityBridge.snapshot.value.foregroundPackage,
        )
    }

    private suspend fun waitForNode(
        selector: NodeSelector,
        timeoutMs: Long,
    ): ActionExecutionResult {
        waitForVisibleNode(selector, timeoutMs)?.let { matched ->
            return visibleNodeResult(
                detail = "Selector became visible.",
                matched = matched,
            )
        }
        attemptMessagesInboxRecovery(selector)?.let { return it }
        return ActionExecutionResult(
            success = false,
            detail = "Timed out waiting for selector ${selector.label()}",
            observedPackage = AccessibilityBridge.snapshot.value.foregroundPackage,
        )
    }

    private suspend fun waitForVisibleNode(
        selector: NodeSelector,
        timeoutMs: Long,
    ) = run {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snapshot = AccessibilityCommandBridge.refreshSnapshot() ?: AccessibilityBridge.snapshot.value
            val matched = snapshot.visibleNodes.firstOrNull { node ->
                selectorMatches(
                    selector,
                    node.text,
                    node.contentDescription,
                    node.resourceId,
                    node.className,
                    node.packageName,
                    node.editable,
                    node.clickable,
                    node.bounds,
                )
            }
            if (matched != null) {
                return@run matched
            }
            delay(150L)
        }
        null
    }

    private fun visibleNodeResult(
        detail: String,
        matched: com.guribbong.phoneappagent.driver.accessibility.UiNodeSnapshot,
    ) = ActionExecutionResult(
        success = true,
        detail = detail,
        observedPackage = matched.packageName,
        targetBounds = matched.bounds,
    )

    private suspend fun attemptMessagesInboxRecovery(selector: NodeSelector): ActionExecutionResult? {
        if (selector.resourceId != SAMSUNG_MESSAGES_FAB_ID) return null
        if (!packageResolver.matches(SAMSUNG_MESSAGES_PACKAGE, selector.packageName)) return null

        val snapshot = AccessibilityBridge.snapshot.value
        if (!packageResolver.matches(SAMSUNG_MESSAGES_PACKAGE, snapshot.foregroundPackage)) return null

        val openedMidFlow =
            snapshot.visibleNodes.any { node ->
                packageResolver.matches(SAMSUNG_MESSAGES_PACKAGE, node.packageName) &&
                    node.resourceId in setOf(
                        SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID,
                        SAMSUNG_MESSAGES_MESSAGE_EDIT_TEXT_ID,
                    )
            }
        if (!openedMidFlow) return null

        Log.d(
            EXECUTOR_TAG,
            "waitForNode recovery: Samsung Messages resumed mid-flow; backing out to inbox before retry",
        )
        val backResult = executeWithRetry(AgentAction.PressGlobal(com.guribbong.phoneappagent.core.dsl.GlobalActionType.BACK))
        if (!backResult.success) {
            return backResult.copy(detail = "Messages inbox recovery failed: ${backResult.detail}")
        }
        delay(500L)
        waitForVisibleNode(selector, 3_000L)?.let { matched ->
            return visibleNodeResult(
                detail = "Selector became visible after returning to the inbox.",
                matched = matched,
            )
        }
        return null
    }

    private fun resolveLauncherIntent(packageName: String): Intent? {
        val packageManager = appContext.packageManager
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            return launchIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }

        val launcherQuery = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
        }
        val launcherActivity = packageManager.queryIntentActivities(launcherQuery, 0)
            .firstOrNull()
            ?.activityInfo
            ?: return null

        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(launcherActivity.packageName, launcherActivity.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private suspend fun executeWithRetry(action: AgentAction): ActionExecutionResult {
        var lastResult: ActionExecutionResult? = null
        repeat(5) { attempt ->
            val result = AccessibilityCommandBridge.execute(action)
            lastResult = result
            if (result.success) {
                Log.d(
                    EXECUTOR_TAG,
                    "executeWithRetry success action=${action::class.simpleName} attempt=${attempt + 1}",
                )
                return result
            }
            delay(250L)
        }
        return lastResult ?: ActionExecutionResult(
            success = false,
            detail = "Accessibility service could not execute ${action::class.simpleName}",
            observedPackage = AccessibilityBridge.snapshot.value.foregroundPackage,
        )
    }

    private fun selectorMatches(
        selector: NodeSelector,
        text: String?,
        contentDescription: String?,
        resourceId: String?,
        className: String?,
        packageName: String?,
        editable: Boolean,
        clickable: Boolean,
        bounds: ScreenBounds?,
    ): Boolean {
        if (matchesSettingsSearchSelector(selector, text, contentDescription, packageName)) {
            return true
        }

        fun String?.matches(target: String?): Boolean {
            if (this.isNullOrBlank() || target.isNullOrBlank()) return false
            val left = this.trim().lowercase()
            val right = target.trim().lowercase()
            return left == right || left in right || right in left
        }

        if (selector.text != null && !selector.text.matches(text)) return false
        if (selector.contentDescription != null && !selector.contentDescription.matches(contentDescription)) return false
        val expectedResourceId = selector.resourceId
        if (expectedResourceId != null && !resourceIdMatches(expectedResourceId, resourceId, packageName)) return false
        if (selector.className != null && !selector.className.matches(className)) return false
        val expectedPackageName = selector.packageName
        if (expectedPackageName != null && !packageResolver.matches(expectedPackageName, packageName)) return false
        if (selector.editable != null && selector.editable != editable) return false
        if (selector.clickable != null && selector.clickable != clickable) return false
        if (selector.boundsHint != null && selector.boundsHint != bounds) return false
        return true
    }

    private fun resourceIdMatches(
        expected: String,
        actual: String?,
        packageName: String?,
    ): Boolean {
        val expectedLower = expected.trim().lowercase()
        val actualLower = actual?.trim()?.lowercase() ?: return false
        if (expectedLower == actualLower || expectedLower in actualLower || actualLower in expectedLower) {
            return true
        }
        val canonicalPackage = packageResolver.canonical(packageName)
        return canonicalPackage == SETTINGS_PACKAGE &&
            expectedLower in SETTINGS_SEARCH_FIELD_RESOURCE_IDS &&
            actualLower in SETTINGS_SEARCH_FIELD_RESOURCE_IDS
    }

    private fun matchesSettingsSearchSelector(
        selector: NodeSelector,
        text: String?,
        contentDescription: String?,
        packageName: String?,
    ): Boolean {
        if (packageResolver.canonical(packageName) != SETTINGS_PACKAGE) return false
        val expectedText = selector.text?.trim()?.lowercase()
        val expectedContentDescription = selector.contentDescription?.trim()?.lowercase()
        val expectedResourceId = selector.resourceId?.trim()?.lowercase()
        val expectsSearch =
            expectedText in SETTINGS_SEARCH_SELECTOR_TERMS ||
                expectedContentDescription in SETTINGS_SEARCH_SELECTOR_TERMS ||
                expectedResourceId in SETTINGS_SEARCH_BUTTON_RESOURCE_IDS
        if (!expectsSearch) return false
        val actualText = text?.trim()?.lowercase()
        val actualDescription = contentDescription?.trim()?.lowercase()
        return actualText in SETTINGS_SEARCH_NODE_TERMS || actualDescription in SETTINGS_SEARCH_NODE_TERMS
    }
}

class PlanExecutor(
    private val accessibilityDriver: AccessibilityDriver,
) {
    suspend fun executeStep(action: AgentAction): ActionExecutionResult =
        accessibilityDriver.execute(action)
}
