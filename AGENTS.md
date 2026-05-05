# AGENTS.md

## Overview

- This workspace is for an Android cross-app local agent MVP (`Mabi Agent`).
- Product goal: run an agent on Android that the user explicitly starts, then let it read, navigate, and act across other apps through `AccessibilityService`.
- Default stack stays fixed unless user says otherwise: Kotlin + Jetpack Compose + Material 3 + OpenRouter API.
- **CRITICAL:** Product runtime is **OpenRouter only**. There is no device-hosted model, GPU/CPU backend, or local planner fallback in the shipped app. If `OPENROUTER_API_KEY` is missing, sessions must fail visibly.
- Prefer official SDKs, proven samples, and small focused glue code over custom frameworks.

## Source Priority

Use this file as repo-wide operating guide. Use local [AGENT.md](AGENT.md) as primary product spec. It carries the Android agent intent: visible overlay, deterministic DSL, policy gate, `k-skill` lookup, deterministic QA, milestone-by-milestone emulator QA, and disk discipline.

## Non-Negotiables

- **Runtime Boundary:** The shipped runtime must **not** depend on ADB, uiautomator2, Appium, desktop bridge processes, or shell UI automation. ADB is for development/QA only.
- Never convert free-form model output directly into taps. Natural language must become deterministic DSL first.
- High-risk actions must stop at `confirm_user`: send, pay, delete, post, share, account change, permission grant, install, upload.
- Agent moves only after explicit user start. Background waiting is allowed; unattended autonomous operation is not MVP scope.
- Agent activity must stay visible. Keep focus ring / highlight border or active badge on while observing or acting.

## Build Direction

- Main app: chat-style control UI similar to Messages, but quieter and more operational.
- Core modules to grow toward:
  - `app/`: Compose UI, onboarding, session shell
  - `runtime-litertlm/`: API client / wrapper (name kept for legacy reasons, but acts as OpenRouter client)
  - `driver-accessibility/`: node tree, gestures, foreground package, selectors
  - `core-dsl/`: action schema such as `launch_app`, `tap`, `scroll`, `input_text`, `confirm_user`, `stop`
  - `core-policy/`: risk gating and stop rules
  - `core-runner/`: step execution, retry, recovery, logging
  - `skill-registry/`: `k-skill` lookup, compile, cache
  - `overlay-ui/`: focus ring, active badge, confirm badge

## Default Technical Choices

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- DI: Koin
- Persistence: Room + DataStore
- Cross-app control: `AccessibilityService`
- Test stack: AndroidX Test, UI Automator, Macrobenchmark, Test Orchestrator

## Runtime Rules

- Prefer `AccessibilityNodeInfo` tree over OCR/vision. Use screenshots only when tree is insufficient or for QA evidence.
- If `k-skill` exists for target app or domain, load and compile it before planning actions.
- Skill guidance is advisory. Current UI tree wins when skill and screen disagree.

## Delivery Workflow

- Work in small milestones. After every meaningful milestone, stop implementation and run app QA before starting next milestone.
- "QA" means actual emulator/device execution, not static review or screenshot-less claims.

## Verification Commands

Run these to verify codebase health and limits:

```bash
# 1. Check disk capacity before heavy builds
bash scripts/check_capacity.sh

# 2. Build the app
./gradlew --no-configuration-cache :app:assembleDebug

# 3. Run unit tests
./gradlew --no-configuration-cache :core-policy:test :core-runner:test :runtime-litertlm:testDebugUnitTest :app:testDebugUnitTest

# 4. Verify runtime boundaries (No ADB/Appium in prod code)
bash scripts/check_runtime_boundary.sh

# 5. Verify OpenRouter-only compliance
bash scripts/check_openrouter_only_runtime.sh
```

Other useful checks:
- Free space: `df -h . /tmp`
- Large directories: `du -sh ~/.gradle ~/.android ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}} 2>/dev/null`
- Emulator list: `emulator -list-avds`
- Connected devices: `adb devices`

## QA Baseline

Use `scripts/qa_milestone.sh <milestone-name>` for automated QA orchestration when available. 
Default golden scenarios to build toward: Settings (Network), Bluetooth, Clock (Alarm), Calculator, Contacts, Chrome, Maps, Files. Stop before sending messages or emails.

## Disk / Capacity Discipline

- Keep at least ~20 GB free before system-image install, new AVD creation. Keep at least ~10 GB free during normal builds.
- Run `bash scripts/check_capacity.sh` frequently.
- Keep only one emulator running.
- Prefer one active Gradle build at a time to prevent cache explosion.
- Clean stale outputs (`build/`, `.gradle/`) after verified milestones if disk gets tight.

## Anti-Patterns

- No device-hosted models or CPU/GPU model backends. OpenRouter only.
- No ADB or uiautomator usage in the production runtime.
- No OCR-first planner when accessibility tree exists.
- No hidden background control without visible overlay.
- No high-risk action without confirmation gate.
- No unchecked disk growth from parallel Gradle runs.
