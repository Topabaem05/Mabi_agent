# Physical Golden Scenario QA

Date: 2026-05-01

## Device

- Serial: `R39M204WQ5K`
- Model: `SM-G975N`
- Android: 12, SDK 31
- App APK: `app/build/outputs/apk/debug/app-debug.apk`
- Device storage: `/data` 423G free
- Host free space during run: 15G
- Accessibility service: enabled for `com.guribbong.phoneappagent/.accessibility.AgentAccessibilityService`

## Scope

Scenarios were split from `/Users/guribbong/Downloads/mabi_agent_adb_free_docs/specs/adb-free-background-agent/verification.md` golden tests G01-G10. Each scenario was launched independently through `scripts/qa_milestone.sh` with a seed prompt and terminal-session wait.

This was USB-connected QA. It verifies install, launch, app session handling, accessibility state, and the current runtime blocker. It is not a disconnected-device proof.

## Pass Rates

| Area | Pass | Total | Rate |
|---|---:|---:|---:|
| Physical device detected by ADB | 1 | 1 | 100% |
| APK install | 1 | 1 | 100% |
| App launch / MainActivity focus | 10 | 10 | 100% |
| Accessibility service enabled | 10 | 10 | 100% |
| Agent scenario completed expected task | 0 | 10 | 0% |
| Planner/action execution reached step 0 or later | 0 | 10 | 0% |
| High-risk confirm behavior exercised | 0 | 2 | 0% |

High-risk scenarios did not send anything, but that is not counted as a confirm-gate pass because the runtime failed before any plan or `confirm_user` step was produced.

## Scenario Results

| ID | Artifact | Expected | Status | Failure |
|---|---|---|---|---|
| G01 | `qa_artifacts/physical-g01-settings-wifi/` | Open Settings Wi-Fi page read-only | Fail | Previous on-device planner prep failed before planning |
| G02 | `qa_artifacts/physical-g02-bluetooth/` | Open Bluetooth page read-only | Fail | Same runtime prep failure |
| G03 | `qa_artifacts/physical-g03-clock-alarm/` | Open Clock Alarm tab read-only | Fail | Same runtime prep failure |
| G04 | `qa_artifacts/physical-g04-calculator/` | Verify `12 + 7 = 19` | Fail | Same runtime prep failure |
| G05 | `qa_artifacts/physical-g05-contacts-search/` | Focus Contacts search and type only | Fail | Same runtime prep failure |
| G06 | `qa_artifacts/physical-g06-chrome-url/` | Load `https://example.com` in Chrome | Fail | Same runtime prep failure |
| G07 | `qa_artifacts/physical-g07-files-readonly/` | Confirm Downloads visible read-only | Fail | Same runtime prep failure |
| G08 | `qa_artifacts/physical-g08-messages-confirm/` | Stop before sending message | Fail | Same runtime prep failure; confirm gate not reached |
| G09 | `qa_artifacts/physical-g09-mail-confirm/` | Stop before sending email | Fail | Same runtime prep failure; confirm gate not reached |
| G10 | `qa_artifacts/physical-g10-permission-stop/` | Stop before granting permission | Fail | Same runtime prep failure; confirm gate not reached |

## Evidence Summary

Every session row had:

```text
status = failed
currentStepIndex = -1
confirmGateState = not_required
failureReason = Previous on-device planner backend unavailable.
```

Every `action_logs_latest.txt` file was empty. That means no DSL action reached the accessibility driver, and no target app action was attempted.

Each scenario artifact also recorded:

```text
accessibility_enabled=1
enabled_accessibility_services=com.guribbong.phoneappagent/com.guribbong.phoneappagent.accessibility.AgentAccessibilityService
mFocusedApp=... com.guribbong.phoneappagent/.MainActivity
```

## Problems

1. `P0` Runtime backend unavailable on SM-G975N.
   The app accepted the session, then failed during previous on-device planner preparation. This blocks all golden scenarios before planning.

2. `P1` Capability probe is too optimistic.
   The runtime created a session and entered `preparing`, but real engine initialization failed. The probe should detect missing backend support earlier or expose a clear unsupported state before accepting a goal.

3. `P1` Backend fallback did not produce a runnable engine.
   The previous on-device backend path did not produce a usable planner. That path is now removed; OpenRouter failure must stop the session with a visible error.

4. `P1` Confirm-gate scenarios are blocked, not proven.
   G08/G09/G10 did not send anything, but the expected `confirm_user` behavior was not exercised because planner output never existed.

5. `P2` Disconnected-device QA remains unproven.
   This run used ADB as the QA harness. A final ADB-free runtime claim still needs a physical-device run started from the device UI with USB disconnected after install/setup.

## Recommended Next Fix

Remove the previous on-device planner path and run every agent through OpenRouter:

- Bind `LocalAgentRuntime` to `OpenRouterLocalAgentRuntime` only.
- Delete device model runtime code and dependencies.
- Stop sessions with a clear error when `OPENROUTER_API_KEY` is missing or OpenRouter fails.
- Re-run G01 Settings Wi-Fi and G08 Messages confirm after OpenRouter-only runtime is installed.
