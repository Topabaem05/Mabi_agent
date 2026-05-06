# Active Context

Current task: Toss-inspired confirmation and history chat UI.

Completed this phase:
- Added a creative decision document for the confirmation/history UI direction.
- Kept the confirmation gate as a bottom decision sheet and made its hierarchy ask whether to proceed first.
- Added selected-session transcript loading from existing history tables and action logs.
- Made history rows clickable and opened selected sessions as a full-screen chat-style transcript.
- Verified compile, focused UI test, debug APK build, runtime-boundary checks, OpenRouter-only check, and connected-device launch/history transcript smoke.

Current QA notes:
- Connected-device QA artifacts are under `qa_artifacts/toss-inspired-confirm-history-ui/`.
- `history_drawer.png` shows the Toss-like history list.
- `history_transcript.png` shows the selected session transcript as chat bubbles.
- A new live `WAITING_FOR_CONFIRM` visual smoke was not triggered in this run to avoid replaying real Play Store install flows on the connected device.

Next phase:
- Add a safe fixture or non-install scenario to visually QA the confirmation bottom sheet without risking app install, payment, send, permission, or account-change behavior.
