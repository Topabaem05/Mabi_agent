package com.guribbong.phoneappagent.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guribbong.phoneappagent.BuildConfig
import com.guribbong.phoneappagent.accessibility.AccessibilityServiceHealth
import com.guribbong.phoneappagent.accessibility.AccessibilityStatusRepository
import com.guribbong.phoneappagent.agent.AgentOrchestrator
import com.guribbong.phoneappagent.agent.AgentRunPhase
import com.guribbong.phoneappagent.agent.AgentServiceController
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.core.runner.LocalAgentRuntime
import com.guribbong.phoneappagent.data.history.ChatSessionSummary
import com.guribbong.phoneappagent.data.history.SessionRepository
import com.guribbong.phoneappagent.di.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MainUiState(
    val status: String = "Ready",
    val sessions: List<ChatSessionSummary> = emptyList(),
    val modelName: String = BuildConfig.OPENROUTER_MODEL,
    val backend: String = "OpenRouter / idle",
    val overlayEnabled: Boolean = true,
    val composer: String = "",
    val accessibilityEnabled: Boolean = false,
    val accessibilityHealth: String = "Disconnected",
    val foregroundApp: String? = null,
    val lastExternalForegroundApp: String? = null,
    val topNodeLabel: String? = null,
    val nodeCount: Int = 0,
    val lastAccessibilityEvent: String = "idle",
    val planSummary: String = "Waiting for first goal",
    val planSteps: List<ExecutionStep> = emptyList(),
    val currentStepIndex: Int = -1,
    val phaseLabel: String = "Idle",
    val needsConfirmation: Boolean = false,
    val policyReason: String = "Planner is waiting for a goal.",
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canStop: Boolean = false,
    val canConfirm: Boolean = false,
)

class MainViewModel(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val accessibilityRepository: AccessibilityStatusRepository,
    private val orchestrator: AgentOrchestrator,
    private val runtime: LocalAgentRuntime,
    private val serviceController: AgentServiceController,
) : ViewModel() {
    private val composer = MutableStateFlow("")
    private val bootstrapMutex = Mutex()
    private var bootstrapped = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                accessibilityRepository.setOverlayEnabled(settings.overlayEnabled)
            }
        }
    }

    val uiState: StateFlow<MainUiState> =
        combine(
            combine(
                sessionRepository.observeSessions(),
                settingsRepository.settings,
                composer,
                accessibilityRepository.snapshot,
                orchestrator.state,
            ) { sessions, settings, draft, accessibility, orchestratorState ->
                SessionUiBundle(
                    sessions = sessions,
                    settings = settings,
                    draft = draft,
                    accessibility = accessibility,
                    orchestratorState = orchestratorState,
                )
            },
            runtime.state,
        ) { bundle, runtimeState ->
            MainUiState(
                status = resolveStatus(bundle.accessibility, bundle.orchestratorState.phase),
                sessions = bundle.sessions,
                modelName = bundle.settings.modelName,
                backend = runtimeState.profile?.backendLabel ?: runtimeState.detail,
                overlayEnabled = bundle.settings.overlayEnabled,
                composer = bundle.draft,
                accessibilityEnabled = bundle.accessibility.enabled,
                accessibilityHealth = resolveAccessibilityHealth(bundle.accessibility.serviceHealth),
                foregroundApp = bundle.accessibility.foregroundPackage,
                lastExternalForegroundApp = bundle.accessibility.lastExternalForegroundPackage,
                topNodeLabel = bundle.accessibility.topNodeLabel,
                nodeCount = bundle.accessibility.nodeCount,
                lastAccessibilityEvent = bundle.accessibility.lastEvent,
                planSummary = bundle.orchestratorState.currentPlan?.summary ?: "Queued goals will be planned on device.",
                planSteps = bundle.orchestratorState.currentPlan?.steps ?: defaultSteps(bundle.accessibility),
                currentStepIndex = bundle.orchestratorState.currentStepIndex,
                phaseLabel = bundle.orchestratorState.phase.name.lowercase().replaceFirstChar(Char::titlecase),
                needsConfirmation = bundle.orchestratorState.phase == AgentRunPhase.WAITING_FOR_CONFIRM,
                policyReason = bundle.orchestratorState.detail,
                canPause = bundle.orchestratorState.phase in setOf(
                    AgentRunPhase.PREPARING,
                    AgentRunPhase.PLANNING,
                    AgentRunPhase.EXECUTING,
                    AgentRunPhase.RECOVERING,
                ),
                canResume = bundle.orchestratorState.phase == AgentRunPhase.PAUSED,
                canStop = bundle.orchestratorState.phase !in setOf(
                    AgentRunPhase.IDLE,
                    AgentRunPhase.COMPLETED,
                    AgentRunPhase.FAILED,
                ),
                canConfirm = bundle.orchestratorState.phase == AgentRunPhase.WAITING_FOR_CONFIRM,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )

    fun onComposerChanged(value: String) {
        composer.value = value
    }

    fun applyIncomingPrompt(prompt: String) {
        val trimmed = normalizeIncomingPrompt(prompt)
        if (trimmed.isNotEmpty()) {
            composer.value = trimmed
        }
    }

    fun runIncomingPrompt(prompt: String) {
        val normalizedPrompt = normalizeIncomingPrompt(prompt)
        if (normalizedPrompt.isEmpty()) return
        composer.value = normalizedPrompt
        queueTask(normalizedPrompt)
    }

    fun queueTask() {
        queueTask(null)
    }

    private fun queueTask(promptOverride: String?) {
        val value = (promptOverride ?: composer.value).trim()
        if (value.isEmpty()) return
        composer.value = ""
        serviceController.startGoal(value)
    }

    fun toggleOverlay() {
        viewModelScope.launch {
            val enabled = !uiState.value.overlayEnabled
            settingsRepository.setOverlayEnabled(enabled)
            accessibilityRepository.setOverlayEnabled(enabled)
        }
    }

    suspend fun bootstrapIfNeeded() {
        if (bootstrapped) return
        bootstrapMutex.withLock {
            if (bootstrapped) return
            sessionRepository.ensureSeedSession()
            orchestrator.bootstrapPendingSession()
            bootstrapped = true
        }
    }

    fun refreshAccessibility() {
        accessibilityRepository.refreshState()
    }

    fun openAccessibilitySettings() {
        accessibilityRepository.openAccessibilitySettings()
    }

    fun pauseOrResume() {
        if (uiState.value.canResume) {
            serviceController.resume()
        } else if (uiState.value.canPause) {
            serviceController.pause()
        }
    }

    fun stopExecution() {
        serviceController.stop()
    }

    fun confirmExecution() {
        serviceController.confirm()
    }

    private fun resolveStatus(
        accessibility: com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot,
        phase: AgentRunPhase,
    ): String =
        when {
            !accessibility.enabled -> "Needs access"
            accessibility.serviceHealth == AccessibilityServiceHealth.RECONNECT_REQUIRED -> "Reconnect service"
            phase == AgentRunPhase.IDLE -> "Idle"
            else -> phase.name.lowercase().replaceFirstChar(Char::titlecase)
        }

    private fun resolveAccessibilityHealth(health: AccessibilityServiceHealth): String =
        when (health) {
            AccessibilityServiceHealth.CONNECTED -> "Connected"
            AccessibilityServiceHealth.DISCONNECTED -> "Disconnected"
            AccessibilityServiceHealth.RECONNECT_REQUIRED -> "Reconnect required"
        }

    private fun defaultSteps(accessibility: com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot): List<ExecutionStep> =
        listOf(
            ExecutionStep(
                action = AgentAction.WaitForCondition(
                    if (accessibility.enabled) {
                        "Describe a cross-app goal"
                    } else {
                        "Enable accessibility access"
                    },
                ),
                expectedObservation = "On-device planner will prepare the next safe action sequence.",
            ),
        )

    private fun normalizeIncomingPrompt(prompt: String): String =
        prompt
            .replace('_', ' ')
            .trim()
}

private data class SessionUiBundle(
    val sessions: List<ChatSessionSummary>,
    val settings: com.guribbong.phoneappagent.di.AgentSettings,
    val draft: String,
    val accessibility: com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot,
    val orchestratorState: com.guribbong.phoneappagent.agent.AgentOrchestratorState,
)
