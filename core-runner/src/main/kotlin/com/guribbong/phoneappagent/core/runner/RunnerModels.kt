package com.guribbong.phoneappagent.core.runner

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.policy.RiskLevel
import kotlinx.coroutines.flow.StateFlow

data class AppCandidate(
    val label: String,
    val packageName: String,
)

data class ExecutionStep(
    val action: AgentAction,
    val expectedObservation: String,
)

data class ExecutionTrace(
    val steps: List<ExecutionStep>,
    val currentStepIndex: Int = 0,
)

enum class PlanningMode {
    INITIAL,
    STEPWISE_REPLAN,
}

data class PlannerInput(
    val goal: String,
    val foregroundPackage: String?,
    val lastExternalForegroundPackage: String?,
    val serializedNodeTree: String,
    val recentActionHistory: List<String>,
    val appMemory: List<String> = emptyList(),
    val appSkillGuidance: List<String> = emptyList(),
    val riskHints: List<String>,
    val candidateApps: List<AppCandidate> = emptyList(),
    val planningMode: PlanningMode = PlanningMode.INITIAL,
    val priorPlanSummary: String? = null,
    val stepBudget: Int = 4,
)

data class PlanDraft(
    val summary: String,
    val steps: List<ExecutionStep>,
    val riskLevel: RiskLevel,
    val needsConfirmation: Boolean,
    val targetPackageCandidates: List<String>,
    val rawModelOutput: String,
    val rawPlanJson: String,
)

enum class ContextProfile {
    FULL,
    REDUCED,
}

data class DeviceCapabilityProfile(
    val supported: Boolean,
    val reason: String,
    val primaryAbi: String,
    val totalMemoryMb: Long,
    val availableStorageMb: Long,
    val recommendedThreads: Int,
    val contextProfile: ContextProfile,
    val backendLabel: String,
)

data class RuntimePreparation(
    val ready: Boolean,
    val detail: String,
    val modelPath: String? = null,
    val profile: DeviceCapabilityProfile,
)

data class WarmUpReport(
    val success: Boolean,
    val detail: String,
    val initTimeSeconds: Double? = null,
    val timeToFirstTokenSeconds: Double? = null,
)

enum class RuntimePhase {
    IDLE,
    DOWNLOADING_MODEL,
    PREPARING,
    READY,
    UNSUPPORTED,
    FAILED,
}

data class RuntimeState(
    val phase: RuntimePhase = RuntimePhase.IDLE,
    val detail: String = "Idle",
    val modelPath: String? = null,
    val profile: DeviceCapabilityProfile? = null,
)

interface LocalAgentRuntime {
    val state: StateFlow<RuntimeState>

    suspend fun probeDeviceCapability(): DeviceCapabilityProfile

    suspend fun prepare(profile: DeviceCapabilityProfile): RuntimePreparation

    suspend fun warmUp(): WarmUpReport

    suspend fun plan(input: PlannerInput): PlanDraft
}
