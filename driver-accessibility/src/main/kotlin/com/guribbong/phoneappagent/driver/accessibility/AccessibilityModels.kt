package com.guribbong.phoneappagent.driver.accessibility

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.ScreenBounds

data class UiNodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val packageName: String?,
    val editable: Boolean,
    val clickable: Boolean,
    val indexPath: List<Int>,
    val bounds: ScreenBounds?,
)

data class ActionExecutionResult(
    val success: Boolean,
    val detail: String,
    val observedPackage: String? = null,
    val selectorFailureReason: String? = null,
    val targetBounds: ScreenBounds? = null,
)

interface AccessibilityDriver {
    suspend fun observeForeground(): String
    suspend fun execute(action: AgentAction): ActionExecutionResult
}
