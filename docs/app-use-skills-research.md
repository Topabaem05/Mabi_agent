# App-Use Skills And Tooling Research

Date: 2026-04-24

## Relevant External Patterns

### mobile-use / Minitap

Useful ideas to port:

- Split planning from screen reasoning and execution.
- Store agent thoughts and executor feedback as first-class trace data.
- Use scratchpad memory tools for reusable information.
- Target UI elements with multiple fallback signals: resource id, text, index, bounds.
- Verify text input against the observed device state.
- Detect repeated failures and force a strategy change.
- Keep an app lock and explicitly classify valid deviations such as OAuth, login, and
  permission screens.

Constraints:

- Upstream local runtime depends on host-side ADB/uiautomator2 for Android. That cannot
  be used as this product runtime path, but it remains useful as QA harness/reference.

### AndroidWorld

Useful ideas:

- Treat app control as benchmarkable golden scenarios, not informal prompt demos.
- Keep deterministic state reset, artifacts, and pass/fail criteria per task.
- Separate read-only tasks from destructive tasks.

### k-skill Style App Skills

Useful ideas:

- App-specific procedures should be advisory memory, not direct uncontrolled taps.
- Current accessibility tree must override stale skill instructions.
- Skills should compile into deterministic DSL selectors and safety gates.

## Local Implementation Decision

The first safe port is persistent app memory rather than arbitrary model-written tools.
It gives the planner prior execution evidence without allowing free-form model text to
directly control the phone.

Implemented memory path:

```text
Room agent_app_memories
  -> SessionRepository.appMemoriesForPackages()
  -> AgentOrchestrator.buildPlannerInput()
  -> PlannerInput.appMemory
  -> OpenRouter/LiteRT-LM prompt
```

Memory entries are written only from verified terminal outcomes:

- completed session,
- failed session,
- package inferred from candidate apps/current screen/active plan.

This preserves the non-negotiable rule that free-form model output must still pass
through strict JSON and DSL validation before execution.
