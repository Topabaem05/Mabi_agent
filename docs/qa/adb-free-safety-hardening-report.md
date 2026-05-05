# ADB-Free Safety Hardening Report

Date: 2026-05-01

## Scope

This report tracks implementation against `/Users/guribbong/Downloads/mabi_agent_adb_free_docs`, focused on user-started visible sessions, ADB-free runtime boundary, deterministic DSL validation, high-risk confirmation, loop detection, and verification evidence.

## Requirements Covered In This Pass

- Requirement 2: runtime source-set boundary audit.
- Requirement 4: deterministic DSL gate before execution.
- Requirement 5: English and Korean high-risk goals require confirmation.
- Requirement 9: repeated no-progress loops fail closed.
- Requirement 13: hidden background control is documented as forbidden.
- Requirement 14: README and architecture docs describe runtime boundary and verification.

## Commands Run

```text
scripts/check_capacity.sh /Users/guribbong/code/phone_app_agent
Result: completed. Free space after build was 16 GiB; Android dirs were ~/.gradle 1.8G, ~/.android 4.3G, SDK 8.1G.

./gradlew :core-policy:test
Result: RED failed before implementation on Korean high-risk policy; GREEN passed after implementation.

./gradlew :core-runner:test
Result: RED failed before PlanValidator/LoopDetector existed; GREEN passed after implementation.

./gradlew --no-configuration-cache :app:testDebugUnitTest --tests 'com.guribbong.phoneappagent.agent.AgentOrchestratorConfirmFlowTest.invalidPlannerPlanFailsBeforeDriverAction'
Result: RED failed because malformed plan reached execution; GREEN passed after orchestrator validation.

./gradlew --no-configuration-cache :core-policy:test :core-runner:test :app:testDebugUnitTest :runtime-litertlm:testDebugUnitTest
Result: BUILD SUCCESSFUL in 9s; 102 actionable tasks, 3 executed, 99 up-to-date.

bash scripts/check_runtime_boundary.sh
Result: OK, no forbidden desktop/ADB automation terms in runtime source sets.

./gradlew --no-configuration-cache :app:assembleDebug
Result: BUILD SUCCESSFUL in 6s; APK produced at app/build/outputs/apk/debug/app-debug.apk.

emulator -list-avds
Result: Pixel_9a.

adb devices
Result: no connected devices before emulator QA.

QA_POST_LAUNCH_WAIT_SEC=8 scripts/qa_milestone.sh adb-free-safety-hardening app/build/outputs/apk/debug/app-debug.apk
Result: completed with artifacts, but manual UI check was blocked by emulator SystemUI ANR.
```

## Manual QA

Emulator QA result: partial/blocker recorded.

- AVD booted as `emulator-5554`.
- QA script warned that the emulator did not fully settle: focused window was `Application Not Responding: com.android.systemui`, free memory was 180 MB.
- APK install succeeded.
- `am start -W` targeted `com.guribbong.phoneappagent/.MainActivity`, but launch state timed out while the SystemUI ANR dialog covered the screen.
- Artifacts were written under `qa_artifacts/adb-free-safety-hardening/`, including `screen.png`, `ui.xml`, `top.txt`, `logcat.txt`, and session database dumps.
- `top.txt` showed `mFocusedApp=... com.guribbong.phoneappagent/.MainActivity`; `ui.xml` showed the SystemUI ANR dialog.

Physical disconnected-device QA: blocked unless a physical Android device is available and USB can be disconnected before running a safe scenario.

## Notes

ADB remains allowed for development installation, emulator control, log inspection, and instrumentation tests. It is not allowed as a shipped runtime dependency or action transport.

Runtime direction changed after physical QA: agent planning must use OpenRouter only. Device-hosted planner backend failures are no longer accepted runtime paths.
