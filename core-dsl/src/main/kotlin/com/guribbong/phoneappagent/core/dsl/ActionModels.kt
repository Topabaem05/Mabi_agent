package com.guribbong.phoneappagent.core.dsl

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun isEmpty(): Boolean = left >= right || top >= bottom
}

data class NodeSelector(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val editable: Boolean? = null,
    val clickable: Boolean? = null,
    val packageName: String? = null,
    val indexPath: List<Int> = emptyList(),
    val boundsHint: ScreenBounds? = null,
    val nearText: String? = null,
) {
    fun label(): String =
        listOf(
            text,
            contentDescription,
            nearText,
            resourceId,
            className,
            packageName,
        ).firstOrNull { !it.isNullOrBlank() } ?: "selector"

    fun historyKey(): String =
        buildString {
            append(text?.let { "text=$it" } ?: "")
            append(contentDescription?.let { "|desc=$it" } ?: "")
            append(resourceId?.let { "|id=$it" } ?: "")
            append(className?.let { "|class=$it" } ?: "")
            append(editable?.let { "|editable=$it" } ?: "")
            append(clickable?.let { "|clickable=$it" } ?: "")
            append(packageName?.let { "|package=$it" } ?: "")
            if (indexPath.isNotEmpty()) {
                append("|index=").append(indexPath.joinToString("."))
            }
            boundsHint?.let { bounds ->
                append("|bounds=")
                    .append(bounds.left)
                    .append(',')
                    .append(bounds.top)
                    .append(',')
                    .append(bounds.right)
                    .append(',')
                    .append(bounds.bottom)
            }
            append(nearText?.let { "|near=$it" } ?: "")
        }.trimStart('|')
}

enum class ScrollDirection {
    FORWARD,
    BACKWARD,
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

enum class GlobalActionType {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
}

sealed interface AgentAction {
    data class LaunchApp(val packageName: String) : AgentAction

    data class WaitForApp(
        val packageName: String,
        val timeoutMs: Long = 5_000L,
    ) : AgentAction

    data class WaitForNode(
        val selector: NodeSelector,
        val timeoutMs: Long = 5_000L,
    ) : AgentAction

    data class Tap(
        val selector: NodeSelector,
        val label: String = selector.label(),
    ) : AgentAction

    data class InputText(
        val selector: NodeSelector,
        val text: String,
    ) : AgentAction

    data class SubmitInput(val selector: NodeSelector) : AgentAction

    data class ClearText(val selector: NodeSelector) : AgentAction

    data class Scroll(
        val selector: NodeSelector? = null,
        val direction: ScrollDirection = ScrollDirection.DOWN,
    ) : AgentAction

    data class PressGlobal(val action: GlobalActionType) : AgentAction
    data class AssertVisible(val selector: NodeSelector) : AgentAction
    data class WaitForCondition(val condition: String) : AgentAction
    data class ConfirmUser(val reason: String) : AgentAction
    data object Stop : AgentAction
}

fun AgentAction.historyKey(): String =
    when (this) {
        is AgentAction.LaunchApp -> "launch_app:$packageName"
        is AgentAction.WaitForApp -> "wait_for_app:$packageName"
        is AgentAction.WaitForNode -> "wait_for_node:${selector.historyKey()}"
        is AgentAction.Tap -> "tap:${selector.historyKey()}"
        is AgentAction.InputText -> "input_text:${selector.historyKey()}|text=$text"
        is AgentAction.SubmitInput -> "submit_input:${selector.historyKey()}"
        is AgentAction.ClearText -> "clear_text:${selector.historyKey()}"
        is AgentAction.Scroll -> "scroll:${selector?.historyKey().orEmpty()}|direction=${direction.name}"
        is AgentAction.PressGlobal -> "press_global:${action.name}"
        is AgentAction.AssertVisible -> "assert_visible:${selector.historyKey()}"
        is AgentAction.WaitForCondition -> "wait_for_condition:$condition"
        is AgentAction.ConfirmUser -> "confirm_user:${reason.trim()}"
        AgentAction.Stop -> "stop"
    }
