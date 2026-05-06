package com.guribbong.phoneappagent.agent

import android.content.Context
import com.guribbong.phoneappagent.accessibility.AccessibilityOverlayBridge
import com.guribbong.phoneappagent.accessibility.AccessibilityServiceHealth
import com.guribbong.phoneappagent.accessibility.AccessibilityStatusRepository
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.ScrollDirection
import com.guribbong.phoneappagent.core.dsl.historyKey
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.core.runner.LocalAgentRuntime
import com.guribbong.phoneappagent.core.runner.LoopDetector
import com.guribbong.phoneappagent.core.runner.PlanDraft
import com.guribbong.phoneappagent.core.runner.PlanValidator
import com.guribbong.phoneappagent.core.runner.PlanningMode
import com.guribbong.phoneappagent.core.runner.PlannerInput
import com.guribbong.phoneappagent.data.history.SessionRepository
import com.guribbong.phoneappagent.execution.CanonicalPackageResolver
import com.guribbong.phoneappagent.execution.PlanExecutor
import com.guribbong.phoneappagent.skills.SkillResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AgentRunPhase {
    IDLE,
    PREPARING,
    PLANNING,
    EXECUTING,
    WAITING_FOR_CONFIRM,
    RECOVERING,
    PAUSED,
    COMPLETED,
    FAILED,
}

private const val PLANNER_STEP_BUDGET = 4
private const val MAX_REPLAN_PASSES = 8

private enum class PlanLoopResult {
    WAITING_FOR_CONFIRM,
    PAUSED,
    NEEDS_REPLAN,
    COMPLETED,
    FAILED,
}

data class AgentOrchestratorState(
    val phase: AgentRunPhase = AgentRunPhase.IDLE,
    val detail: String = "Idle",
    val currentGoal: String = "",
    val currentPlan: PlanDraft? = null,
    val currentStepIndex: Int = -1,
    val sessionId: Long? = null,
    val lastObservedPackage: String? = null,
    val failureReason: String? = null,
)

class AgentOrchestrator(
    context: Context,
    private val sessionRepository: SessionRepository,
    private val accessibilityRepository: AccessibilityStatusRepository,
    private val runtime: LocalAgentRuntime,
    private val planExecutor: PlanExecutor,
    private val policyGate: PolicyGate,
    private val appCatalog: InstalledAppCatalog,
    private val packageResolver: CanonicalPackageResolver,
    private val powerController: AgentPowerController,
    private val skillResolver: SkillResolver,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AgentOrchestratorState())
    private var activeJob: Job? = null
    private var activePlan: PlanDraft? = null
    private var activeSessionId: Long? = null
    private val activeActionHistory = ArrayDeque<String>()
    private val planValidator = PlanValidator()
    private val loopDetector = LoopDetector()
    private var pauseRequested = false
    private var stopRequested = false

    val state: StateFlow<AgentOrchestratorState> = _state.asStateFlow()

    suspend fun bootstrapPendingSession() {
        sessionRepository.ensureSeedSession()
        sessionRepository.latestRecoverableSession()?.let { recoverable ->
            sessionRepository.updateSessionStatus(
                sessionId = recoverable.sessionId,
                status = "failed",
                failureReason = "App restarted during active run. Requeue the goal.",
                confirmGateState = "not_required",
            )
        }
    }

    fun startGoal(goal: String) {
        val normalizedGoal = goal.trim()
        if (normalizedGoal.isEmpty()) return
        stopRequested = false
        pauseRequested = false
        activeActionHistory.clear()
        loopDetector.reset()
        activeJob?.cancel()
        activeJob = launchActiveJob { runGoal(normalizedGoal) }
    }

    fun pause() {
        pauseRequested = true
    }

    fun resume() {
        val plan = activePlan ?: return
        val sessionId = activeSessionId ?: return
        if (_state.value.phase != AgentRunPhase.PAUSED) return
        pauseRequested = false
        stopRequested = false
        activeJob = launchActiveJob {
            runPlanningLoop(
                sessionId = sessionId,
                goal = _state.value.currentGoal,
                initialPlan = plan,
                startIndex = (_state.value.currentStepIndex + 1).coerceAtLeast(0),
            )
        }
    }

    fun confirmAndContinue() {
        val plan = activePlan ?: return
        val sessionId = activeSessionId ?: return
        if (_state.value.phase != AgentRunPhase.WAITING_FOR_CONFIRM) return
        pauseRequested = false
        stopRequested = false
        activeJob = launchActiveJob {
            sessionRepository.updateSessionStatus(
                sessionId = sessionId,
                status = "executing",
                confirmGateState = "confirmed",
                replaceFailureReason = true,
            )
            val resumeStartIndex = (_state.value.currentStepIndex + 2).coerceAtMost(plan.steps.size)
            if (!returnToGuardedTargetAppIfNeeded(sessionId, _state.value.currentGoal, plan, resumeStartIndex)) {
                return@launchActiveJob
            }
            runPlanningLoop(
                sessionId = sessionId,
                goal = _state.value.currentGoal,
                initialPlan = plan,
                startIndex = resumeStartIndex,
            )
        }
    }

    fun stop() {
        stopRequested = true
        AccessibilityOverlayBridge.hide()
        powerController.releaseAfterActiveRun()
        val sessionId = activeSessionId
        if (sessionId != null) {
            scope.launch {
                sessionRepository.updateSessionStatus(
                    sessionId = sessionId,
                    status = "stopped",
                    currentStepIndex = _state.value.currentStepIndex,
                    failureReason = "Stopped by user.",
                    confirmGateState = "not_required",
                )
            }
        }
        _state.value = _state.value.copy(
            phase = AgentRunPhase.FAILED,
            detail = "Stopped by user.",
            failureReason = "Stopped by user.",
        )
    }

    private suspend fun runGoal(goal: String) {
        mutex.withLock {
            accessibilityRepository.refreshState()
            val initialSnapshot = awaitAccessibilityReady()
            val sessionId = sessionRepository.createSession(
                title = goal,
                appName = appCatalog.goalAliasLabel(goal)
                    ?: appCatalog.displayName(initialSnapshot.lastExternalForegroundPackage ?: initialSnapshot.foregroundPackage),
                status = "preparing",
                userGoal = goal,
                plannerInputSummary = "preparing runtime",
            )
            activeSessionId = sessionId
            AccessibilityOverlayBridge.show(
                statusLabel = "Agent starting",
                packageName = initialSnapshot.foregroundPackage ?: initialSnapshot.lastExternalForegroundPackage,
                stepLabel = "preparing",
            )
            _state.value = AgentOrchestratorState(
                phase = AgentRunPhase.PREPARING,
                detail = "Probing device capability",
                currentGoal = goal,
                sessionId = sessionId,
                lastObservedPackage = initialSnapshot.foregroundPackage,
            )

            powerController.blockingReason()?.let { reason ->
                failSession(
                    sessionId = sessionId,
                    goal = goal,
                    reason = reason,
                )
                return
            }

            if (!initialSnapshot.enabled || initialSnapshot.serviceHealth == AccessibilityServiceHealth.RECONNECT_REQUIRED) {
                failSession(
                    sessionId = sessionId,
                    goal = goal,
                    reason = "Accessibility service must be enabled and connected before execution.",
                )
                return
            }

            try {
                val profile = runtime.probeDeviceCapability()
                val preparation = runtime.prepare(profile)
                if (!preparation.ready) {
                    failSession(
                        sessionId = sessionId,
                        goal = goal,
                        reason = preparation.detail,
                    )
                    return
                }
                val warmUp = runtime.warmUp()
                if (!warmUp.success) {
                    failSession(
                        sessionId = sessionId,
                        goal = goal,
                        reason = warmUp.detail,
                    )
                    return
                }

                _state.value = _state.value.copy(
                    phase = AgentRunPhase.PLANNING,
                    detail = "Planning on device",
                )
                AccessibilityOverlayBridge.show(
                    statusLabel = "Agent planning",
                    packageName = accessibilityRepository.snapshot.value.foregroundPackage,
                    stepLabel = "planning",
                )
                val planningSnapshot = accessibilityRepository.snapshot.value
                val plannerInput = buildPlannerInput(
                    goal = goal,
                    snapshot = planningSnapshot,
                    planningMode = PlanningMode.INITIAL,
                )
                appCatalog.missingLaunchableAliasReason(goal)?.let { reason ->
                    sessionRepository.updateSessionStatus(
                        sessionId = sessionId,
                        status = "preparing",
                        appName = appCatalog.goalAliasLabel(goal)
                            ?: appCatalog.displayName(plannerInput.candidateApps.firstOrNull()?.packageName),
                        plannerInputSummary = plannerInputSummary(plannerInput),
                        replaceFailureReason = true,
                    )
                    failSession(
                        sessionId = sessionId,
                        goal = goal,
                        reason = reason,
                    )
                    return
                }
                val plan = runtime.plan(plannerInput)
                validatePlanOrFail(
                    sessionId = sessionId,
                    goal = goal,
                    plan = plan,
                ) ?: return
                activePlan = plan
                appendPlanTrace(
                    sessionId = sessionId,
                    plannerInput = plannerInput,
                    plan = plan,
                    observedPackage = planningSnapshot.foregroundPackage,
                )
                val targetApp = plan.targetPackageCandidates.firstOrNull()
                    ?: plannerInput.candidateApps.firstOrNull()?.packageName
                    ?: planningSnapshot.lastExternalForegroundPackage
                    ?: planningSnapshot.foregroundPackage
                    .orEmpty()
                sessionRepository.updateSessionStatus(
                    sessionId = sessionId,
                    status = "executing",
                    appName = appCatalog.displayName(targetApp.ifBlank { planningSnapshot.foregroundPackage }),
                    plannerInputSummary = plannerInputSummary(plannerInput),
                    validatedPlanJson = plan.rawPlanJson,
                    currentStepIndex = -1,
                    lastObservedPackage = planningSnapshot.foregroundPackage,
                    confirmGateState = "not_required",
                    replaceFailureReason = true,
                )

                runPlanningLoop(
                    sessionId = sessionId,
                    goal = goal,
                    initialPlan = plan,
                    startIndex = 0,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                failSession(
                    sessionId = sessionId,
                    goal = goal,
                    reason = throwable.message ?: throwable::class.java.simpleName,
                )
            }
        }
    }

    private suspend fun runPlanningLoop(
        sessionId: Long,
        goal: String,
        initialPlan: PlanDraft,
        startIndex: Int,
    ) {
        var currentPlan = initialPlan
        var currentStartIndex = startIndex
        var replanPasses = 0

        while (true) {
            activePlan = currentPlan
            when (
                executePlan(
                    sessionId = sessionId,
                    goal = goal,
                    plan = currentPlan,
                    startIndex = currentStartIndex,
                )
            ) {
                PlanLoopResult.WAITING_FOR_CONFIRM,
                PlanLoopResult.PAUSED,
                PlanLoopResult.COMPLETED,
                PlanLoopResult.FAILED,
                -> return

                PlanLoopResult.NEEDS_REPLAN -> {
                    if (replanPasses >= MAX_REPLAN_PASSES) {
                        failSession(
                            sessionId = sessionId,
                            goal = goal,
                            reason = "Planner exceeded $MAX_REPLAN_PASSES replans without converging.",
                        )
                        return
                    }
                    accessibilityRepository.refreshState()
                    val snapshot = accessibilityRepository.snapshot.value
                    _state.value = _state.value.copy(
                        phase = AgentRunPhase.PLANNING,
                        detail = "Refreshing plan from current screen",
                        currentPlan = currentPlan,
                        currentStepIndex = -1,
                        lastObservedPackage = snapshot.foregroundPackage,
                    )
                    AccessibilityOverlayBridge.show(
                        statusLabel = "Agent replanning",
                        packageName = snapshot.foregroundPackage,
                        stepLabel = "planning",
                    )
                    val plannerInput = buildPlannerInput(
                        goal = goal,
                        snapshot = snapshot,
                        planningMode = PlanningMode.STEPWISE_REPLAN,
                        priorPlan = currentPlan,
                    )
                    val nextPlan = runtime.plan(plannerInput)
                    validatePlanOrFail(
                        sessionId = sessionId,
                        goal = goal,
                        plan = nextPlan,
                    ) ?: return
                    currentPlan = nextPlan
                    currentStartIndex = 0
                    replanPasses += 1
                    activePlan = nextPlan
                    appendPlanTrace(
                        sessionId = sessionId,
                        plannerInput = plannerInput,
                        plan = nextPlan,
                        observedPackage = snapshot.foregroundPackage,
                    )
                    val targetApp = nextPlan.targetPackageCandidates.firstOrNull()
                        ?: plannerInput.candidateApps.firstOrNull()?.packageName
                        ?: snapshot.lastExternalForegroundPackage
                        ?: snapshot.foregroundPackage
                        .orEmpty()
                    sessionRepository.updateSessionStatus(
                        sessionId = sessionId,
                        status = "executing",
                        appName = appCatalog.displayName(targetApp.ifBlank { snapshot.foregroundPackage }),
                        plannerInputSummary = plannerInputSummary(plannerInput),
                        validatedPlanJson = nextPlan.rawPlanJson,
                        currentStepIndex = -1,
                        lastObservedPackage = snapshot.foregroundPackage,
                        confirmGateState = "not_required",
                        replaceFailureReason = true,
                    )
                }
            }
        }
    }

    private suspend fun executePlan(
        sessionId: Long,
        goal: String,
        plan: PlanDraft,
        startIndex: Int,
    ): PlanLoopResult {
        for (index in startIndex until plan.steps.size) {
            if (stopRequested) {
                failSession(sessionId, goal, "Stopped by user.")
                return PlanLoopResult.FAILED
            }
            if (pauseRequested) {
                _state.value = _state.value.copy(
                    phase = AgentRunPhase.PAUSED,
                    detail = "Execution paused",
                    currentPlan = plan,
                    currentStepIndex = index - 1,
                )
                sessionRepository.updateSessionStatus(
                    sessionId = sessionId,
                    status = "paused",
                    currentStepIndex = index - 1,
                    confirmGateState = "not_required",
                )
                return PlanLoopResult.PAUSED
            }

            val step = plan.steps[index]
            val confirmAction = step.action as? AgentAction.ConfirmUser
            if (confirmAction != null) {
                val snapshot = accessibilityRepository.snapshot.value
                val observedPackage =
                    snapshot.visibleNodes.firstNotNullOfOrNull { node ->
                        node.packageName?.takeIf { candidatePackage -> candidatePackage in plan.targetPackageCandidates }
                    } ?: plan.targetPackageCandidates.firstOrNull { candidate ->
                        candidate == snapshot.foregroundPackage || candidate == snapshot.lastExternalForegroundPackage
                    } ?: snapshot.lastExternalForegroundPackage ?: snapshot.foregroundPackage
                AccessibilityOverlayBridge.show(
                    statusLabel = "Awaiting confirmation",
                    packageName = observedPackage,
                    stepLabel = "confirm_user",
                )
                sessionRepository.appendActionLog(
                    sessionId = sessionId,
                    stepIndex = index,
                    actionType = step.action::class.simpleName.orEmpty(),
                    selectorSummary = null,
                    resultStatus = "awaiting_confirmation",
                    detail = confirmAction.reason,
                    observedPackage = observedPackage,
                )
                rememberAction(step.action)
                _state.value = _state.value.copy(
                    phase = AgentRunPhase.WAITING_FOR_CONFIRM,
                    detail = confirmAction.reason,
                    currentPlan = plan,
                    currentStepIndex = index - 1,
                )
                sessionRepository.updateSessionStatus(
                    sessionId = sessionId,
                    status = "confirm required",
                    currentStepIndex = index - 1,
                    confirmGateState = "awaiting_confirmation",
                )
                return PlanLoopResult.WAITING_FOR_CONFIRM
            }

            if (step.action == AgentAction.Stop) {
                sessionRepository.appendActionLog(
                    sessionId = sessionId,
                    stepIndex = index,
                    actionType = "Stop",
                    selectorSummary = null,
                    resultStatus = "ok",
                    detail = "Planner reached a safe stop checkpoint.",
                    observedPackage = accessibilityRepository.snapshot.value.foregroundPackage,
                )
                rememberAction(step.action)
                completeSession(
                    sessionId = sessionId,
                    goal = goal,
                    plan = plan,
                    completedIndex = index,
                )
                return PlanLoopResult.COMPLETED
            }

            val observedPackage = accessibilityRepository.snapshot.value.foregroundPackage
            val loopResult = loopDetector.record(
                packageName = observedPackage,
                screenSummary = screenSummary(accessibilityRepository.snapshot.value),
                action = step.action,
            )
            if (loopResult.triggered) {
                failSession(
                    sessionId = sessionId,
                    goal = goal,
                    reason = "LoopDetected: repeated ${loopResult.actionHistoryKey} on ${loopResult.packageName} (${loopResult.screenSummary}) ${loopResult.repeatCount} times.",
                )
                return PlanLoopResult.FAILED
            }
            AccessibilityOverlayBridge.show(
                statusLabel = "Agent active",
                packageName = observedPackage,
                stepLabel = step.action.describe(),
            )
            _state.value = _state.value.copy(
                phase = AgentRunPhase.EXECUTING,
                detail = step.expectedObservation,
                currentPlan = plan,
                currentStepIndex = index,
                lastObservedPackage = observedPackage,
            )
            val result = executeWithRecovery(step)
            sessionRepository.appendActionLog(
                sessionId = sessionId,
                stepIndex = index,
                actionType = step.action::class.simpleName.orEmpty(),
                selectorSummary = step.action.selectorSummary(),
                resultStatus = if (result.success) "ok" else "failed",
                detail = result.detail,
                observedPackage = result.observedPackage,
            )
            sessionRepository.updateSessionStatus(
                sessionId = sessionId,
                status = if (result.success) "executing" else "failed",
                currentStepIndex = index,
                lastObservedPackage = result.observedPackage ?: accessibilityRepository.snapshot.value.foregroundPackage,
                lastSelectorFailureReason = result.selectorFailureReason,
                failureReason = if (result.success) null else result.detail,
                replaceFailureReason = true,
            )
            if (!result.success) {
                failSession(
                    sessionId = sessionId,
                    goal = goal,
                    reason = result.detail,
                )
                return PlanLoopResult.FAILED
            }
            rememberAction(step.action)
        }

        return PlanLoopResult.NEEDS_REPLAN
    }

    private suspend fun returnToGuardedTargetAppIfNeeded(
        sessionId: Long,
        goal: String,
        plan: PlanDraft,
        resumeStartIndex: Int,
    ): Boolean {
        val targetPackage = plan.guardedTargetPackage(resumeStartIndex) ?: return true
        accessibilityRepository.refreshState()
        val snapshot = accessibilityRepository.snapshot.value
        val targetVisible = snapshot.visibleNodes.any { node ->
            packageResolver.matches(targetPackage, node.packageName)
        }
        if (packageResolver.matches(targetPackage, snapshot.foregroundPackage) || targetVisible) {
            return true
        }

        AccessibilityOverlayBridge.show(
            statusLabel = "Returning to app",
            packageName = targetPackage,
            stepLabel = "resume_guarded_step",
        )
        _state.value = _state.value.copy(
            phase = AgentRunPhase.EXECUTING,
            detail = "Returning to $targetPackage before continuing confirmed action.",
            lastObservedPackage = snapshot.foregroundPackage,
        )

        val launchAction = AgentAction.LaunchApp(targetPackage)
        val launchResult = planExecutor.executeStep(launchAction)
        sessionRepository.appendActionLog(
            sessionId = sessionId,
            stepIndex = resumeStartIndex,
            actionType = "ReturnToTargetApp",
            selectorSummary = targetPackage,
            resultStatus = if (launchResult.success) "ok" else "failed",
            detail = launchResult.detail,
            observedPackage = launchResult.observedPackage,
        )
        if (!launchResult.success) {
            failSession(sessionId, goal, launchResult.detail)
            return false
        }
        rememberAction(launchAction)

        val waitAction = AgentAction.WaitForApp(targetPackage)
        val waitResult = planExecutor.executeStep(waitAction)
        sessionRepository.appendActionLog(
            sessionId = sessionId,
            stepIndex = resumeStartIndex,
            actionType = "WaitForTargetApp",
            selectorSummary = targetPackage,
            resultStatus = if (waitResult.success) "ok" else "failed",
            detail = waitResult.detail,
            observedPackage = waitResult.observedPackage,
        )
        if (!waitResult.success) {
            failSession(sessionId, goal, waitResult.detail)
            return false
        }
        rememberAction(waitAction)
        accessibilityRepository.refreshState()
        return true
    }

    private suspend fun validatePlanOrFail(
        sessionId: Long,
        goal: String,
        plan: PlanDraft,
    ): PlanDraft? {
        val result = planValidator.validate(plan)
        if (result.isValid) return plan

        failSession(
            sessionId = sessionId,
            goal = goal,
            reason = "Plan validation failed: ${result.errors.joinToString("; ")}",
        )
        return null
    }

    private suspend fun executeWithRecovery(step: ExecutionStep) =
        planExecutor.executeStep(step.action).let { first ->
            if (first.success) {
                AccessibilityOverlayBridge.show(
                    statusLabel = "Step complete",
                    packageName = first.observedPackage,
                    stepLabel = step.action.describe(),
                    targetBounds = first.targetBounds,
                )
                first
            } else if (step.action.supportsRecovery()) {
                _state.value = _state.value.copy(
                    phase = AgentRunPhase.RECOVERING,
                    detail = "Recovering ${step.action.describe()}",
                )
                AccessibilityOverlayBridge.show(
                    statusLabel = "Recovering",
                    packageName = first.observedPackage ?: accessibilityRepository.snapshot.value.foregroundPackage,
                    stepLabel = step.action.describe(),
                    targetBounds = first.targetBounds,
                )
                val recovery = planExecutor.executeStep(
                    AgentAction.Scroll(direction = ScrollDirection.DOWN),
                )
                if (!recovery.success) {
                    first
                } else {
                    planExecutor.executeStep(step.action)
                }
            } else {
                first
            }
        }

    private suspend fun failSession(
        sessionId: Long,
        goal: String,
        reason: String,
    ) {
        rememberTerminalOutcome(
            goal = goal,
            status = "failure",
            note = buildFailureMemory(reason),
        )
        AccessibilityOverlayBridge.hide()
        sessionRepository.updateSessionStatus(
            sessionId = sessionId,
            status = "failed",
            currentStepIndex = _state.value.currentStepIndex,
            failureReason = reason,
            confirmGateState = "not_required",
            replaceFailureReason = true,
        )
        _state.value = AgentOrchestratorState(
            phase = AgentRunPhase.FAILED,
            detail = reason,
            currentGoal = goal,
            currentPlan = activePlan,
            currentStepIndex = _state.value.currentStepIndex,
            sessionId = sessionId,
            lastObservedPackage = accessibilityRepository.snapshot.value.foregroundPackage,
            failureReason = reason,
        )
    }

    private suspend fun completeSession(
        sessionId: Long,
        goal: String,
        plan: PlanDraft,
        completedIndex: Int,
    ) {
        rememberTerminalOutcome(
            goal = goal,
            status = "success",
            note = buildSuccessMemory(plan, completedIndex),
        )
        AccessibilityOverlayBridge.hide()
        sessionRepository.updateSessionStatus(
            sessionId = sessionId,
            status = "completed",
            currentStepIndex = completedIndex,
            confirmGateState = "not_required",
            failureReason = null,
            replaceFailureReason = true,
        )
        _state.value = AgentOrchestratorState(
            phase = AgentRunPhase.COMPLETED,
            detail = "Execution completed",
            currentGoal = goal,
            currentPlan = plan,
            currentStepIndex = completedIndex,
            sessionId = sessionId,
            lastObservedPackage = accessibilityRepository.snapshot.value.foregroundPackage,
            failureReason = null,
        )
    }

    private suspend fun appendPlanTrace(
        sessionId: Long,
        plannerInput: PlannerInput,
        plan: PlanDraft,
        observedPackage: String?,
    ) {
        sessionRepository.appendActionLog(
            sessionId = sessionId,
            stepIndex = -1,
            actionType = "PlanDraft",
            selectorSummary = plannerInput.planningMode.name,
            resultStatus = "ready",
            detail = "${plan.summary} | steps=${plan.steps.size} | mode=${plannerInput.planningMode.name}",
            observedPackage = observedPackage,
        )
    }

    private fun rememberAction(action: AgentAction) {
        activeActionHistory += action.historyKey()
        while (activeActionHistory.size > 16) {
            activeActionHistory.removeFirst()
        }
    }

    private suspend fun buildPlannerInput(
        goal: String,
        snapshot: com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot,
        planningMode: PlanningMode,
        priorPlan: PlanDraft? = activePlan,
    ): PlannerInput {
        val candidates = appCatalog.candidateAppsForGoal(goal)
        val memoryPackages = buildMemoryPackages(candidates, snapshot)
        val appMemory = sessionRepository.appMemoriesForPackages(memoryPackages)
            .map { memory ->
                "${memory.packageName} ${memory.outcome}: ${memory.note}"
            }
        val skillMemoryRows = sessionRepository.skillMemoriesForPackages(memoryPackages)
        val learnedByPackage = skillMemoryRows
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                rows.take(4).map { row -> "${row.outcome}: ${row.note}" }
            }
        val appSkillGuidance = skillResolver.resolve(
            goal = goal,
            packageNames = memoryPackages,
            learnedMemories = learnedByPackage,
        ).map { it.toPlannerText() }
        return PlannerInput(
            goal = goal,
            foregroundPackage = snapshot.foregroundPackage,
            lastExternalForegroundPackage = snapshot.lastExternalForegroundPackage,
            serializedNodeTree = serializeNodes(snapshot.visibleNodes),
            recentActionHistory = activeActionHistory.toList(),
            appMemory = appMemory,
            appSkillGuidance = appSkillGuidance,
            riskHints = buildList {
                when (policyGate.deriveRiskLevel(goal)) {
                    com.guribbong.phoneappagent.core.policy.RiskLevel.HIGH,
                    com.guribbong.phoneappagent.core.policy.RiskLevel.CRITICAL,
                    -> add("Goal contains high-risk terms.")

                    else -> Unit
                }
                if (snapshot.serviceHealth == AccessibilityServiceHealth.RECONNECT_REQUIRED) {
                    add("Accessibility service requires reconnection.")
                }
                when (planningMode) {
                    PlanningMode.INITIAL -> add("Analyze the current screen before creating the first checkpoint.")
                    PlanningMode.STEPWISE_REPLAN -> add("Continue from the current screen and recentActionHistory without restarting completed work.")
                }
            },
            candidateApps = candidates,
            planningMode = planningMode,
            priorPlanSummary = priorPlan?.summary,
            stepBudget = PLANNER_STEP_BUDGET,
        )
    }

    private fun plannerInputSummary(input: PlannerInput): String =
        buildString {
            append("goal=").append(input.goal)
            append(" | mode=").append(input.planningMode.name)
            append(" | foreground=").append(input.foregroundPackage ?: "none")
            append(" | lastExternal=").append(input.lastExternalForegroundPackage ?: "none")
            append(" | nodes=").append(input.serializedNodeTree.lineSequence().count())
            append(" | history=").append(input.recentActionHistory.size)
            append(" | memory=").append(input.appMemory.size)
            append(" | skills=").append(input.appSkillGuidance.size)
            append(" | skillMemory=").append(input.appSkillGuidance.count { "learned=" in it })
            append(" | candidates=").append(input.candidateApps.joinToString { it.packageName })
        }

    private fun buildMemoryPackages(
        candidates: List<com.guribbong.phoneappagent.core.runner.AppCandidate>,
        snapshot: com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot,
    ): List<String> =
        buildList {
            candidates.forEach { add(it.packageName) }
            snapshot.foregroundPackage?.let(::add)
            snapshot.lastExternalForegroundPackage?.let(::add)
            activePlan?.targetPackageCandidates?.forEach(::add)
        }.filter { it.isNotBlank() }.distinct()

    private suspend fun rememberTerminalOutcome(
        goal: String,
        status: String,
        note: String,
    ) {
        val snapshot = accessibilityRepository.snapshot.value
        val packageName = activePlan?.targetPackageCandidates?.firstOrNull()
            ?: snapshot.lastExternalForegroundPackage
            ?: snapshot.foregroundPackage
            ?: appCatalog.candidateAppsForGoal(goal).firstOrNull()?.packageName
            ?: return
        sessionRepository.rememberAppOutcome(
            packageName = packageName,
            appName = appCatalog.displayName(packageName),
            goal = goal,
            outcome = status,
            note = note,
        )
        sessionRepository.rememberSkillOutcome(
            packageName = packageName,
            appName = appCatalog.displayName(packageName),
            goal = goal,
            outcome = status,
            note = note,
        )
    }

    private fun buildSuccessMemory(plan: PlanDraft, completedIndex: Int): String {
        val actions = plan.steps
            .take(completedIndex + 1)
            .map { it.action.historyKey() }
            .takeLast(6)
            .joinToString(" -> ")
        return "This flow reached a safe checkpoint. Reuse the observed current UI and these recent successful actions when relevant: $actions"
    }

    private fun buildFailureMemory(reason: String): String =
        "Previous run failed: ${reason.take(180)}. Do not repeat the same selector/package blindly; inspect visibleNodes first, include full target info, and pivot strategy if the screen differs."

    private fun serializeNodes(nodes: List<com.guribbong.phoneappagent.driver.accessibility.UiNodeSnapshot>): String =
        nodes.take(256).mapIndexed { index, node ->
            buildString {
                append("ref=@e").append(index + 1)
                append(" | role=").append(node.roleLabel())
                append(" | text=").append(node.text ?: "")
                append(" | desc=").append(node.contentDescription ?: "")
                append(" | id=").append(node.resourceId ?: "")
                append(" | class=").append(node.className ?: "")
                append(" | package=").append(node.packageName ?: "")
                append(" | editable=").append(node.editable)
                append(" | clickable=").append(node.clickable)
                if (node.indexPath.isNotEmpty()) {
                    append(" | idx=").append(node.indexPath.joinToString("."))
                }
                node.bounds?.let { bounds ->
                    append(" | bounds=")
                        .append(bounds.left)
                        .append(',')
                        .append(bounds.top)
                        .append(',')
                        .append(bounds.right)
                        .append(',')
                        .append(bounds.bottom)
                }
            }
        }.joinToString(separator = "\n")

    private fun com.guribbong.phoneappagent.driver.accessibility.UiNodeSnapshot.roleLabel(): String {
        val classTail = className?.substringAfterLast('.').orEmpty().lowercase()
        return when {
            editable -> "text_field"
            clickable && "button" in classTail -> "button"
            clickable -> "control"
            "recyclerview" in classTail || "listview" in classTail || "scrollview" in classTail -> "list"
            !text.isNullOrBlank() || !contentDescription.isNullOrBlank() -> "text"
            else -> "node"
        }
    }

    private fun screenSummary(snapshot: com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot): String =
        buildString {
            append(snapshot.topNodeLabel?.takeIf { it.isNotBlank() } ?: "unknown")
            append("|nodes=").append(snapshot.nodeCount)
        }

    private suspend fun awaitAccessibilityReady(timeoutMs: Long = 25_000L): com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            accessibilityRepository.refreshState()
            val snapshot = accessibilityRepository.snapshot.value
            if (snapshot.enabled && snapshot.serviceHealth == AccessibilityServiceHealth.CONNECTED) {
                return snapshot
            }
            delay(250L)
        }
        return accessibilityRepository.snapshot.value
    }

    private fun launchActiveJob(block: suspend () -> Unit): Job =
        scope.launch {
            powerController.acquireForActiveRun()
            try {
                block()
            } finally {
                powerController.releaseAfterActiveRun()
            }
        }
}

private fun AgentAction.supportsRecovery(): Boolean =
    when (this) {
        is AgentAction.Tap,
        is AgentAction.OpenUri,
        is AgentAction.InputText,
        is AgentAction.SubmitInput,
        is AgentAction.ClearText,
        is AgentAction.AssertVisible,
        is AgentAction.WaitForNode,
        -> true

        else -> false
    }

private fun AgentAction.selectorSummary(): String? =
    when (this) {
        is AgentAction.Tap -> selector.label()
        is AgentAction.InputText -> selector.label()
        is AgentAction.SubmitInput -> selector.label()
        is AgentAction.ClearText -> selector.label()
        is AgentAction.AssertVisible -> selector.label()
        is AgentAction.WaitForNode -> selector.label()
        is AgentAction.Scroll -> selector?.label()
        else -> null
    }

private fun PlanDraft.guardedTargetPackage(startIndex: Int): String? =
    targetPackageCandidates.firstOrNull { it.isNotBlank() }
        ?: steps.drop(startIndex).firstNotNullOfOrNull { step -> step.action.targetPackage() }

private fun AgentAction.targetPackage(): String? =
    when (this) {
        is AgentAction.LaunchApp -> packageName
        is AgentAction.OpenUri -> packageName
        is AgentAction.WaitForApp -> packageName
        is AgentAction.WaitForNode -> selector.packageName
        is AgentAction.Tap -> selector.packageName
        is AgentAction.InputText -> selector.packageName
        is AgentAction.SubmitInput -> selector.packageName
        is AgentAction.ClearText -> selector.packageName
        is AgentAction.Scroll -> selector?.packageName
        is AgentAction.AssertVisible -> selector.packageName
        is AgentAction.WaitForCondition,
        is AgentAction.ConfirmUser,
        is AgentAction.PressGlobal,
        AgentAction.Stop,
        -> null
    }

private fun AgentAction.describe(): String =
    when (this) {
        is AgentAction.LaunchApp -> "Launch $packageName"
        is AgentAction.OpenUri -> "Open ${packageName ?: uri}"
        is AgentAction.WaitForApp -> "Wait for $packageName"
        is AgentAction.WaitForNode -> "Wait for ${selector.label()}"
        is AgentAction.Tap -> "Tap ${label.ifBlank { selector.label() }}"
        is AgentAction.InputText -> "Input text into ${selector.label()}"
        is AgentAction.SubmitInput -> "Submit ${selector.label()}"
        is AgentAction.ClearText -> "Clear ${selector.label()}"
        is AgentAction.Scroll -> "Scroll ${selector?.label() ?: "current container"}"
        is AgentAction.PressGlobal -> "Press ${action.name}"
        is AgentAction.AssertVisible -> "Assert ${selector.label()} visible"
        is AgentAction.WaitForCondition -> "Wait for ${condition}"
        is AgentAction.ConfirmUser -> "Confirm ${reason}"
        AgentAction.Stop -> "Stop"
    }
