# AGENT.md

## Role

You are building `phone_app_agent`, an Android cross-app local agent MVP.
Goal: run an on-device agent that the user explicitly starts, then let it read, navigate, and act across other Android apps through `AccessibilityService`.

## Product Direction

- Keep main model fixed by default: `Gemma 4 E2B`.
- Keep Android on-device runtime fixed by default: `LiteRT-LM`.
- If model does not know target Korean app flow well, consult `NomaDamas/k-skill` first.
- Keep agent visibly active while observing or acting: focus ring, highlight border, or active pill.
- UI should feel like Android Messages: simple, modern, restrained, operational.
- Prefer official SDKs, proven samples, and small glue code over custom framework work.

## Non-Negotiables

### Model / Runtime

1. Main model stays `Gemma 4 E2B` instruction-tuned unless user changes it.
2. Primary Android runtime is `LiteRT-LM`.
3. Older function-calling SDKs may be reference material, not main runtime.
4. Runtime abstraction is allowed, but repo default remains `Gemma 4 E2B + LiteRT-LM`.

### Execution / Safety

5. Never execute free-form reasoning directly. Convert to deterministic DSL first.
6. High-risk actions must hard-stop at `confirm_user`: send, pay, delete, post, share, external upload, install, permission approval, account/security change.
7. Agent may act only after explicit user start. Background waiting is allowed; unattended automation is not MVP scope.
8. User must always be able to see where agent is looking or acting. Keep visual highlight on.

### Product / Distribution

9. Design for internal distribution, sideload, or testing tracks first.
10. Treat `AccessibilityService` disclosure, user intent, and visible active state as product requirements.

## Recommended Stack

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- DI: Koin
- Persistence: Room + DataStore
- Deferred jobs only: WorkManager for downloads, cache cleanup, diagnostics upload
- Cross-app control: `AccessibilityService`
- Overlay: accessibility overlay APIs first, fallback `TYPE_ACCESSIBILITY_OVERLAY`
- Tests: AndroidX Test, UI Automator, Macrobenchmark, Test Orchestrator

## Architecture Target

```text
phone_app_agent/
├─ app/                   # Compose app shell, chat UI, onboarding
├─ runtime-litertlm/      # Gemma 4 E2B runtime wrapper
├─ driver-accessibility/  # node tree, gestures, selectors, package detection
├─ core-dsl/              # deterministic action schema
├─ core-policy/           # risk gate, allow/deny, confirm rules
├─ core-runner/           # step runner, retry, recovery, logs
├─ skill-registry/        # k-skill lookup, compile, cache
├─ overlay-ui/            # focus ring, active pill, confirm badge
├─ data-history/          # sessions, messages, step logs
├─ backtest-runner/       # deterministic replay and result writer
├─ macrobenchmark/
├─ baselineprofile/
└─ docs/
```

## Core Behavior Rules

- Prefer accessibility node tree over OCR or vision.
- Prefer `contentDescription`, `resourceId`, `text`, `className`, and bounds-based selectors.
- Use screenshots as support evidence or fallback, not first-line planner input.
- If `k-skill` and current UI disagree, current UI wins.
- Keep overlay non-intrusive: minimal accent, neutral palette, no heavy dimming.

## Minimum DSL Surface

Start from this action set:

- `launch_app`
- `wait_for`
- `tap`
- `double_tap`
- `long_press`
- `scroll`
- `swipe`
- `input_text`
- `clear_text`
- `press_global`
- `assert_visible`
- `assert_not_visible`
- `extract_text`
- `confirm_user`
- `sleep`
- `stop`

## Delivery Workflow

- Work in small milestones, not wide batches.
- Typical milestones:
  - chat shell
  - local model wrapper
  - accessibility driver basics
  - overlay/focus ring
  - planner to DSL
  - policy gate
  - replay runner
  - k-skill integration
- After each milestone, stop coding and run real app QA before next milestone.
- Do not stack multiple untested milestones together.

## Mandatory Milestone QA

For every meaningful implementation stage:

1. Check disk headroom before build or emulator work.
2. Boot Android emulator before claiming milestone progress.
3. Build current app, install it, and launch it.
4. Exercise changed path manually in emulator.
5. Record outcome: what rendered, what worked, what failed, what blocked.
6. If UI changed, confirm actual rendered UI in emulator.
7. If navigation or feature flow changed, drive that path end-to-end in emulator or instrumentation.
8. If model runtime changed, still run emulator shell QA, but reserve final runtime/perf claims for physical device.
9. Do not proceed to next milestone until current one has explicit QA result.

Static inspection, type checks, and code review do not replace milestone QA.

## Disk / Capacity Discipline

- Expect macOS local disk pressure during Android work.
- Before heavy steps, check free space for workspace volume and `/tmp`.
- Keep at least ~20 GB free before system-image install, AVD creation, benchmark runs, or large Gradle cache expansion.
- Keep at least ~10 GB free during normal incremental dev, build, and emulator QA loops.
- Inspect these directories before or during heavy work:
  - `~/.gradle`
  - `~/.android`
  - `${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}`
  - repo `build/` outputs
  - emulator snapshots and unused AVDs
- Keep one emulator active unless test need requires more.
- Keep one Gradle build active unless there is a strong reason not to.
- Prefer project-local build outputs and remove stale outputs after verified milestones.
- If space gets tight, reclaim in this order:
  - stale repo `build/` outputs
  - stale local `.gradle/` outputs for repo
  - stale emulator snapshots
  - unused AVDs or system images
  - regenerable Gradle caches
- If disk pressure breaks build/test, report culprit path and cleanup action.

## QA Strategy

This project should be verified with deterministic replay plus stepwise observation, not vague prompt poking.

### Golden Scenarios

- G01: Open Settings, enter Wi-Fi or Internet area
- G02: Open Bluetooth page and read state only
- G03: Open Clock and enter Alarm tab
- G04: Open Calculator and verify `12 + 7 = 19`
- G05: Open Contacts, focus search, type only
- G06: Open Chrome and load predefined URL
- G07: Open Maps and reach destination search results
- G08: Open Files and confirm file presence
- G09: In messenger, stop before send
- G10: In mail, stop before send

### Result Artifacts

When available, keep QA artifacts under:

```text
backtest-results/<run_id>/
├─ result.json
├─ screenshots/
├─ ui-trees/
└─ trace.log
```

### Pass Criteria

- Low/medium scenarios trend toward >=95% step success
- No send without `confirm_user`
- Recovery loops must not thrash indefinitely
- Overlay alignment drift is treated as failure

## Implementation Order

### Step 0

- Define allowlist / denylist
- Define risk actions
- Freeze MVP boundary: read, navigate, safe input, stop before risky commit

### Step 1

- Create project skeleton
- Compose shell
- history drawer
- Room / DataStore

### Step 2

- LiteRT-LM wrapper
- model import / warm-up
- local chat persistence
- note: emulator does not prove final on-device runtime quality

### Step 3

- AccessibilityService
- node tree walking
- foreground package detection
- `tap`, `scroll`, `input_text`, `press_global`

### Step 4

- focus ring / overlay
- active badge
- confirm badge

### Step 5

- planner to DSL
- policy gate
- runner / retry / stop / recovery

### Step 6

- `k-skill` resolver
- skill loader
- skill compiler
- low-confidence skill assist

### Step 7

- `confirm_user`
- message/mail/share/post/delete/pay protection

### Step 8

- replay runner
- golden scenario automation
- screenshots / UI trees / result logs

### Step 9

- warm-up latency
- overlay jank
- input latency
- macrobenchmark / baseline profile

### Step 10

- onboarding
- disclosure copy
- bug export / diagnostics
- settings polish

## Anti-Patterns

- No OCR-first control path when accessibility tree exists.
- No invisible background control.
- No high-risk action without confirmation gate.
- No “done” after code edits only.
- No runtime success claims from emulator-only evidence.
- No uncontrolled disk growth from repeated AVD creation, snapshot accumulation, or parallel Gradle churn.

## Useful Commands

- Free space: `df -h . /tmp`
- Large Android dirs: `du -sh ~/.gradle ~/.android ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}} 2>/dev/null`
- Emulator list: `emulator -list-avds`
- Devices: `adb devices`
- Build: `./gradlew assembleDebug`
- Instrumented tests: `./gradlew connectedDebugAndroidTest`

## Done Definition

Only treat work as done when:

- changed scope builds,
- milestone emulator QA ran after each meaningful stage,
- final changed flow was manually verified in emulator,
- physical-device follow-up happened for hardware-sensitive runtime claims,
- disk usage stayed under control or cleanup/reporting was done.
