# Mobile-Use Port Absorption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` for sequential execution. Use `superpowers:subagent-driven-development` only when splitting implementation into independent file sets. Follow each checkbox in order and mark it complete only after the listed QA passes.

**Goal:** Absorb the useful automation behavior from `minitap-ai/mobile-use` into the Android app as native Kotlin code so the app agent can understand screens, plan subgoals, execute external apps through `AccessibilityService`, recover from failures, and record evidence without runtime ADB, Python, UIAutomator2, LangGraph, or host processes.

**Architecture:** Keep product runtime Android-native. Port mobile-use concepts, not its host stack: `Planner -> Contextor -> Cortex -> Executor -> Summarizer/Convergence` becomes a Kotlin foreground-service state machine; mobile-use `Target` fallback becomes deterministic selector scoring; `get_screen_data()` becomes an in-app `ObservationFrame` from accessibility tree plus optional screenshot; mobile-use executor feedback becomes persisted action attempts and replanning input.

**Tech Stack:** Kotlin, Android AccessibilityService, Foreground Service, Compose, Room, DataStore, existing `core-dsl`, `core-runner`, `runtime-litertlm`, `skill-registry`, OpenRouter runtime currently set to `google/gemma-4-26b-a4b-it`, optional future LiteRT-LM local runtime.

**Non-goals:** Do not embed Python, LangGraph, ADB, `uiautomator2`, iOS/WDA/IDB, Limrun cloud, mobile-use telemetry, or host-side screen recording into product runtime. ADB/mobile-use may stay only as QA harnesses.

**Current Constraints:** This checkout has no `.git` directory, so this plan uses checkpoint artifacts instead of required commits. Disk is tight at about `9.5GiB` free; every emulator/build milestone must run `scripts/check_capacity.sh` first and avoid extra AVD/system image downloads.

---

## Capability Map

Port these mobile-use features:

- Multi-stage agent graph: planner subgoals, contextor observation, cortex decision, executor tool call, summarizer/history compaction, convergence/replan.
- Observation frame: UI hierarchy, screenshot metadata, focused app/package, screen size, device date.
- Target fallback: bounds first, then resource id, then text, with index disambiguation and attempt logs.
- Input reliability: focus target, optional clear, type text, refresh observation, verify field value.
- Swipe reliability: coordinate and percentage swipe variants.
- Executor feedback: structured tool messages/action attempts fed back into the next planning loop.
- App lock/package context: expected app with allowed package alias and relaunch/recovery policy.
- Trace artifacts: screenshot/tree/action/result logs per scenario.

Reject these runtime dependencies:

- `adbutils`, `uiautomator2`, `uiautomator dump`, `LangGraph`, host Python controller, WDA/IDB, external video analyzer.

---

## Test Plan

### Objective
Verify that mobile-use-like behavior is implemented inside the Android app runtime and that the app can use other apps through accessibility-driven actions with traceable model planning.

### Prerequisites
- Android emulator or physical device connected.
- Accessibility service manually enabled once.
- OpenRouter key available in local environment or app config, without logging the secret.
- At least `10GiB` free for normal incremental build; `20GiB` preferred before emulator-heavy QA.

### Test Cases
1. `ObservationFrame`: Run app on emulator, open Settings, start goal "Search Settings for wifi" -> expected observation contains foreground package, screen size, node list, and screenshot metadata when screenshot capture is available -> verify session trace row and artifact JSON.
2. `Subgoal graph`: Prompt Chrome query goal -> expected planner creates subgoals, contextor observes current screen, cortex emits strict JSON decisions, executor runs only compiled DSL -> verify trace includes all stages.
3. `Target fallback`: Use a screen where a node has bounds/resource/text -> expected tap attempts are recorded in fallback order and success stops further attempts -> verify action result log.
4. `Input verification`: Focus Settings search and type `wifi` -> expected post-action observation verifies editable node text contains `wifi` -> verify action result log.
5. `Recovery`: Simulate selector miss on Files or Chrome first-run -> expected one reobserve, one alternate selector, one scroll retry, then safe fail or continue -> verify no infinite loop.
6. `Safety`: Prompt send/share/delete/pay/install/permission action -> expected `confirm_user` gate, no commit action before user confirm -> verify plan/action trace.

### Commands
```bash
scripts/check_capacity.sh
./gradlew --no-configuration-cache :core-dsl:test :core-runner:test :runtime-litertlm:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
scripts/qa_milestone.sh mobile-use-port-settings-wifi
scripts/qa_milestone.sh mobile-use-port-chrome-query
scripts/qa_milestone.sh mobile-use-port-files-search
```

### Success Criteria
All unit tests pass, `:app:assembleDebug` passes, every milestone QA has an artifact directory under `qa_artifacts/`, product runtime contains no ADB/mobile-use/Python dependency, and high-risk actions always stop at `confirm_user`.

---

## Phase 1: Native Port Boundary And Capability Manifest

- [ ] Create `docs/mobile-use-native-port-map.md` with a concrete port/reject matrix.

  QA: Review the document and verify every imported concept maps to an Android-native module or is explicitly rejected.

  Content:
  ```markdown
  # Mobile-Use Native Port Map

  ## Product Runtime Rule
  Runtime execution must stay inside the Android app process and Android framework services. No ADB, Python, UIAutomator2, LangGraph, or host process is allowed after install and one-time accessibility enablement.

  ## Ported Concepts
  | mobile-use concept | Android-native target |
  |---|---|
  | PlannerNode subgoals | core-runner AgentGraphState.subgoals |
  | ContextorNode get_screen_data | app observation ObservationProvider |
  | CortexNode structured decisions | runtime-litertlm strict DecisionDraft JSON |
  | ExecutorNode tool messages | core-runner ActionAttemptLog |
  | Target(resource_id,text,bounds,index) | core-dsl ActionTarget |
  | screenshot + UI hierarchy | ObservationFrame tree + optional screenshot |
  | convergence/replan | AgentOrchestrator bounded recovery |

  ## Rejected Runtime Dependencies
  ADB, uiautomator2, adbutils, Python, LangGraph, WDA, IDB, Limrun cloud, mobile-use telemetry.
  ```

- [ ] Add a checkpoint note at `docs/superpowers/plans/2026-04-24-mobile-use-port-absorption-progress.md`.

  QA: `test -s docs/superpowers/plans/2026-04-24-mobile-use-port-absorption-progress.md`.

  Command:
  ```bash
  printf '%s\n' 'Phase 1 checkpoint: port boundary documented.' >> docs/superpowers/plans/2026-04-24-mobile-use-port-absorption-progress.md
  ```

---

## Phase 2: ObservationFrame From Accessibility Tree Plus Optional Screenshot

- [ ] Add `core-runner/src/main/kotlin/com/guribbong/phoneappagent/runner/ObservationModels.kt`.

  QA: `./gradlew --no-configuration-cache :core-runner:test`.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runner

  import kotlinx.datetime.Instant

  data class ObservationFrame(
      val goal: String,
      val foregroundPackage: String?,
      val lastExternalForegroundPackage: String?,
      val focusedAppInfo: String?,
      val screenWidth: Int?,
      val screenHeight: Int?,
      val deviceDate: String?,
      val visibleNodes: List<ObservedNode>,
      val screenshot: ScreenshotData?,
      val capturedAt: Instant,
  )

  data class ObservedNode(
      val text: String?,
      val contentDescription: String?,
      val resourceId: String?,
      val className: String?,
      val packageName: String?,
      val editable: Boolean,
      val clickable: Boolean,
      val bounds: NodeBounds?,
      val indexPath: List<Int>,
  )

  data class NodeBounds(
      val left: Int,
      val top: Int,
      val right: Int,
      val bottom: Int,
  ) {
      val centerX: Int get() = (left + right) / 2
      val centerY: Int get() = (top + bottom) / 2
      val width: Int get() = right - left
      val height: Int get() = bottom - top
  }

  data class ScreenshotData(
      val base64Png: String,
      val width: Int,
      val height: Int,
      val capturedAt: Instant,
      val source: ScreenshotSource,
  )

  enum class ScreenshotSource {
      ACCESSIBILITY_TAKE_SCREENSHOT,
      MEDIA_PROJECTION,
      UNAVAILABLE,
  }
  ```

- [ ] Add `app/src/main/java/com/guribbong/phoneappagent/observation/ObservationProvider.kt`.

  QA: Instrument a Settings run and verify an `ObservationFrame` appears in session logs with node count and package.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.observation

  import com.guribbong.phoneappagent.accessibility.AgentAccessibilityService
  import com.guribbong.phoneappagent.runner.ObservationFrame
  import com.guribbong.phoneappagent.runner.ObservedNode
  import kotlinx.datetime.Clock

  interface ObservationProvider {
      suspend fun observe(goal: String): ObservationFrame
  }

  class AccessibilityObservationProvider(
      private val accessibilityServiceBridge: AccessibilityServiceBridge,
      private val screenshotProvider: ScreenshotProvider,
  ) : ObservationProvider {
      override suspend fun observe(goal: String): ObservationFrame {
          val snapshot = accessibilityServiceBridge.currentSnapshot()
          val screenshot = screenshotProvider.captureOrNull()
          return ObservationFrame(
              goal = goal,
              foregroundPackage = snapshot.foregroundPackage,
              lastExternalForegroundPackage = snapshot.lastExternalForegroundPackage,
              focusedAppInfo = snapshot.foregroundPackage,
              screenWidth = snapshot.screenWidth,
              screenHeight = snapshot.screenHeight,
              deviceDate = snapshot.deviceDate,
              visibleNodes = snapshot.nodes.map { it.toObservedNode() },
              screenshot = screenshot,
              capturedAt = Clock.System.now(),
          )
      }
  }

  private fun AccessibilityNodeSnapshot.toObservedNode(): ObservedNode = ObservedNode(
      text = text,
      contentDescription = contentDescription,
      resourceId = resourceId,
      className = className,
      packageName = packageName,
      editable = editable,
      clickable = clickable,
      bounds = bounds,
      indexPath = indexPath,
  )
  ```

- [ ] Add `app/src/main/java/com/guribbong/phoneappagent/observation/ScreenshotProvider.kt`.

  QA: If screenshot capture is unavailable on the test API/device, verify `screenshot=null` and planner still runs from tree.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.observation

  import android.os.Build
  import com.guribbong.phoneappagent.runner.ScreenshotData

  interface ScreenshotProvider {
      suspend fun captureOrNull(): ScreenshotData?
  }

  class AccessibilityScreenshotProvider(
      private val serviceProvider: () -> AgentAccessibilityService?,
  ) : ScreenshotProvider {
      override suspend fun captureOrNull(): ScreenshotData? {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
          val service = serviceProvider() ?: return null
          return service.captureScreenshotDataOrNull()
      }
  }
  ```

---

## Phase 3: Extend PlannerInput Without Making Screenshot Mandatory

- [ ] Extend `core-runner/src/main/kotlin/com/guribbong/phoneappagent/runner/RunnerModels.kt` `PlannerInput` with observation fields.

  QA: Unit test serialization and ensure screenshot base64 is redacted from normal logs.

  Code:
  ```kotlin
  data class PlannerInput(
      val goal: String,
      val foregroundPackage: String?,
      val lastExternalForegroundPackage: String?,
      val serializedNodeTree: String,
      val recentActionHistory: List<String>,
      val riskHints: List<String>,
      val screenWidth: Int? = null,
      val screenHeight: Int? = null,
      val focusedAppInfo: String? = null,
      val deviceDate: String? = null,
      val screenshotBase64Png: String? = null,
      val screenshotForQaOnly: Boolean = true,
      val subgoalPlan: List<AgentSubgoal> = emptyList(),
      val scratchpad: AgentScratchpad = AgentScratchpad.Empty,
  )
  ```

- [ ] Add `runtime-litertlm/src/main/kotlin/com/guribbong/phoneappagent/runtime/RuntimeVisionPolicy.kt`.

  QA: Verify current `google/gemma-4-26b-a4b-it` prompt path does not send screenshot image content unless explicitly enabled.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runtime

  data class RuntimeVisionPolicy(
      val sendScreenshotsToPlanner: Boolean,
      val maxScreenshotBytes: Int,
  ) {
      companion object {
          val TreeOnly = RuntimeVisionPolicy(
              sendScreenshotsToPlanner = false,
              maxScreenshotBytes = 0,
          )
      }
  }
  ```

- [ ] Modify `OpenRouterRuntime` prompt building so it includes screenshot metadata by default and image content only when `RuntimeVisionPolicy.sendScreenshotsToPlanner` is true.

  QA: Add a unit test asserting the prompt contains `screenshot_available=true` and does not contain raw base64 under default policy.

  Code pattern:
  ```kotlin
  private fun PlannerInput.toObservationPrompt(policy: RuntimeVisionPolicy): String = buildString {
      appendLine("Foreground package: ${foregroundPackage.orEmpty()}")
      appendLine("Focused app: ${focusedAppInfo.orEmpty()}")
      appendLine("Screen size: ${screenWidth ?: -1}x${screenHeight ?: -1}")
      appendLine("Screenshot available: ${screenshotBase64Png != null}")
      if (policy.sendScreenshotsToPlanner && screenshotBase64Png != null) {
          appendLine("Screenshot base64 png:")
          appendLine(screenshotBase64Png.take(policy.maxScreenshotBytes))
      }
      appendLine("Accessibility tree:")
      appendLine(serializedNodeTree)
  }
  ```

---

## Phase 4: Mobile-Use-Style Graph State In Core Runner

- [ ] Add `core-runner/src/main/kotlin/com/guribbong/phoneappagent/runner/AgentGraphState.kt`.

  QA: Unit test subgoal status transitions and convergence decisions.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runner

  import kotlinx.datetime.Instant

  data class AgentGraphState(
      val sessionId: Long,
      val initialGoal: String,
      val subgoals: List<AgentSubgoal> = emptyList(),
      val latestObservation: ObservationFrame? = null,
      val structuredDecision: DecisionDraft? = null,
      val actionAttempts: List<ActionAttemptLog> = emptyList(),
      val agentThoughts: List<String> = emptyList(),
      val scratchpad: AgentScratchpad = AgentScratchpad.Empty,
  ) {
      val currentSubgoal: AgentSubgoal? get() = subgoals.firstOrNull { it.status == SubgoalStatus.PENDING }
          ?: subgoals.firstOrNull { it.status == SubgoalStatus.NOT_STARTED }
  }

  data class AgentSubgoal(
      val id: String,
      val description: String,
      val status: SubgoalStatus,
      val completionReason: String? = null,
      val startedAt: Instant? = null,
      val endedAt: Instant? = null,
  )

  enum class SubgoalStatus {
      NOT_STARTED,
      PENDING,
      SUCCESS,
      FAILURE,
  }

  data class DecisionDraft(
      val decisionsReason: String,
      val actions: List<AgentActionDraft>,
      val completeSubgoalIds: List<String>,
  )

  data class AgentScratchpad(
      val entries: List<String>,
  ) {
      companion object {
          val Empty = AgentScratchpad(emptyList())
      }
  }
  ```

- [ ] Add `core-runner/src/main/kotlin/com/guribbong/phoneappagent/runner/ConvergencePolicy.kt`.

  QA: Unit test that failed subgoal triggers replan once and repeated failure ends safely.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runner

  class ConvergencePolicy(
      private val maxReplansPerSubgoal: Int = 1,
  ) {
      fun next(state: AgentGraphState): ConvergenceDecision {
          if (state.subgoals.isNotEmpty() && state.subgoals.all { it.status == SubgoalStatus.SUCCESS }) {
              return ConvergenceDecision.Complete
          }
          val failed = state.subgoals.firstOrNull { it.status == SubgoalStatus.FAILURE }
          if (failed != null) {
              val replans = state.actionAttempts.count { it.subgoalId == failed.id && it.phase == "replan" }
              return if (replans < maxReplansPerSubgoal) ConvergenceDecision.Replan(failed.id) else ConvergenceDecision.Fail(failed.completionReason ?: "Subgoal failed")
          }
          return ConvergenceDecision.Continue
      }
  }

  sealed interface ConvergenceDecision {
      data object Continue : ConvergenceDecision
      data object Complete : ConvergenceDecision
      data class Replan(val subgoalId: String) : ConvergenceDecision
      data class Fail(val reason: String) : ConvergenceDecision
  }
  ```

---

## Phase 5: Contextor And Cortex Prompt Split

- [ ] Add `runtime-litertlm/src/main/kotlin/com/guribbong/phoneappagent/runtime/ContextorPrompt.kt`.

  QA: Unit test prompt includes package, app lock context, visible labels, and executor feedback without raw secret/config values.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runtime

  import com.guribbong.phoneappagent.runner.AgentGraphState

  object ContextorPrompt {
      fun build(state: AgentGraphState): String = buildString {
          val observation = state.latestObservation
          appendLine("Role: Contextor. Analyze the current Android screen.")
          appendLine("Goal: ${state.initialGoal}")
          appendLine("Foreground package: ${observation?.foregroundPackage.orEmpty()}")
          appendLine("Focused app: ${observation?.focusedAppInfo.orEmpty()}")
          appendLine("Current subgoal: ${state.currentSubgoal?.description.orEmpty()}")
          appendLine("Recent executor feedback:")
          state.actionAttempts.takeLast(8).forEach { appendLine("- ${it.summary}") }
          appendLine("Visible node labels:")
          observation?.visibleNodes.orEmpty().take(80).forEach {
              appendLine("- text=${it.text.orEmpty()} desc=${it.contentDescription.orEmpty()} id=${it.resourceId.orEmpty()} editable=${it.editable} clickable=${it.clickable}")
          }
      }
  }
  ```

- [ ] Add `runtime-litertlm/src/main/kotlin/com/guribbong/phoneappagent/runtime/CortexPrompt.kt`.

  QA: Unit test prompt requires strict JSON and forbids direct taps outside DSL.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runtime

  import com.guribbong.phoneappagent.runner.AgentGraphState

  object CortexPrompt {
      fun build(state: AgentGraphState): String = buildString {
          appendLine("Role: Cortex. Convert screen understanding into strict JSON actions.")
          appendLine("You must output only JSON matching DecisionDraft.")
          appendLine("Do not output prose, markdown, or raw coordinate instructions.")
          appendLine("Allowed actions: launch_app, wait_for_app, wait_for_node, tap, input_text, clear_text, scroll, press_global, assert_visible, confirm_user, stop.")
          appendLine("High-risk actions send/pay/delete/post/share/install/permission/account-change/upload must use confirm_user before any commit action.")
          appendLine("Current goal: ${state.initialGoal}")
          appendLine("Current subgoal: ${state.currentSubgoal?.description.orEmpty()}")
          appendLine("Contextor thoughts:")
          state.agentThoughts.takeLast(6).forEach { appendLine("- $it") }
      }
  }
  ```

- [ ] Modify `AgentOrchestrator` loop from single planner pass into bounded stages: `planner -> observe/contextor -> cortex -> validate -> execute -> summarize -> convergence`.

  QA: Run existing Settings wifi QA and verify trace records every stage.

  Implementation rule:
  ```kotlin
  while (sessionActive && iteration < maxIterations) {
      ensureSubgoals()
      val observation = observationProvider.observe(goal)
      val context = runtime.contextualize(graphState.withObservation(observation))
      val decision = runtime.decide(graphState.withThought(context))
      val plan = decisionValidator.compile(decision)
      val result = executor.execute(plan)
      graphState = summarizer.apply(graphState, result)
      when (convergencePolicy.next(graphState)) {
          ConvergenceDecision.Continue -> continue
          ConvergenceDecision.Complete -> completeSession()
          is ConvergenceDecision.Replan -> replan()
          is ConvergenceDecision.Fail -> failSession()
      }
  }
  ```

---

## Phase 6: Target Fallback And Attempt Logs

- [ ] Extend `core-dsl/src/main/kotlin/com/guribbong/phoneappagent/dsl/ActionModels.kt` with mobile-use-like target fields.

  QA: JSON parser accepts `resourceId`, `resourceIdIndex`, `text`, `textIndex`, `bounds`, and rejects empty target for tap/input.

  Code:
  ```kotlin
  data class ActionTarget(
      val resourceId: String? = null,
      val resourceIdIndex: Int? = null,
      val text: String? = null,
      val textIndex: Int? = null,
      val contentDescription: String? = null,
      val className: String? = null,
      val editable: Boolean? = null,
      val clickable: Boolean? = null,
      val packageName: String? = null,
      val indexPath: List<Int>? = null,
      val bounds: BoundsHint? = null,
      val nearText: String? = null,
  )

  data class BoundsHint(
      val left: Int,
      val top: Int,
      val right: Int,
      val bottom: Int,
  )
  ```

- [ ] Add `core-runner/src/main/kotlin/com/guribbong/phoneappagent/runner/ActionAttemptLog.kt`.

  QA: Unit test logs fallback attempts in order and preserves failure reasons.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runner

  data class ActionAttemptLog(
      val sessionId: Long,
      val subgoalId: String?,
      val actionIndex: Int,
      val phase: String,
      val strategy: String,
      val summary: String,
      val success: Boolean,
      val failureReason: String?,
  )
  ```

- [ ] Modify `AndroidAccessibilityDriver.tap(target)` fallback order in `AgentExecutors.kt`.

  QA: In a fake-node unit test, verify attempt order is `bounds -> resourceId -> text -> contentDescription -> selectorScore`.

  Code pattern:
  ```kotlin
  suspend fun tapTarget(target: ActionTarget): ActionResult {
      val attempts = mutableListOf<ActionAttemptLog>()
      target.bounds?.let {
          val result = gestureTap(it.centerX, it.centerY)
          attempts += result.toAttempt("bounds")
          if (result.success) return result.withAttempts(attempts)
      }
      target.resourceId?.let {
          val result = tapByResourceId(it, target.resourceIdIndex ?: 0)
          attempts += result.toAttempt("resourceId")
          if (result.success) return result.withAttempts(attempts)
      }
      target.text?.let {
          val result = tapByText(it, target.textIndex ?: 0)
          attempts += result.toAttempt("text")
          if (result.success) return result.withAttempts(attempts)
      }
      val scored = tapBySelectorScore(target)
      attempts += scored.toAttempt("selectorScore")
      return scored.withAttempts(attempts)
  }
  ```

---

## Phase 7: Focus-And-Input Text Tool Parity

- [ ] Add `InputTextExecutor` behavior matching mobile-use focus/input/verify flow.

  QA: Settings search field receives `wifi`; refreshed observation confirms an editable node contains `wifi`.

  Code pattern:
  ```kotlin
  class InputTextExecutor(
      private val driver: AndroidAccessibilityDriver,
      private val observationProvider: ObservationProvider,
  ) {
      suspend fun focusAndInput(goal: String, target: ActionTarget, text: String, clearFirst: Boolean): ActionResult {
          val focus = driver.tapTarget(target)
          if (!focus.success) return focus
          if (clearFirst) driver.clearFocusedText()
          val typed = driver.inputText(text)
          if (!typed.success) return typed
          val observed = observationProvider.observe(goal)
          val verified = observed.visibleNodes.any { node ->
              node.editable && node.text.orEmpty().contains(text, ignoreCase = true)
          }
          return typed.copy(
              success = verified,
              failureReason = if (verified) null else "Typed text was not visible in refreshed editable node",
          )
      }
  }
  ```

- [ ] Persist post-action verification text in action logs.

  QA: Inspect session DB/action log after Settings QA and verify `postObservedText` or equivalent contains the visible typed text.

---

## Phase 8: Swipe Coordinates And Percentages

- [ ] Extend DSL scroll action to support absolute coordinates and percentage coordinates.

  QA: Unit test converts `startXPercent=0.5`, `startYPercent=0.8`, `endXPercent=0.5`, `endYPercent=0.2` using `screenWidth`/`screenHeight`.

  Code:
  ```kotlin
  data class SwipeSpec(
      val direction: ScrollDirection? = null,
      val startX: Int? = null,
      val startY: Int? = null,
      val endX: Int? = null,
      val endY: Int? = null,
      val startXPercent: Float? = null,
      val startYPercent: Float? = null,
      val endXPercent: Float? = null,
      val endYPercent: Float? = null,
      val durationMs: Long = 350L,
  )
  ```

- [ ] Implement `SwipeSpec.toCoordinates(screenWidth, screenHeight)`.

  QA: Unit test rejects percentages outside `0.0..1.0` and missing screen size.

---

## Phase 9: Dialog, App-Lock, And Recovery Policies

- [ ] Add `core-runner/src/main/kotlin/com/guribbong/phoneappagent/runner/SystemDialogDetector.kt`.

  QA: Unit tests detect ANR, permission dialogs, and generic system alert nodes.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.runner

  class SystemDialogDetector {
      fun detect(observation: ObservationFrame): SystemDialog? {
          val labels = observation.visibleNodes.mapNotNull { it.text ?: it.contentDescription }
          if (labels.any { it.contains("isn't responding", ignoreCase = true) }) {
              return SystemDialog.Anr
          }
          if (labels.any { it.contains("Allow", ignoreCase = true) || it.contains("permission", ignoreCase = true) }) {
              return SystemDialog.Permission
          }
          return null
      }
  }

  sealed interface SystemDialog {
      data object Anr : SystemDialog
      data object Permission : SystemDialog
  }
  ```

- [ ] Add recovery policy to orchestrator: reobserve once, alternate selector once, scroll once, then safe fail.

  QA: Force a selector miss and verify the trace has exactly those retries and then stops.

  Code rule:
  ```kotlin
  data class RecoveryBudget(
      val reobserveRemaining: Int = 1,
      val alternateSelectorRemaining: Int = 1,
      val scrollRemaining: Int = 1,
  )
  ```

- [ ] Add package alias/app-lock verification using existing `CanonicalPackageResolver`.

  QA: `com.android.settings` goal remains valid when foreground package is `com.google.android.settings.intelligence`.

---

## Phase 10: Scratchpad Memory And Skill Guidance

- [ ] Add Room-backed scratchpad entries for app usage lessons.

  QA: After a successful Settings run, a later Settings run includes prior app-use guidance in planner input.

  Schema:
  ```kotlin
  @Entity(tableName = "agent_scratchpad")
  data class AgentScratchpadEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val packageName: String,
      val goalFingerprint: String,
      val lesson: String,
      val successCount: Int,
      val failureCount: Int,
      val updatedAtEpochMs: Long,
  )
  ```

- [ ] Add `SkillGuidanceCompiler` support for mobile-use target hints.

  QA: A skill can specify `resourceId`, `text`, `bounds`, and `fallbackText`; compiled planner input includes the hints but current tree still wins.

  Skill example:
  ```json
  {
    "packageName": "com.android.settings",
    "goalPattern": "wifi|network",
    "targets": [
      {
        "name": "settings_search",
        "resourceId": "com.android.settings:id/search_action_bar",
        "text": "Search settings"
      }
    ]
  }
  ```

---

## Phase 11: Trace Artifacts And QA Evidence

- [ ] Add `app/src/main/java/com/guribbong/phoneappagent/trace/TraceRecorder.kt`.

  QA: Every QA run writes `result.json`, `planner_trace.jsonl`, `actions.jsonl`, and screenshot files when screenshot capture is available.

  Code:
  ```kotlin
  package com.guribbong.phoneappagent.trace

  import com.guribbong.phoneappagent.runner.ActionAttemptLog
  import com.guribbong.phoneappagent.runner.AgentGraphState
  import com.guribbong.phoneappagent.runner.ObservationFrame

  interface TraceRecorder {
      suspend fun recordObservation(state: AgentGraphState, observation: ObservationFrame)
      suspend fun recordAttempts(state: AgentGraphState, attempts: List<ActionAttemptLog>)
      suspend fun recordDecision(state: AgentGraphState)
      suspend fun finish(state: AgentGraphState, status: String, reason: String?)
  }
  ```

- [ ] Add an in-app export/debug action that copies trace artifacts from app-private storage to shareable debug storage when user explicitly requests it.

  QA: Export one Settings run and verify files exist under a timestamped artifact directory.

---

## Phase 12: QA Harnesses Remain External, Runtime Stays Internal

- [ ] Add `scripts/qa_mobile_use_port.sh` to run product QA scenarios and collect artifacts.

  QA: Run at least Settings wifi and Chrome query after build. If Chrome ANR occurs, trace must classify it as ANR/system dialog rather than generic selector failure.

  Script:
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail

  scripts/check_capacity.sh
  ./gradlew --no-configuration-cache :app:assembleDebug

  for scenario in settings-wifi chrome-query files-search contacts-search safety-send-confirm; do
    scripts/qa_milestone.sh "mobile-use-port-${scenario}"
  done
  ```

- [ ] Add CI/static guard that product source does not import mobile-use runtime dependencies.

  QA: Guard fails if `adbutils`, `uiautomator2`, `langgraph`, or `.mobile-use-src` is referenced from `app`, `core-*`, `runtime-*`, `driver-*`, `skill-registry`, or `overlay-ui`.

  Command:
  ```bash
  ! rg -n "adbutils|uiautomator2|langgraph|\\.mobile-use-src|minitap\\.mobile_use" app core-* runtime-* driver-* skill-registry overlay-ui
  ```

---

## Phase 13: Final Acceptance Pass

- [ ] Run full local verification.

  QA command:
  ```bash
  scripts/check_capacity.sh
  ./gradlew --no-configuration-cache :core-dsl:test :core-runner:test :runtime-litertlm:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
  ```

- [ ] Run milestone device/emulator QA.

  QA command:
  ```bash
  scripts/qa_mobile_use_port.sh
  ```

- [ ] Manually inspect artifact outputs.

  QA:
  ```bash
  find qa_artifacts -maxdepth 2 -type f \( -name 'result.json' -o -name 'planner_trace.jsonl' -o -name 'actions.jsonl' \) | tail -30
  ```

- [ ] Confirm runtime boundary.

  QA:
  ```bash
  ! rg -n "adbutils|uiautomator2|langgraph|\\.mobile-use-src|minitap\\.mobile_use" app core-* runtime-* driver-* skill-registry overlay-ui
  ```

- [ ] Update final progress checkpoint.

  QA:
  ```bash
  printf '%s\n' 'Final checkpoint: mobile-use native absorption implemented and QA artifacts inspected.' >> docs/superpowers/plans/2026-04-24-mobile-use-port-absorption-progress.md
  tail -20 docs/superpowers/plans/2026-04-24-mobile-use-port-absorption-progress.md
  ```

---

## Implementation Order

1. Phase 1 creates the boundary so external runtime dependencies do not leak in.
2. Phases 2-3 create richer observation and planner input.
3. Phases 4-5 split the agent loop into mobile-use-like reasoning stages.
4. Phases 6-8 port tool execution reliability.
5. Phases 9-10 add recovery, app context, memory, and skills.
6. Phases 11-13 add evidence capture and final QA gates.

Do not proceed to the next phase until the phase QA has a recorded pass, fail-with-cause, or explicit blocker in the progress checkpoint.
