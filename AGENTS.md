# AGENTS.md

## Overview

- This workspace is for an Android cross-app local agent MVP.
- Product goal: run an on-device agent on Android that the user explicitly starts, then let it read, navigate, and act across other apps through `AccessibilityService`.
- Default stack stays fixed unless user says otherwise: `Gemma 4 E2B` + `LiteRT-LM` + Kotlin + Jetpack Compose + Material 3.
- Prefer official SDKs, proven samples, and small focused glue code over custom frameworks.

## Source Priority

Use this file as repo-wide operating guide. Use local [AGENT.md](/Users/guribbong/code/phone_app_agent/AGENT.md) as primary product spec. It carries the Android agent intent: visible overlay, deterministic DSL, policy gate, `k-skill` lookup, deterministic QA, milestone-by-milestone emulator QA, and disk discipline.

## Non-Negotiables

- Never convert free-form model output directly into taps. Natural language must become deterministic DSL first.
- High-risk actions must stop at `confirm_user`: send, pay, delete, post, share, account change, permission grant, install, upload.
- Agent moves only after explicit user start. Background waiting is allowed; unattended autonomous operation is not MVP scope.
- Agent activity must stay visible. Keep focus ring / highlight border or active badge on while observing or acting.
- Treat policy/disclosure requirements for `AccessibilityService` as product requirements, not later cleanup.

## Build Direction

- Main app: chat-style control UI similar to Messages, but quieter and more operational.
- Core modules to grow toward:
  - `app/`: Compose UI, onboarding, session shell
  - `runtime-litertlm/`: model wrapper, backend selection, warm-up
  - `driver-accessibility/`: node tree, gestures, foreground package, selectors
  - `core-dsl/`: action schema such as `launch_app`, `tap`, `scroll`, `input_text`, `confirm_user`, `stop`
  - `core-policy/`: risk gating and stop rules
  - `core-runner/`: step execution, retry, recovery, logging
  - `skill-registry/`: `k-skill` lookup, compile, cache
  - `overlay-ui/`: focus ring, active badge, confirm badge
  - `backtest-runner/`: deterministic replay and artifact writer

## Default Technical Choices

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- DI: Koin
- Persistence: Room + DataStore
- Deferred work only: WorkManager for downloads, cache cleanup, log upload
- Cross-app control: `AccessibilityService`
- Overlay: accessibility overlay APIs, fallback `TYPE_ACCESSIBILITY_OVERLAY`
- Test stack: AndroidX Test, UI Automator, Macrobenchmark, Test Orchestrator

## Runtime Rules

- `LiteRT-LM` is first choice runtime. Do not make older function-calling SDKs core runtime.
- Keep model/runtime abstraction replaceable, but default still `Gemma 4 E2B + LiteRT-LM`.
- Prefer `AccessibilityNodeInfo` tree over OCR/vision. Use screenshots only when tree is insufficient or for QA evidence.
- If `k-skill` exists for target app or domain, load and compile it before planning actions.
- Skill guidance is advisory. Current UI tree wins when skill and screen disagree.

## Delivery Workflow

- Work in small milestones. Examples: shell UI, local chat wiring, accessibility driver tap flow, overlay, planner-to-DSL path, policy gate.
- After every meaningful milestone, stop implementation and run app QA before starting next milestone.
- "QA" here means actual emulator/device execution, not static review, typecheck, or screenshot-less claims.
- Do not batch many untested changes together. Keep step size small enough that failures localize fast.

## Mandatory Milestone QA

- For each milestone, boot Android emulator before claiming progress.
- Install and launch current app build in emulator.
- Manually exercise changed flow and capture what happened: screen reached, visible UI, errors, logs, or block.
- If change touches UI, confirm rendered screen in emulator.
- If change touches navigation or behavior, drive that path in emulator or instrumentation test.
- If change touches on-device model runtime, emulator QA still required for shell/integration, but final model-performance claims require physical-device verification.
- Do not continue to next milestone until current milestone has one recorded QA outcome: pass, fail with cause, or blocked by explicit missing prerequisite.

## QA Baseline

- Default golden scenarios to build toward:
  - Open Settings, enter network or Wi-Fi area
  - Open Bluetooth page and read-only state
  - Open Clock and enter Alarm tab
  - Open Calculator and verify `12 + 7 = 19`
  - Open Contacts, focus search, type only
  - Open Chrome and load predefined URL
  - Open Maps and reach search results
  - Open Files and verify file presence
  - In messenger, stop before send
  - In mail, stop before send
- Store replay artifacts when available under `backtest-results/<run_id>/` with `result.json`, screenshots, UI trees, and trace log.

## Disk / Capacity Discipline

- Assume local disk pressure is real. Check free space before heavy Android work, especially first Gradle sync, emulator boot, system-image install, or benchmark runs.
- Keep at least ~20 GB free before system-image install, new AVD creation, or benchmark-heavy runs. Keep at least ~10 GB free during normal incremental builds and emulator QA.
- Before large build/test steps, inspect:
  - free space for workspace volume and `/tmp`
  - size of `~/.gradle`, `~/.android`, Android SDK system images, emulator snapshots, and repo `build/` outputs
- Keep only one emulator running unless task explicitly needs more.
- Do not download extra system images or create extra AVDs unless current test need cannot use existing ones.
- Prefer one active Gradle build at a time. Parallel Android builds waste disk and make cache growth harder to control.
- Use project-local build outputs where practical and clean stale outputs after verified milestones.
- If free space gets tight, reclaim in this order:
  - old repo `build/` and `.gradle/` outputs not needed
  - stale emulator snapshots
  - unused AVDs or system images
  - stale Gradle caches safe to regenerate
- If build/test fails from disk pressure or corrupted caches, report exact culprit directory and recovery action taken.

## Verification Commands

- Free space: `df -h . /tmp`
- Large directories: `du -sh ~/.gradle ~/.android ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}} 2>/dev/null`
- Emulator list: `emulator -list-avds`
- Connected devices: `adb devices`
- Gradle build: `./gradlew assembleDebug`
- Instrumented tests: `./gradlew connectedDebugAndroidTest`

## Anti-Patterns

- No OCR-first planner when accessibility tree exists.
- No hidden background control without visible overlay.
- No high-risk action without confirmation gate.
- No "done" after code edits only. Run emulator/device QA.
- No unchecked disk growth from repeated emulator boots, snapshots, or parallel Gradle runs.
- No claiming LiteRT runtime success from emulator-only evidence.

## Done Definition

- Code builds for changed scope.
- Milestone QA ran in emulator for each meaningful stage of implementation.
- Final changed flow was manually verified on emulator, with physical-device follow-up when runtime claim depends on real hardware.
- Disk remained under control or cleanup actions were documented and executed.
