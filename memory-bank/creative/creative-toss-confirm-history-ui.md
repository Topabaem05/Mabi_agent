# Creative: Toss Confirmation And History Chat UI

## Problem

The app needs a clearer user-control UI for two moments: risky agent continuation and past-session review. The current confirmation UI already exists, but it reads more like a process/options panel than a direct "should I proceed?" gate. The current history drawer lists sessions but does not open the underlying conversation or agent thought/action flow.

## Constraints

- Keep Android native Compose + Material 3.
- Do not add Toss, Granite, React Native, or web dependencies.
- Keep OpenRouter-only runtime and deterministic DSL safety gates unchanged.
- High-risk actions must remain blocked at `confirm_user` until the user explicitly confirms.
- Preserve visible agent activity and avoid hidden autonomous control.
- Keep UI usable on narrow Android screens with stable bottom controls and no text overlap.

## Options

### Option 1: Minimal Patch

Keep the current glass home and bottom sheet, make rows clickable, and show a small modal transcript.

Pros:
- Lowest code churn.
- Least risk to current UI.

Cons:
- Transcript modal would compete with the drawer and bottom sheet.
- Confirmation would still feel secondary to process details.
- Less aligned with Toss list/sheet patterns.

### Option 2: Toss-Like Operational Shell

Keep the main Compose shell, but reduce decorative glass prominence. Use compact Toss-like list rows, light grey surfaces, blue primary actions, fixed bottom input, and a full-screen transcript route opened from history.

Pros:
- Clearer hierarchy for both confirmation and history.
- Matches inspected Toss examples without importing their stack.
- Full-screen transcript gives enough room for chat bubbles and action logs.
- Implementation remains local to UI state, repository transcript mapping, and Compose.

Cons:
- Moderate `AppRoot.kt` churn.
- Requires selected-session state and transcript mapping.

### Option 3: Full Navigation Refactor

Introduce a Compose navigation graph with home, history list, transcript, and confirmation destinations.

Pros:
- Clean long-term navigation model.
- Easier future deep links.

Cons:
- Too much scope for this milestone.
- Higher regression risk around service-driven initial prompts and confirmation state.

## Pros And Cons Summary

Option 1 is safe but does not materially improve the UX. Option 3 is architecturally clean but too broad for the current task. Option 2 gives the requested user-visible behavior with contained implementation risk.

## Recommended Decision

Use Option 2.

The confirmation gate becomes a blocking bottom decision sheet: direct question first, policy reason second, choices third, process detail lower in the sheet. History uses the existing drawer as an index, but tapping a row opens a full-screen chat transcript so conversations and agent thoughts can be read cleanly.

## Implementation Notes

- Keep `RiskOptionUiBuilder` copy and options, but adjust `RiskConfirmationSheetContent` ordering and styling toward a Toss-like decision surface.
- Use blue only for the primary confirmation/proceed path and selected/high-emphasis states.
- Add `ChatTranscript`, `ChatTranscriptMessage`, and `ChatTranscriptRole` as repository-facing models.
- Build transcript messages from existing `chat_sessions` and `agent_action_logs`; do not change Room schema.
- Add `MainViewModel.openSession(sessionId)` and `closeSession()`.
- Rename visible Claude-specific history composables to Mabi naming while preserving the same file.
- Render transcript bubbles:
  - user: right aligned, blue bubble
  - agent: left aligned, white bubble
  - thought/action: left aligned, compact grey bubble
  - status: centered or low-emphasis system row
- Keep runtime, policy, orchestrator, and notification behavior unchanged except for UI calls to existing confirm/stop handlers.
