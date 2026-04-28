package com.guribbong.phoneappagent.accessibility

import android.util.Log
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.ScreenBounds
import com.guribbong.phoneappagent.driver.accessibility.ActionExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val BRIDGE_TAG = "PhoneAppAgentBridge"

internal interface AccessibilityActionPerformer {
    fun execute(action: AgentAction): ActionExecutionResult
    fun refreshSnapshot(): AccessibilitySnapshot?
}

internal data class OverlayHudState(
    val enabled: Boolean = true,
    val active: Boolean = false,
    val statusLabel: String = "Idle",
    val packageName: String? = null,
    val stepLabel: String? = null,
    val targetBounds: ScreenBounds? = null,
)

internal object AccessibilityOverlayBridge {
    private val _state = MutableStateFlow(OverlayHudState())
    val state: StateFlow<OverlayHudState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun show(
        statusLabel: String,
        packageName: String?,
        stepLabel: String?,
        targetBounds: ScreenBounds? = null,
    ) {
        _state.value = _state.value.copy(
            active = true,
            statusLabel = statusLabel,
            packageName = packageName,
            stepLabel = stepLabel,
            targetBounds = targetBounds,
        )
    }

    fun clearTarget() {
        _state.value = _state.value.copy(targetBounds = null)
    }

    fun hide() {
        _state.value = _state.value.copy(
            active = false,
            statusLabel = "Idle",
            stepLabel = null,
            targetBounds = null,
        )
    }
}

internal object AccessibilityCommandBridge {
    @Volatile
    private var performer: AccessibilityActionPerformer? = null

    fun attach(performer: AccessibilityActionPerformer) {
        Log.d(BRIDGE_TAG, "attach performer=${performer::class.simpleName}")
        this.performer = performer
    }

    fun detach(performer: AccessibilityActionPerformer) {
        if (this.performer === performer) {
            Log.d(BRIDGE_TAG, "detach performer=${performer::class.simpleName}")
            this.performer = null
        }
    }

    fun execute(action: AgentAction): ActionExecutionResult {
        val current = performer
        Log.d(BRIDGE_TAG, "execute action=${action::class.simpleName} performerPresent=${current != null}")
        return current?.execute(action) ?: ActionExecutionResult(
            success = false,
            detail = "Accessibility performer unavailable.",
        )
    }

    fun refreshSnapshot(): AccessibilitySnapshot? = performer?.refreshSnapshot()
}
