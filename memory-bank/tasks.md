# Tasks

## Current Plan: Global Inner Blur Wave Agent Overlay

### Scope And Goal

Add a global animated overlay, inspired by the provided React/Figma inner blur wave panel, that appears while the agent is commanded and operating across apps, then slowly fades out when the job reaches a terminal state.

This must be a native Android overlay. Do not add React, Tailwind, WebView, or web runtime dependencies.

### Affected Files Or Systems

- `app/src/main/java/com/guribbong/phoneappagent/accessibility/AccessibilityOverlayController.kt`
- `app/src/main/java/com/guribbong/phoneappagent/accessibility/AccessibilityCommandBridge.kt`
- `app/src/main/java/com/guribbong/phoneappagent/agent/AgentOrchestrator.kt`
- `memory-bank/progress.md`

### Implementation Plan

1. Keep `TYPE_ACCESSIBILITY_OVERLAY` as the global render surface.
2. Add a custom `InnerWaveBackdropView` behind the existing active pill and target highlight:
   - translucent white/blue edge glow
   - soft radial blue fields around the edges
   - animated blob movement and pulse using a native animator
   - center kept visually clear so the controlled app remains readable
3. Change overlay dismissal from immediate detach to slow fade-out:
   - `AccessibilityOverlayBridge.hide()` still marks the overlay inactive.
   - `AccessibilityOverlayController.render()` fades the root alpha down, then detaches.
   - Any new `show()` cancels fade-out and restores full alpha.
4. Show the overlay earlier in the agent lifecycle:
   - preparing/probing runtime
   - planning
   - executing
   - waiting for confirmation
   - recovering
5. Keep target highlight behavior unchanged for concrete tap/input/assert actions.
6. Run capacity check, compile, focused unit tests, build, runtime-boundary check, OpenRouter-only check, and a connected-device smoke if disk allows.

### Success Criteria

- Global overlay appears when an agent goal enters preparing/planning/executing/recovering/confirmation phases.
- Overlay contains an animated inner blur wave visual layer and preserves the existing active pill and target highlight.
- Overlay does not block touch input.
- Terminal completion/failure/stop fades out slowly instead of disappearing instantly.
- Existing safety and OpenRouter-only checks still pass.
- Debug APK builds.

### Verification Plan

```bash
bash scripts/check_capacity.sh
./gradlew --no-configuration-cache :app:compileDebugKotlin
./gradlew --no-configuration-cache :app:testDebugUnitTest --tests 'com.guribbong.phoneappagent.agent.AgentOrchestratorConfirmFlowTest'
./gradlew --no-configuration-cache :app:assembleDebug
bash scripts/check_runtime_boundary.sh
bash scripts/check_openrouter_only_runtime.sh
QA_POST_LAUNCH_WAIT_SEC=6 bash scripts/qa_milestone.sh inner-wave-global-overlay app/build/outputs/apk/debug/app-debug.apk
```

### Risks Or Blockers

- This machine has low disk headroom; emulator QA may be blocked.
- Accessibility overlay visual effects must stay light enough to avoid jank while another app is active.
- Android framework blur APIs are version-dependent, so the implementation should use Canvas gradients and alpha animation rather than relying on platform blur.
- The wave layer must not obscure app content or interfere with existing target bounds highlight.

### Creative Phase

Creative required: no. The user provided a concrete visual reference; implementation can map it directly to native Canvas gradients and animator-driven motion.

### Build Result

Implemented.

Key details:

- `AccessibilityOverlayController` now renders a native `InnerWaveBackdropView` behind the existing HUD pill and target highlight.
- Active runs show the overlay during preparing, planning, replanning, executing, confirmation, and recovery.
- `hide()` now drives a 2.4 second root alpha fade before the overlay detaches.
- Active overlay visibility is no longer suppressed by the stored idle overlay preference; the preference only affects inactive/idle rendering.
- The overlay remains `FLAG_NOT_TOUCHABLE` and uses the existing accessibility overlay window type.

Verification completed:

- Capacity check, Kotlin compile, focused orchestrator unit test, debug APK build, runtime-boundary check, and OpenRouter-only check passed.
- Connected-device QA captured the wave overlay in Settings at `qa_artifacts/inner-wave-global-overlay-settings-forced/screen_after_8s.png`.
- The QA run was stopped after capture to avoid leaving an active test agent session on the device.

## Current Plan: Toss-Inspired Confirmation And History Chat UI

### Scope And Goal

Refine the Mabi Agent app UI/UX around two user-visible control points:

- When the agent asks whether it should proceed, show a bottom sheet pop-up that clearly asks the user to proceed or stop.
- When the user taps a history item, open that session as a clean chat-style transcript of user requests, agent responses, and action/thought logs.

Use `https://github.com/toss/apps-in-toss-examples` as UI reference material, especially the compact list/header patterns, fixed bottom input, safe-area-aware mobile layout, strong primary action treatment, and restrained grey/blue palette. Do not add Toss, Granite, React Native, or web dependencies to this Android app; port only the product feel into Compose + Material 3.

### Requirements And Where Addressed

- Proceed confirmation bottom sheet:
  - Address in `app/src/main/java/com/guribbong/phoneappagent/ui/AppRoot.kt` by making the existing `RiskOptionPopup` the canonical `WAITING_FOR_CONFIRM` surface.
  - Keep execution wired through `MainViewModel.confirmExecution()` and `MainViewModel.stopExecution()`.
  - Keep `AgentRunPhase.WAITING_FOR_CONFIRM`, `confirm_user`, and the policy gate as the source of truth.
- Chat-style history drill-in:
  - Address in `data-history/src/main/java/com/guribbong/phoneappagent/data/history/ChatSessionDao.kt` and `SessionRepository.kt` by exposing a selected session transcript built from `chat_sessions` plus `agent_action_logs`.
  - Address in `app/src/main/java/com/guribbong/phoneappagent/ui/MainViewModel.kt` with selected-session state and `openSession(sessionId)` / `closeSession()` events.
  - Address in `AppRoot.kt` by making each history row clickable and rendering the selected transcript as user/agent/status bubbles.
- Toss-inspired UI/UX:
  - Address in `AppRoot.kt` and, if needed, `ui/theme/Color.kt` / `Theme.kt` with a restrained Toss-like system: light grey content surfaces, black high-emphasis text, blue primary actions, small row controls, compact spacing, stable safe-area bottom input, and no nested decorative cards.
- Runtime and safety boundaries:
  - No changes to OpenRouter-only runtime selection.
  - No production ADB, Appium, uiautomator, shell UI automation, or free-form tap execution.
  - High-risk actions remain blocked until explicit user confirmation.

### Named Resources And References

- Repo source of truth:
  - `AGENTS.md`
  - `AGENT.md`
  - `memory-bank/projectbrief.md`
  - `memory-bank/activeContext.md`
- Current UI and state files:
  - `app/src/main/java/com/guribbong/phoneappagent/ui/AppRoot.kt`
  - `app/src/main/java/com/guribbong/phoneappagent/ui/MainViewModel.kt`
  - `app/src/main/java/com/guribbong/phoneappagent/ui/RiskOptionUiModel.kt`
- Current history files:
  - `data-history/src/main/java/com/guribbong/phoneappagent/data/history/ChatSessionEntity.kt`
  - `data-history/src/main/java/com/guribbong/phoneappagent/data/history/ChatSessionDao.kt`
  - `data-history/src/main/java/com/guribbong/phoneappagent/data/history/SessionRepository.kt`
  - `data-history/src/main/java/com/guribbong/phoneappagent/data/history/AppDatabase.kt`
- Toss examples inspected:
  - `/tmp/apps-in-toss-examples/examples/pages/index.tsx`: `ListHeader` + `FlatList` + `ListRow` structure.
  - `/tmp/apps-in-toss-examples/examples/src/components/ExampleListItem.tsx`: list row with icon, label, and small weak actions.
  - `/tmp/apps-in-toss-examples/weekly-todo-react/src/components/BottomReply.tsx` and `BottomReply.module.css`: fixed bottom input, 48px controls, safe-area padding, blue primary action.
  - `/tmp/apps-in-toss-examples/weekly-todo-react/src/index.css`: grey/blue tokens and mobile viewport discipline.

### State Transitions And Data Flow

#### Proceed Confirmation

1. Runner emits or reaches `confirm_user`.
2. `AgentOrchestrator` moves to `AgentRunPhase.WAITING_FOR_CONFIRM`.
3. `MainViewModel.uiState` sets `needsConfirmation`, `showRiskProcessPopup`, `canConfirm`, `canStop`, `policyReason`, and `riskOptionUiModel`.
4. `AppRoot` renders a bottom sheet over the current chat shell.
5. User taps:
   - `Proceed` -> `MainViewModel.confirmExecution()` -> `AgentServiceController.confirm()` -> guarded continuation/replanning path.
   - `Stop` -> `MainViewModel.stopExecution()` -> `AgentServiceController.stop()` -> terminal stopped/failed state.
   - safe follow-up/refinement option -> starts a new explicit goal without continuing the risky action.

#### History Transcript

1. `ClaudeHistoryDrawer` becomes `MabiHistoryDrawer` and receives `onSessionClick`.
2. Tapping a session calls `MainViewModel.openSession(sessionId)`.
3. ViewModel loads or observes a `ChatTranscriptUiModel` from repository data:
   - user bubble from `ChatSessionEntity.userGoal`
   - agent summary bubble from `plannerInputSummary`, `validatedPlanJson` summary, status, and failure reason
   - thought/action bubbles from `AgentActionLogEntity` rows
   - confirmation/status system bubbles from `confirmGateState`, `status`, and `failureReason`
4. UI closes the drawer and renders a transcript view with chronological bubbles and a bottom composer.
5. Back/close returns to the live agent home without changing the active runner state.

### Step-By-Step Implementation Plan

1. Rename and simplify the current UI surface names.
   - Rename Claude-specific composables to Mabi-specific names.
   - Remove or isolate unused legacy card components in `AppRoot.kt` only if they interfere with the new layout.
   - Keep changes scoped; do not refactor runner/runtime code.
2. Make confirmation bottom sheet explicit and Toss-like.
   - Keep `BottomSheetScaffold`, but reduce the sheet into a direct question: what the agent wants to do, why confirmation is required, and the available choices.
   - Use a primary blue `Proceed` action, neutral secondary safe action when present, and subdued `Stop`.
   - Keep current domain-specific copy from `RiskOptionUiBuilder`, but make the sheet layout read as a decision prompt first.
   - Ensure the sheet works on small screens with bounded height and vertical scrolling.
3. Add selected history transcript data.
   - Add repository models such as `ChatTranscript`, `ChatTranscriptMessage`, and `ChatTranscriptRole`.
   - Add DAO access for one session and its action logs; existing tables are enough, so avoid schema changes unless absolutely necessary.
   - Map action logs into readable chat/status messages without exposing raw JSON by default.
4. Wire history click behavior.
   - Add selected session id/transcript state to `MainViewModel`.
   - Add `openSession(sessionId)` and `closeSession()`.
   - Pass click handlers from `AppRoot` to history rows.
5. Build chat-style transcript UI.
   - Render user messages right-aligned, agent/status messages left-aligned, and low-emphasis thought/action logs as compact neutral bubbles.
   - Keep metadata visible but concise: app name, status, updated time, step result, and observed package when useful.
   - Use stable bubble widths and text wrapping so long Korean or package names do not overflow.
6. Apply Toss-inspired visual polish.
   - Use light background/content bands, 12-20dp radii, 48dp tap targets, compact list rows, strong black text, grey dividers, and blue primary controls.
   - Keep the active agent badge/focus requirements visible.
   - Avoid adding a marketing-style hero page or decorative gradients.
7. Add focused tests.
   - Unit-test transcript mapping from session + action logs to chat bubbles.
   - Keep or update `RiskOptionUiBuilderTest` for bottom-sheet option copy.
   - Add ViewModel-level tests only if the existing test setup can instantiate the repository and state flow without large harness work.
8. Run milestone validation.
   - Check capacity first.
   - Run focused unit tests.
   - Build the debug APK.
   - Run runtime-boundary checks.
   - Run emulator/device UI QA before claiming the UI milestone complete.

### Success Criteria

- A `WAITING_FOR_CONFIRM` state always shows an in-app bottom sheet that asks whether the agent should proceed.
- The confirmation sheet has working `Proceed` and `Stop` paths and does not allow risky continuation through any other UI path.
- History rows are clickable.
- Clicking a history row opens a neat chat-style transcript with the original user goal, agent/session status, and ordered action/thought logs.
- The new UI visibly follows the Toss examples in density, list treatment, bottom input behavior, and restrained grey/blue styling while remaining native Compose.
- OpenRouter-only and runtime-boundary checks still pass.
- Debug APK builds.
- Actual rendered UI is verified on an emulator or physical device.

### Verification Plan

Run:

```bash
bash scripts/check_capacity.sh
./gradlew --no-configuration-cache :app:testDebugUnitTest --tests 'com.guribbong.phoneappagent.ui.RiskOptionUiBuilderTest'
./gradlew --no-configuration-cache :data-history:testDebugUnitTest :app:testDebugUnitTest
./gradlew --no-configuration-cache :app:assembleDebug
bash scripts/check_runtime_boundary.sh
bash scripts/check_openrouter_only_runtime.sh
```

Then run app QA:

```bash
bash scripts/qa_milestone.sh toss-inspired-confirm-history-ui
```

Manual QA checklist:

- Launch app and confirm the home screen does not have overlapping text on a small emulator.
- Trigger or simulate `WAITING_FOR_CONFIRM`; verify the bottom sheet asks to proceed and stop.
- Tap `Stop`; verify the agent stops and the sheet disappears.
- Trigger confirmation again; tap `Proceed`; verify it uses the existing guarded confirm path.
- Open history; tap at least one session; verify a chat transcript appears in chronological order.
- Rotate or test a narrow viewport if available; verify bottom input and sheet remain usable.

### Failure Behavior

- If transcript loading fails, show a non-destructive error bubble and keep the history drawer usable.
- If a session has no action logs, show the user goal and session status instead of an empty transcript.
- If confirmation state disappears while the sheet is open, dismiss the sheet and reflect the latest phase.
- If `canConfirm` is false, disable `Proceed`; do not hide `Stop` when stopping is allowed.
- If notification permission or foreground-service behavior differs by Android version, the in-app sheet remains the primary visible confirmation UI while notification actions remain fallback controls.

### Privacy And Security Considerations

- Do not display API keys, raw OpenRouter request headers, or raw model payloads in history.
- Redact or summarize long `validatedPlanJson` content unless the user explicitly opens a debug/details view.
- Keep package names and action logs visible enough for audit, but avoid exposing extracted private text from other apps beyond what the current app already stores.
- Preserve high-risk confirmation gates for send, pay, delete, post, share, account changes, permission grants, install, and upload.
- Do not add analytics, remote logging, or diagnostics upload as part of this UI task.

### Risks Or Blockers

- `AppRoot.kt` is already large; a small component split may be warranted during build to keep the change reviewable.
- Current history summaries do not expose action logs as a flow; adding transcript observation may require DAO additions or one-shot load on click.
- Existing memory-bank context says previous confirmation UI work was implemented, but `activeContext.md` still describes it as missing. Treat current source code as the authority during build.
- Emulator QA may be blocked by disk, AVD, or device availability; run capacity checks before Gradle/emulator work.

### Creative Phase

Creative complete: `memory-bank/creative/creative-toss-confirm-history-ui.md`.

Chosen approach: keep the current Compose app shell, make the confirmation gate a direct blocking bottom decision sheet, and use the history drawer as an index into a full-screen chat transcript. Port the Toss examples as a native visual language only: compact rows, light grey surfaces, strong black text, blue primary controls, 48dp tap targets, safe-area-aware bottom input, and restrained spacing.

Implementation guidance:

- Confirmation sheet hierarchy: direct question, policy reason, primary choices, then process details.
- Transcript route: full-screen overlay from history row click, not a small modal.
- Transcript bubbles: user right/blue, agent left/white, action/thought left/grey, status low-emphasis system row.
- Existing decorative glass can remain on the live home screen, but transcript and confirmation surfaces should be calmer and Toss-like.

### Next Phase

build complete

### Build Result

Implemented:

- Added a Memory Bank creative decision document for the Toss-inspired confirmation/history UI.
- Added transcript models and repository loading for selected history sessions using existing `chat_sessions` and `agent_action_logs` data.
- Added selected-transcript state and `openSession(sessionId)` / `closeSession()` to `MainViewModel`.
- Made history drawer rows clickable and renamed the history surface to Mabi terminology.
- Added a full-screen chat-style transcript view with user, planner, action/thought, and status bubbles.
- Adjusted the confirmation sheet hierarchy to ask "계속 진행할까요?" first and emphasize the proceed path with Toss-like blue treatment.
- Kept runtime, policy, orchestrator, and OpenRouter-only behavior unchanged.

Verified:

- `bash scripts/check_capacity.sh`
- `./gradlew --no-configuration-cache :data-history:compileDebugKotlin :app:compileDebugKotlin`
- `./gradlew --no-configuration-cache :app:testDebugUnitTest --tests 'com.guribbong.phoneappagent.ui.RiskOptionUiBuilderTest'`
- `./gradlew --no-configuration-cache :app:assembleDebug`
- `bash scripts/check_runtime_boundary.sh`
- `bash scripts/check_openrouter_only_runtime.sh`
- `QA_POST_LAUNCH_WAIT_SEC=6 bash scripts/qa_milestone.sh toss-inspired-confirm-history-ui app/build/outputs/apk/debug/app-debug.apk`
- Manual connected-device smoke via ADB:
  - opened the history drawer
  - tapped a history row
  - verified the transcript screen rendered

QA artifacts:

- `qa_artifacts/toss-inspired-confirm-history-ui/screen.png`
- `qa_artifacts/toss-inspired-confirm-history-ui/history_drawer.png`
- `qa_artifacts/toss-inspired-confirm-history-ui/history_transcript.png`

Remaining gap:

- Confirmation bottom sheet compile path is verified and existing risk copy tests pass, but this run did not safely trigger a new live `WAITING_FOR_CONFIRM` flow because connected-device history includes real Play Store install sessions. Use a dedicated safe confirm fixture or non-install confirm scenario for visual confirmation-sheet QA.
