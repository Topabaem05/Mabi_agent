# Project Brief

Mabi Agent is an Android cross-app local agent MVP. The app uses OpenRouter as the only planner runtime and executes deterministic DSL actions through AccessibilityService after the user starts a task.

Core constraints:
- OpenRouter only; no local model or fallback planner.
- AccessibilityService is the production control path.
- ADB is QA-only.
- High-risk actions such as install, send, delete, share, payment, and permission grants must stop at `confirm_user`.
- The user must remain in control before any risky next process continues.
