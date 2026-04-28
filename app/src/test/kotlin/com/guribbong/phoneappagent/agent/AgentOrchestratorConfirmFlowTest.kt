package com.guribbong.phoneappagent.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.guribbong.phoneappagent.accessibility.AccessibilityServiceHealth
import com.guribbong.phoneappagent.accessibility.AccessibilitySnapshot
import com.guribbong.phoneappagent.accessibility.AccessibilityStatusRepository
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.runner.DeviceCapabilityProfile
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.core.runner.LocalAgentRuntime
import com.guribbong.phoneappagent.core.runner.PlanDraft
import com.guribbong.phoneappagent.core.runner.PlanningMode
import com.guribbong.phoneappagent.core.runner.PlannerInput
import com.guribbong.phoneappagent.core.runner.RuntimePhase
import com.guribbong.phoneappagent.core.runner.RuntimePreparation
import com.guribbong.phoneappagent.core.runner.RuntimeState
import com.guribbong.phoneappagent.core.runner.WarmUpReport
import com.guribbong.phoneappagent.data.history.AgentSessionState
import com.guribbong.phoneappagent.data.history.AgentAppMemory
import com.guribbong.phoneappagent.data.history.AgentSkillMemory
import com.guribbong.phoneappagent.data.history.ChatSessionSummary
import com.guribbong.phoneappagent.data.history.RecoverableSession
import com.guribbong.phoneappagent.data.history.SessionRepository
import com.guribbong.phoneappagent.driver.accessibility.AccessibilityDriver
import com.guribbong.phoneappagent.driver.accessibility.ActionExecutionResult
import com.guribbong.phoneappagent.execution.CanonicalPackageResolver
import com.guribbong.phoneappagent.execution.PlanExecutor
import com.guribbong.phoneappagent.skills.AppSkillContext
import com.guribbong.phoneappagent.skills.AppSkillProcedure
import com.guribbong.phoneappagent.skills.AppSkillStepHint
import com.guribbong.phoneappagent.skills.SkillResolver
import com.guribbong.phoneappagent.skills.SkillSelectorHint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentOrchestratorConfirmFlowTest {
    @Before
    fun stopKoinBefore() {
        stopKoin()
    }

    @After
    fun stopKoinAfter() {
        stopKoin()
    }

    @Test
    fun confirmFlowStopsBeforeCommitAndResumesAtNextStep() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionRepository = FakeSessionRepository()
        val accessibilityRepository = FakeAccessibilityStatusRepository()
        val recordedActions = mutableListOf<AgentAction>()
        val runtimePlan = PlanDraft(
            summary = "Samsung Messages send flow",
            steps = listOf(
                ExecutionStep(
                    action = AgentAction.LaunchApp("com.samsung.android.messaging"),
                    expectedObservation = "Messages launches",
                ),
                ExecutionStep(
                    action = AgentAction.WaitForApp("com.samsung.android.messaging"),
                    expectedObservation = "Messages reaches the foreground",
                ),
                ExecutionStep(
                    action = AgentAction.ConfirmUser("User confirmation required before sending."),
                    expectedObservation = "Wait at confirm_user",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = NodeSelector(
                            resourceId = "com.samsung.android.messaging:id/send_button",
                            packageName = "com.samsung.android.messaging",
                        ),
                        label = "Send",
                    ),
                    expectedObservation = "Send button tapped after confirmation",
                ),
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "The goal is complete after the guarded send action.",
                ),
            ),
            riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.CRITICAL,
            needsConfirmation = true,
            targetPackageCandidates = listOf("com.samsung.android.messaging"),
            rawModelOutput = "{}",
            rawPlanJson = """{"steps":["launch","wait","confirm","tap","stop"]}""",
        )
        val orchestrator = AgentOrchestrator(
            context = context,
            sessionRepository = sessionRepository,
            accessibilityRepository = accessibilityRepository,
            runtime = FakeRuntime(runtimePlan),
            planExecutor = PlanExecutor(RecordingAccessibilityDriver(recordedActions)),
            policyGate = PolicyGate(),
            appCatalog = InstalledAppCatalog(context),
            packageResolver = CanonicalPackageResolver(),
            powerController = AgentPowerController(context),
            skillResolver = FakeSkillResolver(),
        )

        orchestrator.startGoal("send hello to 12345")

        waitForPhase(orchestrator, AgentRunPhase.WAITING_FOR_CONFIRM)

        assertEquals(
            listOf(
                AgentAction.LaunchApp("com.samsung.android.messaging"),
                AgentAction.WaitForApp("com.samsung.android.messaging"),
            ),
            recordedActions,
        )
        assertEquals("confirm required", sessionRepository.latestStatus)
        assertEquals(1, sessionRepository.latestCurrentStepIndex)
        assertNull(sessionRepository.latestFailureReason)
        assertTrue(sessionRepository.actionLogs.any { it.actionType == "PlanDraft" })
        assertEquals(
            listOf(
                ActionLogRecord(
                    stepIndex = 0,
                    actionType = "LaunchApp",
                    resultStatus = "ok",
                    detail = "ok",
                    observedPackage = "com.samsung.android.messaging",
                ),
                ActionLogRecord(
                    stepIndex = 1,
                    actionType = "WaitForApp",
                    resultStatus = "ok",
                    detail = "ok",
                    observedPackage = "com.samsung.android.messaging",
                ),
                ActionLogRecord(
                    stepIndex = 2,
                    actionType = "ConfirmUser",
                    resultStatus = "awaiting_confirmation",
                    detail = "User confirmation required before sending.",
                    observedPackage = "com.samsung.android.messaging",
                ),
            ),
            sessionRepository.nonPlanActionLogs(),
        )

        orchestrator.confirmAndContinue()

        val terminalState = waitForTerminalState(orchestrator)
        assertEquals(sessionRepository.latestFailureReason, AgentRunPhase.COMPLETED, terminalState.phase)

        assertEquals(
            listOf(
                AgentAction.LaunchApp("com.samsung.android.messaging"),
                AgentAction.WaitForApp("com.samsung.android.messaging"),
                AgentAction.Tap(
                    selector = NodeSelector(
                        resourceId = "com.samsung.android.messaging:id/send_button",
                        packageName = "com.samsung.android.messaging",
                    ),
                    label = "Send",
                ),
            ),
            recordedActions,
        )
        assertEquals("completed", sessionRepository.latestStatus)
        assertEquals(4, sessionRepository.latestCurrentStepIndex)
        assertNull(sessionRepository.latestFailureReason)
        val nonPlanLogs = sessionRepository.nonPlanActionLogs()
        assertEquals(
            ActionLogRecord(
                stepIndex = 3,
                actionType = "Tap",
                resultStatus = "ok",
                detail = "ok",
                observedPackage = "com.samsung.android.messaging",
            ),
            nonPlanLogs[3],
        )
        assertEquals(
            ActionLogRecord(
                stepIndex = 4,
                actionType = "Stop",
                resultStatus = "ok",
                detail = "Planner reached a safe stop checkpoint.",
                observedPackage = "com.samsung.android.messaging",
            ),
            nonPlanLogs.last(),
        )
    }

    @Test
    fun replansFromCurrentScreenAfterMiniPlanFinishes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionRepository = FakeSessionRepository()
        val accessibilityRepository = FakeAccessibilityStatusRepository()
        val recordedActions = mutableListOf<AgentAction>()
        val runtime = FakeRuntime(
            PlanDraft(
                summary = "Open demo app first checkpoint",
                steps = listOf(
                    ExecutionStep(
                        action = AgentAction.LaunchApp("com.example.demo"),
                        expectedObservation = "Demo app launches",
                    ),
                    ExecutionStep(
                        action = AgentAction.WaitForApp("com.example.demo"),
                        expectedObservation = "Demo app reaches the foreground",
                    ),
                ),
                riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.LOW,
                needsConfirmation = false,
                targetPackageCandidates = listOf("com.example.demo"),
                rawModelOutput = "{}",
                rawPlanJson = """{"steps":["launch","wait"]}""",
            ),
            PlanDraft(
                summary = "Demo app already opened; stop at this checkpoint",
                steps = listOf(
                    ExecutionStep(
                        action = AgentAction.Stop,
                        expectedObservation = "Checkpoint reached",
                    ),
                ),
                riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.LOW,
                needsConfirmation = false,
                targetPackageCandidates = listOf("com.example.demo"),
                rawModelOutput = "{}",
                rawPlanJson = """{"steps":["stop"]}""",
            ),
        )
        val orchestrator = AgentOrchestrator(
            context = context,
            sessionRepository = sessionRepository,
            accessibilityRepository = accessibilityRepository,
            runtime = runtime,
            planExecutor = PlanExecutor(RecordingAccessibilityDriver(recordedActions)),
            policyGate = PolicyGate(),
            appCatalog = InstalledAppCatalog(context),
            packageResolver = CanonicalPackageResolver(),
            powerController = AgentPowerController(context),
            skillResolver = FakeSkillResolver(),
        )

        orchestrator.startGoal("Launch the demo app")

        val terminalState = waitForTerminalState(orchestrator)
        assertEquals(sessionRepository.latestFailureReason, AgentRunPhase.COMPLETED, terminalState.phase)

        assertEquals(
            listOf(
                AgentAction.LaunchApp("com.example.demo"),
                AgentAction.WaitForApp("com.example.demo"),
            ),
            recordedActions,
        )
        assertEquals(2, runtime.planInputs.size)
        assertEquals(PlanningMode.INITIAL, runtime.planInputs[0].planningMode)
        assertEquals(PlanningMode.STEPWISE_REPLAN, runtime.planInputs[1].planningMode)
        assertTrue(runtime.planInputs[1].recentActionHistory.contains("launch_app:com.example.demo"))
        assertTrue(runtime.planInputs[1].recentActionHistory.contains("wait_for_app:com.example.demo"))
        assertEquals("completed", sessionRepository.latestStatus)
        assertEquals(0, sessionRepository.latestCurrentStepIndex)
    }

    @Test
    fun replansAcrossMultiplePassesAndStopsAtConfirmGate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionRepository = FakeSessionRepository()
        val accessibilityRepository = FakeAccessibilityStatusRepository()
        val recordedActions = mutableListOf<AgentAction>()
        val runtime = FakeRuntime(
            PlanDraft(
                summary = "Open the target app first",
                steps = listOf(
                    ExecutionStep(
                        action = AgentAction.LaunchApp("com.samsung.android.messaging"),
                        expectedObservation = "Messages launches",
                    ),
                    ExecutionStep(
                        action = AgentAction.WaitForApp("com.samsung.android.messaging"),
                        expectedObservation = "Messages reaches foreground",
                    ),
                ),
                riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.MEDIUM,
                needsConfirmation = false,
                targetPackageCandidates = listOf("com.samsung.android.messaging"),
                rawModelOutput = "{}",
                rawPlanJson = """{"steps":["launch","wait"]}""",
            ),
            PlanDraft(
                summary = "Type the draft message",
                steps = listOf(
                    ExecutionStep(
                        action = AgentAction.WaitForNode(
                            selector = NodeSelector(
                                resourceId = "com.samsung.android.messaging:id/message_edit_text",
                                packageName = "com.samsung.android.messaging",
                            ),
                        ),
                        expectedObservation = "Message editor is visible",
                    ),
                    ExecutionStep(
                        action = AgentAction.InputText(
                            selector = NodeSelector(
                                resourceId = "com.samsung.android.messaging:id/message_edit_text",
                                packageName = "com.samsung.android.messaging",
                            ),
                            text = "hello",
                        ),
                        expectedObservation = "Draft text appears",
                    ),
                ),
                riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.MEDIUM,
                needsConfirmation = false,
                targetPackageCandidates = listOf("com.samsung.android.messaging"),
                rawModelOutput = "{}",
                rawPlanJson = """{"steps":["wait_for_node","input_text"]}""",
            ),
            PlanDraft(
                summary = "Pause before send",
                steps = listOf(
                    ExecutionStep(
                        action = AgentAction.ConfirmUser("User confirmation required before sending."),
                        expectedObservation = "Wait at confirm_user",
                    ),
                    ExecutionStep(
                        action = AgentAction.Tap(
                            selector = NodeSelector(
                                resourceId = "com.samsung.android.messaging:id/send_button",
                                packageName = "com.samsung.android.messaging",
                            ),
                            label = "Send",
                        ),
                        expectedObservation = "Send action fires after confirmation",
                    ),
                    ExecutionStep(
                        action = AgentAction.Stop,
                        expectedObservation = "The guarded flow is complete",
                    ),
                ),
                riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.CRITICAL,
                needsConfirmation = true,
                targetPackageCandidates = listOf("com.samsung.android.messaging"),
                rawModelOutput = "{}",
                rawPlanJson = """{"steps":["confirm","tap","stop"]}""",
            ),
        )
        val orchestrator = AgentOrchestrator(
            context = context,
            sessionRepository = sessionRepository,
            accessibilityRepository = accessibilityRepository,
            runtime = runtime,
            planExecutor = PlanExecutor(RecordingAccessibilityDriver(recordedActions)),
            policyGate = PolicyGate(),
            appCatalog = InstalledAppCatalog(context),
            packageResolver = CanonicalPackageResolver(),
            powerController = AgentPowerController(context),
            skillResolver = FakeSkillResolver(),
        )

        orchestrator.startGoal("send hello to 12345")

        waitForPhase(orchestrator, AgentRunPhase.WAITING_FOR_CONFIRM)

        assertEquals(3, runtime.planInputs.size)
        assertEquals(PlanningMode.INITIAL, runtime.planInputs[0].planningMode)
        assertEquals(PlanningMode.STEPWISE_REPLAN, runtime.planInputs[1].planningMode)
        assertEquals(PlanningMode.STEPWISE_REPLAN, runtime.planInputs[2].planningMode)
        assertTrue(runtime.planInputs[1].recentActionHistory.contains("launch_app:com.samsung.android.messaging"))
        assertTrue(runtime.planInputs[1].recentActionHistory.contains("wait_for_app:com.samsung.android.messaging"))
        assertTrue(
            runtime.planInputs[2].recentActionHistory.contains(
                "input_text:id=com.samsung.android.messaging:id/message_edit_text|package=com.samsung.android.messaging|text=hello",
            ),
        )
        assertEquals("confirm required", sessionRepository.latestStatus)

        orchestrator.confirmAndContinue()

        val terminalState = waitForTerminalState(orchestrator)
        assertEquals(sessionRepository.latestFailureReason, AgentRunPhase.COMPLETED, terminalState.phase)
        assertEquals("completed", sessionRepository.latestStatus)
        assertEquals(
            listOf(
                AgentAction.LaunchApp("com.samsung.android.messaging"),
                AgentAction.WaitForApp("com.samsung.android.messaging"),
                AgentAction.WaitForNode(
                    selector = NodeSelector(
                        resourceId = "com.samsung.android.messaging:id/message_edit_text",
                        packageName = "com.samsung.android.messaging",
                    ),
                ),
                AgentAction.InputText(
                    selector = NodeSelector(
                        resourceId = "com.samsung.android.messaging:id/message_edit_text",
                        packageName = "com.samsung.android.messaging",
                    ),
                    text = "hello",
                ),
                AgentAction.Tap(
                    selector = NodeSelector(
                        resourceId = "com.samsung.android.messaging:id/send_button",
                        packageName = "com.samsung.android.messaging",
                    ),
                    label = "Send",
                ),
            ),
            recordedActions,
        )
    }

    @Test
    fun appSkillGuidanceReachesPlannerInput() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionRepository = FakeSessionRepository()
        sessionRepository.skillMemories += AgentSkillMemory(
            packageName = "com.samsung.android.messaging",
            appName = "Messages",
            outcome = "success",
            note = "Compose button selector worked from inbox.",
        )
        val accessibilityRepository = FakeAccessibilityStatusRepository()
        val runtime = FakeRuntime(
            PlanDraft(
                summary = "Use Messages skill context",
                steps = listOf(
                    ExecutionStep(
                        action = AgentAction.Stop,
                        expectedObservation = "Stop after observing skill guidance.",
                    ),
                ),
                riskLevel = com.guribbong.phoneappagent.core.policy.RiskLevel.LOW,
                needsConfirmation = false,
                targetPackageCandidates = listOf("com.samsung.android.messaging"),
                rawModelOutput = "{}",
                rawPlanJson = """{"steps":["stop"]}""",
            ),
        )
        val orchestrator = AgentOrchestrator(
            context = context,
            sessionRepository = sessionRepository,
            accessibilityRepository = accessibilityRepository,
            runtime = runtime,
            planExecutor = PlanExecutor(RecordingAccessibilityDriver(mutableListOf())),
            policyGate = PolicyGate(),
            appCatalog = InstalledAppCatalog(context),
            packageResolver = CanonicalPackageResolver(),
            powerController = AgentPowerController(context),
            skillResolver = FakeSkillResolver(
                contexts = listOf(
                    AppSkillContext(
                        packageName = "com.samsung.android.messaging",
                        appName = "Messages",
                        procedures = listOf(
                            AppSkillProcedure(
                                name = "compose_message",
                                goalPatterns = listOf("send", "message"),
                                steps = listOf(
                                    AppSkillStepHint(
                                        intent = "Find compose button",
                                        preferredSelectors = listOf(
                                            SkillSelectorHint(
                                                resourceId = "com.samsung.android.messaging:id/fab",
                                                clickable = true,
                                            ),
                                        ),
                                        expectedObservation = "Recipient field is visible",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        orchestrator.startGoal("send a message")

        waitForTerminalState(orchestrator)

        val input = runtime.planInputs.single()
        assertTrue(input.appSkillGuidance.any { it.contains("compose_message") })
        assertTrue(input.appSkillGuidance.any { it.contains("learned=success: Compose button selector worked") })
    }

    private suspend fun waitForPhase(
        orchestrator: AgentOrchestrator,
        expected: AgentRunPhase,
    ) {
        withTimeout(5_000L) {
            while (orchestrator.state.value.phase != expected) {
                delay(25L)
            }
        }
    }

    private suspend fun waitForTerminalState(
        orchestrator: AgentOrchestrator,
    ): AgentOrchestratorState =
        withTimeout(5_000L) {
            var state = orchestrator.state.value
            while (state.phase != AgentRunPhase.COMPLETED && state.phase != AgentRunPhase.FAILED) {
                delay(25L)
                state = orchestrator.state.value
            }
            state
        }
}

private class FakeRuntime(
    vararg plans: PlanDraft,
) : LocalAgentRuntime {
    private val runtimeState = MutableStateFlow(RuntimeState(phase = RuntimePhase.READY, detail = "Ready"))
    private val planQueue = ArrayDeque(plans.toList())
    val planInputs = mutableListOf<PlannerInput>()

    override val state: StateFlow<RuntimeState> = runtimeState.asStateFlow()

    override suspend fun probeDeviceCapability(): DeviceCapabilityProfile =
        DeviceCapabilityProfile(
            supported = true,
            reason = "supported",
            primaryAbi = "arm64-v8a",
            totalMemoryMb = 8192,
            availableStorageMb = 32768,
            recommendedThreads = 4,
            contextProfile = com.guribbong.phoneappagent.core.runner.ContextProfile.FULL,
            backendLabel = "test",
        )

    override suspend fun prepare(profile: DeviceCapabilityProfile): RuntimePreparation =
        RuntimePreparation(
            ready = true,
            detail = "Prepared",
            modelPath = null,
            profile = profile,
        )

    override suspend fun warmUp(): WarmUpReport =
        WarmUpReport(
            success = true,
            detail = "Warm",
        )

    override suspend fun plan(input: PlannerInput): PlanDraft {
        planInputs += input
        return planQueue.removeFirstOrNull()
            ?: error("No more fake plans available for ${input.goal}")
    }
}

private class FakeSkillResolver(
    private val contexts: List<AppSkillContext> = emptyList(),
) : SkillResolver {
    override suspend fun resolve(
        goal: String,
        packageNames: List<String>,
        learnedMemories: Map<String, List<String>>,
    ): List<AppSkillContext> =
        contexts
            .filter { context -> context.packageName in packageNames }
            .map { context ->
                context.copy(
                    learnedMemories = learnedMemories[context.packageName].orEmpty(),
                )
            }
}

private class FakeAccessibilityStatusRepository : AccessibilityStatusRepository {
    private val snapshotFlow = MutableStateFlow(
        AccessibilitySnapshot(
            enabled = true,
            serviceHealth = AccessibilityServiceHealth.CONNECTED,
            foregroundPackage = "com.samsung.android.messaging",
            lastExternalForegroundPackage = "com.samsung.android.messaging",
            topNodeLabel = "Messages",
            nodeCount = 3,
            lastEvent = "test",
        ),
    )

    override val snapshot: StateFlow<AccessibilitySnapshot> = snapshotFlow.asStateFlow()

    override fun refreshState() = Unit

    override fun openAccessibilitySettings() = Unit

    override fun setOverlayEnabled(enabled: Boolean) = Unit
}

private class RecordingAccessibilityDriver(
    private val recordedActions: MutableList<AgentAction>,
) : AccessibilityDriver {
    override suspend fun observeForeground(): String = "com.samsung.android.messaging"

    override suspend fun execute(action: AgentAction): ActionExecutionResult {
        recordedActions += action
        return ActionExecutionResult(
            success = true,
            detail = "ok",
            observedPackage = "com.samsung.android.messaging",
        )
    }
}

private class FakeSessionRepository : SessionRepository {
    private val sessions = mutableMapOf<Long, SessionRecord>()
    private var nextId = 1L
    private val latestAgentState = MutableStateFlow<AgentSessionState?>(null)

    var latestStatus: String? = null
        private set
    var latestCurrentStepIndex: Int? = null
        private set
    var latestFailureReason: String? = null
        private set
    val actionLogs = mutableListOf<ActionLogRecord>()
    val appMemories = mutableListOf<AgentAppMemory>()
    val skillMemories = mutableListOf<AgentSkillMemory>()

    override fun observeSessions(): Flow<List<ChatSessionSummary>> = flowOf(emptyList())

    override fun observeLatestAgentState(): Flow<AgentSessionState?> = latestAgentState

    override suspend fun createSession(
        title: String,
        appName: String,
        status: String,
        userGoal: String,
        plannerInputSummary: String,
        validatedPlanJson: String,
        currentStepIndex: Int,
        lastObservedPackage: String?,
        lastSelectorFailureReason: String?,
        confirmGateState: String,
        failureReason: String?,
    ): Long {
        val id = nextId++
        sessions[id] = SessionRecord(
            id = id,
            title = title,
            appName = appName,
            status = status,
            userGoal = userGoal,
            plannerInputSummary = plannerInputSummary,
            validatedPlanJson = validatedPlanJson,
            currentStepIndex = currentStepIndex,
            lastObservedPackage = lastObservedPackage,
            lastSelectorFailureReason = lastSelectorFailureReason,
            confirmGateState = confirmGateState,
            failureReason = failureReason,
        )
        updateLatestState(id)
        latestStatus = status
        latestCurrentStepIndex = currentStepIndex
        latestFailureReason = failureReason
        return id
    }

    override suspend fun updateSessionStatus(
        sessionId: Long,
        status: String,
        appName: String?,
        plannerInputSummary: String?,
        validatedPlanJson: String?,
        currentStepIndex: Int?,
        lastObservedPackage: String?,
        lastSelectorFailureReason: String?,
        confirmGateState: String?,
        failureReason: String?,
        replaceFailureReason: Boolean,
    ) {
        val current = sessions.getValue(sessionId)
        sessions[sessionId] = current.copy(
            status = status,
            appName = appName ?: current.appName,
            plannerInputSummary = plannerInputSummary ?: current.plannerInputSummary,
            validatedPlanJson = validatedPlanJson ?: current.validatedPlanJson,
            currentStepIndex = currentStepIndex ?: current.currentStepIndex,
            lastObservedPackage = lastObservedPackage ?: current.lastObservedPackage,
            lastSelectorFailureReason = lastSelectorFailureReason ?: current.lastSelectorFailureReason,
            confirmGateState = confirmGateState ?: current.confirmGateState,
            failureReason = if (replaceFailureReason) failureReason else failureReason ?: current.failureReason,
        )
        updateLatestState(sessionId)
        latestStatus = status
        latestCurrentStepIndex = currentStepIndex ?: current.currentStepIndex
        latestFailureReason = if (replaceFailureReason) failureReason else failureReason ?: current.failureReason
    }

    override suspend fun appendActionLog(
        sessionId: Long,
        stepIndex: Int,
        actionType: String,
        selectorSummary: String?,
        resultStatus: String,
        detail: String,
        observedPackage: String?,
    ) {
        actionLogs += ActionLogRecord(
            stepIndex = stepIndex,
            actionType = actionType,
            resultStatus = resultStatus,
            detail = detail,
            observedPackage = observedPackage,
        )
    }

    override suspend fun appMemoriesForPackages(
        packageNames: List<String>,
        limit: Int,
    ): List<AgentAppMemory> =
        appMemories
            .filter { it.packageName in packageNames }
            .take(limit)

    override suspend fun rememberAppOutcome(
        packageName: String,
        appName: String,
        goal: String,
        outcome: String,
        note: String,
        source: String,
    ) {
        appMemories += AgentAppMemory(
            packageName = packageName,
            appName = appName,
            outcome = outcome,
            note = note,
        )
    }

    override suspend fun skillMemoriesForPackages(
        packageNames: List<String>,
        limit: Int,
    ): List<AgentSkillMemory> =
        skillMemories
            .filter { it.packageName in packageNames }
            .take(limit)

    override suspend fun rememberSkillOutcome(
        packageName: String,
        appName: String,
        goal: String,
        outcome: String,
        note: String,
        source: String,
    ) {
        skillMemories += AgentSkillMemory(
            packageName = packageName,
            appName = appName,
            outcome = outcome,
            note = note,
        )
    }

    override suspend fun latestRecoverableSession(): RecoverableSession? = null

    override suspend fun ensureSeedSession() = Unit

    private fun updateLatestState(sessionId: Long) {
        val session = sessions.getValue(sessionId)
        latestAgentState.value = AgentSessionState(
            sessionId = session.id,
            status = session.status,
            currentStepIndex = session.currentStepIndex,
            lastObservedPackage = session.lastObservedPackage,
            failureReason = session.failureReason,
            requiresUserAction = session.confirmGateState == "awaiting_confirmation",
            confirmGateState = session.confirmGateState,
            planJson = session.validatedPlanJson,
            goal = session.userGoal,
        )
    }
}

private fun FakeSessionRepository.nonPlanActionLogs(): List<ActionLogRecord> =
    actionLogs.filter { it.actionType != "PlanDraft" }

private data class SessionRecord(
    val id: Long,
    val title: String,
    val appName: String,
    val status: String,
    val userGoal: String,
    val plannerInputSummary: String,
    val validatedPlanJson: String,
    val currentStepIndex: Int,
    val lastObservedPackage: String?,
    val lastSelectorFailureReason: String?,
    val confirmGateState: String,
    val failureReason: String?,
)

private data class ActionLogRecord(
    val stepIndex: Int,
    val actionType: String,
    val resultStatus: String,
    val detail: String,
    val observedPackage: String?,
)
