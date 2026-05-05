package com.guribbong.phoneappagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.GlobalActionType
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.ScreenBounds
import com.guribbong.phoneappagent.core.dsl.ScrollDirection
import com.guribbong.phoneappagent.driver.accessibility.ActionExecutionResult
import com.guribbong.phoneappagent.driver.accessibility.UiNodeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val SERVICE_TAG = "PhoneAppAgentA11y"
private const val SETTINGS_PACKAGE = "com.android.settings"
private val SETTINGS_PACKAGE_ALIASES = setOf(
    "com.android.settings",
    "com.android.settings.intelligence",
    "com.google.android.settings.intelligence",
)
private val SETTINGS_SEARCH_FIELD_RESOURCE_IDS = setOf(
    "com.android.settings.intelligence:id/search_src_text",
    "com.google.android.settings.intelligence:id/open_search_view_edit_text",
)
private val SETTINGS_SEARCH_BUTTON_RESOURCE_IDS = setOf(
    "com.android.settings:id/search_action_bar",
)
private const val CHROME_PACKAGE = "com.android.chrome"
private const val CHROME_SEARCH_BOX_RESOURCE_ID = "com.android.chrome:id/search_box_text"
private const val CHROME_URL_BAR_RESOURCE_ID = "com.android.chrome:id/url_bar"
private val TRANSIENT_OVERLAY_PACKAGES = setOf("com.android.systemui")
private val SETTINGS_SEARCH_SELECTOR_TERMS = setOf("search settings", "settings search", "설정 검색", "검색")
private val SETTINGS_SEARCH_NODE_TERMS = setOf("search settings", "settings search", "설정 검색", "검색")

class AgentAccessibilityService : AccessibilityService(), AccessibilityActionPerformer {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayController: AccessibilityOverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(SERVICE_TAG, "onServiceConnected")
        overlayController = AccessibilityOverlayController(this)
        serviceScope.launch {
            AccessibilityOverlayBridge.state.collectLatest { state ->
                overlayController?.render(state.copy(packageName = state.packageName ?: rootInActiveWindow?.packageName?.toString()))
            }
        }
        AccessibilityBridge.markConnected()
        AccessibilityCommandBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        publishSnapshot(event)
    }

    override fun refreshSnapshot(): AccessibilitySnapshot? {
        publishSnapshot(null)
        return AccessibilityBridge.snapshot.value
    }

    private fun publishSnapshot(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow
        val nodes = mutableListOf<UiNodeSnapshot>()
        val eventPackage = event?.packageName?.toString()
        val rootPackage = rootNode?.packageName?.toString()
        val foregroundPackage =
            if (eventPackage in TRANSIENT_OVERLAY_PACKAGES && rootPackage !in TRANSIENT_OVERLAY_PACKAGES) {
                rootPackage ?: eventPackage
            } else {
                eventPackage ?: rootPackage
            }
        val lastExternalPackage = foregroundPackage
            ?.takeUnless { it == packageName || it in TRANSIENT_OVERLAY_PACKAGES }
            ?: AccessibilityBridge.snapshot.value.lastExternalForegroundPackage

        collectNodes(rootNode, nodes, limit = 256, indexPath = emptyList())

        val topLabel = nodes.firstNotNullOfOrNull { snapshot ->
            snapshot.text?.takeIf { it.isNotBlank() }
                ?: snapshot.contentDescription?.takeIf { it.isNotBlank() }
                ?: snapshot.resourceId?.takeIf { it.isNotBlank() }
        }

        AccessibilityBridge.update(
            foregroundPackage = foregroundPackage,
            lastExternalForegroundPackage = lastExternalPackage,
            topNodeLabel = topLabel,
            nodeCount = nodes.size,
            lastEvent = eventName(event?.eventType),
            visibleNodes = nodes,
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(SERVICE_TAG, "onUnbind")
        AccessibilityCommandBridge.detach(this)
        AccessibilityBridge.markDisconnected()
        overlayController?.release()
        overlayController = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(SERVICE_TAG, "onDestroy")
        AccessibilityCommandBridge.detach(this)
        AccessibilityBridge.markDisconnected()
        overlayController?.release()
        overlayController = null
        super.onDestroy()
    }

    override fun execute(action: AgentAction): ActionExecutionResult =
        when (action) {
            is AgentAction.Tap -> tap(action.selector, action.label)
            is AgentAction.InputText -> inputText(action.selector, action.text)
            is AgentAction.SubmitInput -> submitInput(action.selector)
            is AgentAction.ClearText -> clearText(action.selector)
            is AgentAction.Scroll -> scroll(action.selector, action.direction)
            is AgentAction.PressGlobal -> pressGlobal(action.action)
            is AgentAction.AssertVisible -> assertVisible(action.selector)
            is AgentAction.WaitForNode -> assertVisible(action.selector)
            is AgentAction.WaitForApp,
            is AgentAction.WaitForCondition,
            is AgentAction.LaunchApp,
            is AgentAction.ConfirmUser,
            AgentAction.Stop,
            -> ActionExecutionResult(success = true, detail = "No-op service action.")
        }

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        sink: MutableList<UiNodeSnapshot>,
        limit: Int,
        indexPath: List<Int>,
    ) {
        if (node == null || sink.size >= limit) return
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, List<Int>>>()
        queue += node to indexPath
        while (queue.isNotEmpty() && sink.size < limit) {
            val (currentNode, currentIndexPath) = queue.removeFirst()
            val bounds = Rect().also(currentNode::getBoundsInScreen)
            sink += UiNodeSnapshot(
                text = currentNode.text?.toString(),
                contentDescription = currentNode.contentDescription?.toString(),
                resourceId = currentNode.viewIdResourceName,
                className = currentNode.className?.toString(),
                packageName = currentNode.packageName?.toString(),
                editable = currentNode.isEditable,
                clickable = currentNode.isClickable,
                indexPath = currentIndexPath,
                bounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom).takeUnless { it.isEmpty() },
            )
            for (index in 0 until currentNode.childCount) {
                val child = currentNode.getChild(index) ?: continue
                queue += child to (currentIndexPath + index)
            }
        }
    }

    private fun eventName(eventType: Int?): String =
        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state_changed"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window_content_changed"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "view_clicked"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "view_text_changed"
            else -> "event_${eventType ?: 0}"
        }

    private fun tap(
        selector: NodeSelector,
        label: String,
    ): ActionExecutionResult {
        val node = findNode(selector)
            ?: return ActionExecutionResult(
                success = false,
                detail = "Tap target not found: ${selector.label()}",
                selectorFailureReason = "selector_not_found",
                observedPackage = rootInActiveWindow?.packageName?.toString(),
            )
        val target = clickableAncestor(node) ?: node
        val bounds = boundsOf(target)
        AccessibilityOverlayBridge.show(
            statusLabel = "Executing tap",
            packageName = rootInActiveWindow?.packageName?.toString(),
            stepLabel = label.ifBlank { selector.label() },
            targetBounds = bounds,
        )
        val success =
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                (target.isFocusable && target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) ||
                dispatchTap(target)
        return ActionExecutionResult(
            success = success,
            detail = if (success) "Tap executed." else "Tap gesture failed.",
            observedPackage = rootInActiveWindow?.packageName?.toString(),
            targetBounds = bounds,
        )
    }

    private fun inputText(
        selector: NodeSelector,
        text: String,
    ): ActionExecutionResult {
        val node = resolveEditableNode(selector)
            ?: return ActionExecutionResult(
                success = false,
                detail = "Editable field not found.",
                selectorFailureReason = "editable_not_found",
                observedPackage = rootInActiveWindow?.packageName?.toString(),
            )
        val bounds = boundsOf(node)
        AccessibilityOverlayBridge.show(
            statusLabel = "Typing",
            packageName = rootInActiveWindow?.packageName?.toString(),
            stepLabel = selector.label(),
            targetBounds = bounds,
        )
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return ActionExecutionResult(
            success = success,
            detail = if (success) "Text input sent." else "Text input failed.",
            observedPackage = rootInActiveWindow?.packageName?.toString(),
            targetBounds = bounds,
        )
    }

    private fun clearText(selector: NodeSelector): ActionExecutionResult =
        inputText(selector, "")

    private fun submitInput(selector: NodeSelector): ActionExecutionResult {
        val initialNode = resolveEditableNode(selector)
            ?: return ActionExecutionResult(
                success = false,
                detail = "Editable field not found for submit.",
                selectorFailureReason = "editable_not_found",
                observedPackage = rootInActiveWindow?.packageName?.toString(),
            )
        val bounds = boundsOf(initialNode)
        AccessibilityOverlayBridge.show(
            statusLabel = "Submitting",
            packageName = rootInActiveWindow?.packageName?.toString(),
            stepLabel = selector.label(),
            targetBounds = bounds,
        )
        val imeEnterActionId = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        val supportedActionIds = initialNode.actionList.map { it.id }
        Log.d(
            SERVICE_TAG,
            "submitInput selector=${selector.label()} supportedActions=$supportedActionIds",
        )
        if (imeEnterActionId !in supportedActionIds) {
            fallbackChromeSearchSubmit(initialNode, selector)?.let { return it }
            return ActionExecutionResult(
                success = false,
                detail = "IME submit action is not supported by the target field.",
                selectorFailureReason = "ime_enter_not_supported",
                observedPackage = rootInActiveWindow?.packageName?.toString(),
                targetBounds = bounds,
            )
        }
        if (!initialNode.isFocused) {
            val focusTarget = clickableAncestor(initialNode) ?: initialNode
            focusTarget.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            focusTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val submitNode = editableAncestor(findFocusedEditableNode() ?: findNode(selector)) ?: initialNode
        val success = submitNode.performAction(imeEnterActionId)
        if (!success) {
            fallbackChromeSearchSubmit(submitNode, selector)?.let { return it }
        }
        return ActionExecutionResult(
            success = success,
            detail = if (success) {
                "IME submit action sent."
            } else {
                "IME submit action failed. focused=${submitNode.isFocused}"
            },
            observedPackage = rootInActiveWindow?.packageName?.toString(),
            targetBounds = boundsOf(submitNode),
        )
    }

    private fun fallbackChromeSearchSubmit(
        node: AccessibilityNodeInfo,
        selector: NodeSelector,
    ): ActionExecutionResult? {
        val resourceId = selector.resourceId ?: node.viewIdResourceName
        if (
            rootInActiveWindow?.packageName?.toString() != CHROME_PACKAGE ||
            resourceId !in setOf(CHROME_URL_BAR_RESOURCE_ID, CHROME_SEARCH_BOX_RESOURCE_ID)
        ) {
            return null
        }
        val query = node.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) return null
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encodedQuery")).apply {
            `package` = CHROME_PACKAGE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            startActivity(intent)
            ActionExecutionResult(
                success = true,
                detail = "Chrome search URL opened for '$query'.",
                observedPackage = CHROME_PACKAGE,
                targetBounds = boundsOf(node),
            )
        }.getOrNull()
    }

    private fun resolveEditableNode(selector: NodeSelector): AccessibilityNodeInfo? {
        val directMatch = findNode(selector)
        if (
            selector.resourceId == CHROME_SEARCH_BOX_RESOURCE_ID &&
            rootInActiveWindow?.packageName?.toString() == CHROME_PACKAGE
        ) {
            val seedNode = directMatch ?: findFocusedEditableNode()
            val activationTarget = seedNode?.let(::clickableAncestor) ?: seedNode
            activationTarget?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            activationTarget?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val urlBarSelector = NodeSelector(
                resourceId = CHROME_URL_BAR_RESOURCE_ID,
                className = "android.widget.EditText",
                packageName = CHROME_PACKAGE,
            )
            return editableAncestor(findNode(urlBarSelector) ?: findFocusedEditableNode() ?: directMatch)
        }
        return editableAncestor(directMatch ?: findFocusedEditableNode())
    }

    private fun scroll(
        selector: NodeSelector?,
        direction: ScrollDirection,
    ): ActionExecutionResult {
        val node = selector?.let(::findNode) ?: findScrollableNode(rootInActiveWindow)
            ?: return ActionExecutionResult(
                success = false,
                detail = "Scrollable container not found.",
                selectorFailureReason = "scrollable_not_found",
                observedPackage = rootInActiveWindow?.packageName?.toString(),
            )
        val actionId =
            when (direction) {
                ScrollDirection.BACKWARD, ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                ScrollDirection.FORWARD, ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
        val success = node.performAction(actionId)
        return ActionExecutionResult(
            success = success,
            detail = if (success) "Scroll action sent." else "Scroll action failed.",
            observedPackage = rootInActiveWindow?.packageName?.toString(),
            targetBounds = boundsOf(node),
        )
    }

    private fun pressGlobal(action: GlobalActionType): ActionExecutionResult {
        val globalAction =
            when (action) {
                GlobalActionType.BACK -> GLOBAL_ACTION_BACK
                GlobalActionType.HOME -> GLOBAL_ACTION_HOME
                GlobalActionType.RECENTS -> GLOBAL_ACTION_RECENTS
                GlobalActionType.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                GlobalActionType.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            }
        val success = performGlobalAction(globalAction)
        return ActionExecutionResult(
            success = success,
            detail = if (success) "Global action executed." else "Global action failed.",
            observedPackage = rootInActiveWindow?.packageName?.toString(),
        )
    }

    private fun assertVisible(selector: NodeSelector): ActionExecutionResult {
        val node = findNode(selector)
            ?: return ActionExecutionResult(
                success = false,
                detail = "Selector not visible: ${selector.label()}",
                selectorFailureReason = "selector_not_visible",
                observedPackage = rootInActiveWindow?.packageName?.toString(),
            )
        val bounds = boundsOf(node)
        AccessibilityOverlayBridge.show(
            statusLabel = "Inspecting target",
            packageName = rootInActiveWindow?.packageName?.toString(),
            stepLabel = selector.label(),
            targetBounds = bounds,
        )
        return ActionExecutionResult(
            success = true,
            detail = "Selector visible.",
            observedPackage = rootInActiveWindow?.packageName?.toString(),
            targetBounds = bounds,
        )
    }

    private fun findNode(
        selector: NodeSelector,
        searchRoot: AccessibilityNodeInfo? = rootInActiveWindow,
    ): AccessibilityNodeInfo? {
        val root = searchRoot ?: return null
        if (selector.indexPath.isNotEmpty()) {
            traverseIndexPath(root, selector.indexPath)?.let { indexed ->
                if (scoreNode(indexed, selector) > Int.MIN_VALUE) {
                    return indexed
                }
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var bestMatch: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE
        var traversed = 0
        while (queue.isNotEmpty() && traversed < 300) {
            val node = queue.removeFirst()
            traversed += 1
            val score = scoreNode(node, selector)
            if (score > bestScore) {
                bestScore = score
                bestMatch = node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return bestMatch?.takeIf { bestScore > 0 }
    }

    private fun traverseIndexPath(
        root: AccessibilityNodeInfo,
        indexPath: List<Int>,
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = root
        for (index in indexPath) {
            current = current?.getChild(index) ?: return null
        }
        return current
    }

    private fun scoreNode(
        node: AccessibilityNodeInfo,
        selector: NodeSelector,
    ): Int {
        if (matchesSettingsSearchSelector(selector, node)) {
            var score = 180
            if (node.isClickable) score += 8
            return score
        }
        if (selector.editable != null && selector.editable != node.isEditable) return Int.MIN_VALUE
        if (selector.clickable != null && selector.clickable != node.isClickable) return Int.MIN_VALUE
        if (selector.packageName != null && !matchesPackageName(selector.packageName, node.packageName?.toString())) {
            return Int.MIN_VALUE
        }
        if (selector.className != null && !matches(selector.className, node.className?.toString())) return Int.MIN_VALUE
        val expectedResourceId = selector.resourceId
        if (expectedResourceId != null && !matchesResourceId(expectedResourceId, node.viewIdResourceName, node.packageName?.toString())) {
            return Int.MIN_VALUE
        }

        var score = 0
        selector.packageName?.let {
            score += 20
        }
        selector.className?.let {
            score += 25
        }
        selector.resourceId?.let {
            score += 110
        }
        selector.text?.let { text ->
            if (!matches(text, node.text?.toString())) return Int.MIN_VALUE
            score += 100
        }
        selector.contentDescription?.let { contentDescription ->
            if (!matches(contentDescription, node.contentDescription?.toString())) return Int.MIN_VALUE
            score += 100
        }
        selector.nearText?.let { nearText ->
            if (!hasNearText(node, nearText)) return Int.MIN_VALUE
            score += 45
        }
        selector.boundsHint?.let { hint ->
            val bounds = boundsOf(node) ?: return Int.MIN_VALUE
            if (hint != bounds) return Int.MIN_VALUE
            score += 30
        }
        if (selector.text == null && selector.contentDescription == null && selector.resourceId == null && selector.className == null) {
            score += 10
        }
        if (node.isClickable) score += 8
        if (node.isEditable) score += 8
        return score
    }

    private fun matchesSettingsSearchSelector(
        selector: NodeSelector,
        node: AccessibilityNodeInfo,
    ): Boolean {
        val packageName = node.packageName?.toString()?.trim()?.lowercase()
        if (packageName !in SETTINGS_PACKAGE_ALIASES) return false
        val expectedText = selector.text?.trim()?.lowercase()
        val expectedDescription = selector.contentDescription?.trim()?.lowercase()
        val expectedResourceId = selector.resourceId?.trim()?.lowercase()
        val expectsSearch =
            expectedText in SETTINGS_SEARCH_SELECTOR_TERMS ||
                expectedDescription in SETTINGS_SEARCH_SELECTOR_TERMS ||
                expectedResourceId in SETTINGS_SEARCH_BUTTON_RESOURCE_IDS
        if (!expectsSearch) return false
        val actualText = node.text?.toString()?.trim()?.lowercase()
        val actualDescription = node.contentDescription?.toString()?.trim()?.lowercase()
        return actualText in SETTINGS_SEARCH_NODE_TERMS || actualDescription in SETTINGS_SEARCH_NODE_TERMS
    }

    private fun hasNearText(
        node: AccessibilityNodeInfo,
        nearText: String,
    ): Boolean {
        val parent = node.parent ?: return false
        for (index in 0 until parent.childCount) {
            val sibling = parent.getChild(index) ?: continue
            if (matches(nearText, sibling.text?.toString()) || matches(nearText, sibling.contentDescription?.toString())) {
                return true
            }
        }
        return false
    }

    private fun matches(
        expected: String?,
        actual: String?,
    ): Boolean {
        if (expected.isNullOrBlank() || actual.isNullOrBlank()) return false
        val left = expected.trim().lowercase()
        val right = actual.trim().lowercase()
        return left == right || left in right || right in left
    }

    private fun matchesPackageName(
        expected: String?,
        actual: String?,
    ): Boolean {
        if (expected.isNullOrBlank() || actual.isNullOrBlank()) return false
        val expectedLower = expected.trim().lowercase()
        val actualLower = actual.trim().lowercase()
        if (expectedLower == actualLower) return true
        return expectedLower in SETTINGS_PACKAGE_ALIASES && actualLower in SETTINGS_PACKAGE_ALIASES
    }

    private fun matchesResourceId(
        expected: String,
        actual: String?,
        packageName: String?,
    ): Boolean {
        if (matches(expected, actual)) return true
        val packageLower = packageName?.trim()?.lowercase()
        val expectedLower = expected.trim().lowercase()
        val actualLower = actual?.trim()?.lowercase() ?: return false
        return packageLower in SETTINGS_PACKAGE_ALIASES &&
            expectedLower in SETTINGS_SEARCH_FIELD_RESOURCE_IDS &&
            actualLower in SETTINGS_SEARCH_FIELD_RESOURCE_IDS
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun editableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isEditable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return editableAncestor(it) ?: it }
        return findScrollableNode(root) ?: root
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val root = node ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var traversed = 0
        while (queue.isNotEmpty() && traversed < 200) {
            val current = queue.removeFirst()
            traversed += 1
            if (current.actionList.any { action ->
                    action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                        action.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }) {
                return current
            }
            for (index in 0 until current.childCount) {
                current.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun dispatchTap(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        val latch = CountDownLatch(1)
        var completed = false
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            },
            null,
        )
        if (!dispatched) return false
        latch.await(1, TimeUnit.SECONDS)
        return completed
    }

    private fun boundsOf(node: AccessibilityNodeInfo?): ScreenBounds? {
        val target = node ?: return null
        val rect = Rect().also(target::getBoundsInScreen)
        return ScreenBounds(rect.left, rect.top, rect.right, rect.bottom).takeUnless { it.isEmpty() }
    }
}
