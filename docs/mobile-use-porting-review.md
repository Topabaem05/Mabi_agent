# mobile-use Porting Review

Date: 2026-04-24

## Scope

This review compares the current Android in-app agent against `minitap-ai/mobile-use`
v3.6.3 and the public mobile-use documentation. The product constraint remains
different: this app must run from inside Android through `AccessibilityService`,
without depending on a host ADB/uiautomator2 process during normal runtime.

## Sources Checked

- Upstream checkout: `.mobile-use-src`, commit `e1d31ab` (`chore: bump to v3.6.3`)
- Upstream public README: `https://github.com/minitap-ai/mobile-use`
- Upstream paper summary: `https://arxiv.org/abs/2602.07787`
- Local QA evidence: `qa_artifacts/batch154-all-launcher-app-agent-qa/latest_summary.tsv`

## What Is Ported

- Natural-language goal entry exists through the app chat UI and seed prompt QA path.
- UI-aware control exists through `AccessibilityService` node snapshots and DSL actions.
- Deterministic action schema exists in `core-dsl`.
- Stepwise replanning exists through `PlanningMode.STEPWISE_REPLAN`, `recentActionHistory`,
  `priorPlanSummary`, and short-horizon plans.
- Safe confirmation exists for high-risk actions through `confirm_user`.
- Trace artifacts exist through Room session rows and `agent_action_logs`.
- Foreground execution exists through `AgentForegroundService` and visible overlay/notification paths.

## Not Yet Fully Ported

- mobile-use separates Planner, Orchestrator, Contextor, Cortex, Executor, and Summarizer.
  The current app still mostly uses one planner plus executor loop, so context pollution
  and brittle step selection remain likely.
- mobile-use has scratchpad tools (`save_note`, `read_note`, `list_notes`). The app now
  has persistent app outcome memory, but not model-callable arbitrary scratchpad tools.
- mobile-use targets elements with combined `resource_id`, index, text, and bounds, then
  falls back across those signals. The current DSL supports similar fields, but the planner
  and executor do not consistently require or exploit combined full-target data.
- mobile-use verifies text input using device state feedback. The current app logs text
  input success from the action performer, but does not always re-observe and assert the
  typed text before advancing.
- mobile-use has app-lock/contextor logic that relaunches the locked app or permits
  OAuth/permission deviations. The current app has canonical package alias handling, but
  not a dedicated app-lock reasoning agent.
- mobile-use includes explicit loop/failure strategy changes. The current recovery path is
  bounded, but often fails after one selector miss instead of pivoting to an alternate
  strategy derived from the current screen.
- mobile-use supports structured data scraping and optional video analysis. The current app
  does not have a structured extraction output path beyond session logs.

## Memory Added In This App

The app now persists app-level execution memories in Room:

- Table: `agent_app_memories`
- Repository API: `appMemoriesForPackages(...)`, `rememberAppOutcome(...)`
- Planner input field: `PlannerInput.appMemory`
- Runtime prompt fields: `appMemory` for OpenRouter runtime, `mem` for compact LiteRT-LM runtime

The orchestrator records terminal outcomes:

- Success memory: recent successful action chain and safe checkpoint.
- Failure memory: failure reason plus instruction to avoid repeating the same selector/package blindly.

Planner calls receive memories for:

- candidate apps inferred from the goal,
- current foreground package,
- last external foreground package,
- active plan target packages.

## QA Result That Motivated This

Latest broad app QA over 18 launcher apps:

- Completed: 3/18
- Confirm-gated safe stop: 1/18
- Failed: 14/18

Primary failure categories:

- Wrong package aliases: Contacts, Clock, Messages, Play Store, SIM Toolkit.
- Brittle selectors: Chrome, Maps, Phone, YouTube Music, Camera, Photos, Drive, Calendar.
- Permission/login/SystemUI deviations not handled as task context: YouTube, Drive, Calendar.

## Next Porting Priorities

1. Add app-lock/contextor behavior.
   Keep execution inside the target package unless the current screen is a valid OAuth,
   permission, account, or system dialog deviation.

2. Require full-target selectors.
   Planner should provide resourceId plus text/contentDescription plus boundsHint when
   available, not a single fragile ID.

3. Add verified text input.
   After `input_text`, re-observe the editable field and only continue if expected text
   or query/result evidence is visible.

4. Replace hardcoded Samsung-only prompt rules with installed-app aliases.
   Current Pixel emulator failures show Samsung-specific instructions leaking into Google
   apps.

5. Add screen-level failure memory.
   Store `(package, goal class, failed selector, visible screen summary, better next
   strategy)` so replans can pivot rather than repeat.

6. Add structured extraction output.
   mobile-use supports data scraping; this app needs a safe `extract_text`/JSON result
   path before claiming parity.
