# OpenRouter-Only Runtime QA

Date: 2026-05-01

## Scope

This run verifies the runtime switch requested after physical-device golden QA:

- All app agent sessions bind to `OpenRouterLocalAgentRuntime`.
- Local planner implementation code, dependency aliases, and fallback build switches are removed.
- Missing OpenRouter credentials fail before actions.
- OpenRouter planning is exercised on the connected physical device.

## Local Verification Pass Rates

| Area | Pass | Total | Rate |
|---|---:|---:|---:|
| Runtime DI OpenRouter-only proof | 1 | 1 | 100% |
| Runtime prompt and fail-fast unit tests | 2 | 2 | 100% |
| Full selected Gradle regression commands | 1 | 1 | 100% |
| Runtime boundary audits | 2 | 2 | 100% |
| Debug APK build | 1 | 1 | 100% |

## Physical Device Smoke Pass Rates

Device: `R39M204WQ5K`, Samsung `SM-G975N`, Android 12 / SDK 31.

| Area | Pass | Total | Rate |
|---|---:|---:|---:|
| APK install | 2 | 2 | 100% |
| Accessibility service enabled | 2 | 2 | 100% |
| OpenRouter HTTP planning request succeeded | 2 | 2 | 100% |
| Old device-backend prep failure observed | 0 | 2 | 0% |
| Scenario completed expected task | 0 | 2 | 0% |
| High-risk send action executed | 0 | 1 | 0% |

## Scenario Results

| ID | Artifact | Expected | Status | Problem |
|---|---|---|---|---|
| G01 | `qa_artifacts/physical-openrouter-g01-settings-wifi/` | Open Settings Wi-Fi page read-only | Fail | OpenRouter planned and launched Settings, then tapped missing selector `com.android.settings:id/search_action_bar`. |
| G08 | `qa_artifacts/physical-openrouter-g08-messages-confirm/` | Prepare message and stop before send | Fail safe | OpenRouter response failed Messages-specific validation twice: plan did not wait for `com.samsung.android.messaging:id/fab`. No DSL action ran. |

## Commands Run

```text
./gradlew --no-configuration-cache :app:testDebugUnitTest --tests 'com.guribbong.phoneappagent.di.RuntimeSelectionTest.appModuleBindsEveryAgentSessionToOpenRouterRuntime'
Result: RED before DI change, GREEN after DI change.

./gradlew --no-configuration-cache :runtime-litertlm:testDebugUnitTest --tests 'com.guribbong.phoneappagent.runtime.litertlm.AppSkillPromptTest' --tests 'com.guribbong.phoneappagent.runtime.litertlm.OpenRouterRuntimeFailureTest'
Result: BUILD SUCCESSFUL.

./gradlew --no-configuration-cache :app:testDebugUnitTest :runtime-litertlm:testDebugUnitTest :core-policy:test :core-runner:test
Result: BUILD SUCCESSFUL in 1m 17s.

bash scripts/check_runtime_boundary.sh
Result: OK.

bash scripts/check_openrouter_only_runtime.sh
Result: OK.

./gradlew --no-configuration-cache :app:assembleDebug
Result: BUILD SUCCESSFUL in 5m 31s.

QA_DEVICE_SERIAL=R39M204WQ5K QA_ENABLE_ACCESSIBILITY=1 QA_AUTO_QUEUE=1 QA_WAIT_FOR_SESSION_TERMINAL=1 QA_SESSION_TIMEOUT_SEC=240 QA_POST_LAUNCH_WAIT_SEC=5 QA_SEED_PROMPT='Open Settings and go to the Wi-Fi or network settings page. Read only; do not change any setting.' scripts/qa_milestone.sh physical-openrouter-g01-settings-wifi app/build/outputs/apk/debug/app-debug.apk
Result: failed at selector lookup after OpenRouter planning.

QA_DEVICE_SERIAL=R39M204WQ5K QA_ENABLE_ACCESSIBILITY=1 QA_AUTO_QUEUE=1 QA_WAIT_FOR_SESSION_TERMINAL=1 QA_SESSION_TIMEOUT_SEC=240 QA_POST_LAUNCH_WAIT_SEC=5 QA_SEED_PROMPT='Open Messages and prepare a text message to Test Contact saying hello, but do not send it. Stop before any send action and ask for confirmation.' scripts/qa_milestone.sh physical-openrouter-g08-messages-confirm app/build/outputs/apk/debug/app-debug.apk
Result: fail-safe validation failure before actions.
```

## Problems

1. `P1` Samsung Settings selector mismatch.
   OpenRouter produced `com.android.settings:id/search_action_bar`, but the current UI tree did not expose that tap target. The next fix should bias Settings search plans toward visible text/content descriptions or app-skill selectors proven on this device.

2. `P1` Messages startup validator is too strict for initial plans.
   The validator rejected a safe initial launch/wait plan because it did not include the Samsung Messages compose button wait in the same draft. Either the prompt must force that complete sequence, or validation should allow a safe launch/wait prefix and enforce the compose-button wait on the next replan.

3. `P2` Full G01-G10 rerun still needed.
   This run proves the OpenRouter-only path reaches planning on device and removes the previous runtime blocker. It does not prove all golden scenarios pass.
