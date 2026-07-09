# Mabi Agent

Mabi Agent is a user-started Android cross-app agent MVP. It observes and acts through Android `AccessibilityService`, converts planner output into deterministic DSL actions, and keeps agent activity visible through overlay or notification state.

## Runtime Boundary

The shipped runtime must not depend on ADB, uiautomator2, Appium, desktop bridge processes, or shell UI automation. ADB is development-only for install, logs, emulator QA, and instrumented tests.

Run the runtime boundary audit from the repo root:

```bash
bash scripts/check_runtime_boundary.sh
```

## Visible Supervised Session

Supported mode:

- User explicitly starts a session from Mabi Agent.
- Accessibility service is enabled and connected before execution.
- Agent remains visibly active while observing or acting in target apps.
- High-risk work stops at `confirm_user` before commit actions.

Forbidden mode:

- Hidden background control.
- Unattended autonomous operation after the user stops a session.
- Direct execution of free-form model text as taps, text input, scrolls, launches, or global actions.

## Runtime Direction

Product runtime is OpenRouter only. `OPENROUTER_API_KEY` must be configured before an agent session can plan actions.

There is no device-hosted model, GPU backend, CPU backend, or fallback planner path in the shipped app. If OpenRouter is unavailable, the session fails with a visible error and no accessibility action is executed.

## Verification

```bash
scripts/check_capacity.sh
./gradlew --no-configuration-cache :app:assembleDebug
./gradlew --no-configuration-cache :core-policy:test :core-runner:test :runtime-litertlm:testDebugUnitTest :app:testDebugUnitTest
bash scripts/check_runtime_boundary.sh
bash scripts/check_openrouter_only_runtime.sh
```

Emulator QA is required for meaningful app milestones. Physical disconnected-device QA is required before claiming standalone ADB-free runtime completion.

---

## Related Project

[OpenLife Market](https://topabaem05.github.io/openlife-market/) - Autonomous AI agents that must sell their own research to survive. Live experiment based on arXiv:2606.31046.
