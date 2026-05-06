# Mabi Agent Mobile OpenRouter Skill Plan

Source: `/Users/guribbong/Downloads/Mabi_agent-plan.pdf`

## Objective

Move Mabi Agent toward the document's remote-OpenRouter mobile-agent workflow while preserving the repo's current product boundary:

- OpenRouter is the only product planner runtime.
- AccessibilityService is the production control path.
- ADB is allowed only for development and QA.
- Model output must become deterministic DSL before any action runs.
- High-risk actions must stop at `confirm_user`.

## Document Requirements Mapped To Repo Work

1. Compact structured snapshots
   - Add deterministic element references such as `@e1`.
   - Include role, text/content description, resource id, package, editable/clickable flags, index path, and bounds when available.
   - Keep snapshots token-efficient for OpenRouter prompts.

2. Deterministic element targeting
   - Map `findElement` to the existing `NodeSelector` and selector scoring path.
   - Allow planner-visible index path and bounds hints so elements without stable ids can still be targeted.

3. Skill/function surface
   - Existing mappings: `launch_app`, `tap`, `input_text`, `clear_text`, `scroll`, `press_global`, `assert_visible`, `confirm_user`, `stop`.
   - Add or document equivalents:
     - `snapshotScreen`: accessibility snapshot serialization.
     - `findElement`: selector generation from snapshot nodes.
     - `clickElement`: `tap`.
     - `fillField`: `clear_text` + `input_text`.
     - `scrollView`: `scroll`.
     - `getText`: read node text through snapshot/verification.
     - `takeScreenshot`: QA/debug artifact only initially.
     - `replayScript`: future deterministic replay runner milestone.

4. Remote invocation
   - Keep in-app OpenRouter planning for now.
   - Do not expose a network HTTP/WebSocket command endpoint until authentication and local-only access policy are designed.

5. QA additions
   - Unit-test prompt serialization so refs/index paths/bounds reach OpenRouter compactly.
   - Run runtime-boundary checks to ensure no ADB/Appium/uiautomator enters product runtime.
   - Run OpenRouter-only checks.
   - Build and unit-test the changed modules.
   - Device QA only after code changes build.

## Milestones

### M1: Compact Snapshot Refs For OpenRouter

Implementation:
- Extend app-side node serialization to emit `ref=@eN`, `role`, `idx`, and `bounds`.
- Preserve existing `text`, `desc`, `id`, `class`, `package`, `editable`, and `clickable` fields for compatibility.
- Extend OpenRouter prompt compaction so these fields survive into `visibleNodes`.
- Update system instruction to prefer stable ids/text, but use `indexPath`/`boundsHint` from visible nodes when no stable id exists.

QA:
- `./gradlew --no-configuration-cache :runtime-litertlm:testDebugUnitTest`
- `bash scripts/check_runtime_boundary.sh`
- `bash scripts/check_openrouter_only_runtime.sh`
- `./gradlew --no-configuration-cache :app:assembleDebug`

Pass criteria:
- Prompt unit test proves refs/index/bounds survive compacting.
- Existing runtime tests still pass.
- Runtime boundary scripts pass.
- Build succeeds.

### M2: Explicit Skill Name Documentation And Planner Contract

Implementation:
- Document the PDF skill names as aliases for current DSL actions.
- Add planner prompt wording that explains the alias mapping without allowing raw free-form actions.

QA:
- Unit tests for accepted DSL types still pass.
- Plan validator still rejects empty selectors.

### M3: Replay Script Design

Implementation:
- Add a repo-local design for replay scripts and storage format.
- Defer execution engine until M1/M2 QA is stable.

QA:
- Document review and PlanValidator extension test plan.

## Execution Status

- M1 complete: compact snapshot refs, role labels, index paths, and bounds now survive app serialization and OpenRouter prompt compaction.
- M2 complete: PDF skill aliases are documented in the planner contract as DSL mappings only; validator coverage now rejects empty selectors for `submit_input`, `clear_text`, and targeted `scroll`.
- Next code milestone after this QA gate: M3 replay script design, then a separate implementation milestone for replay execution only after the format and validator tests are accepted.

## QA Evidence

- `bash scripts/check_runtime_boundary.sh`: pass.
- `bash scripts/check_openrouter_only_runtime.sh`: pass.
- `./gradlew --no-configuration-cache :core-runner:test :runtime-litertlm:testDebugUnitTest`: pass.
- `./gradlew --no-configuration-cache :core-policy:test :core-runner:test :runtime-litertlm:testDebugUnitTest :app:testDebugUnitTest`: pass.
- `./gradlew --no-configuration-cache :app:assembleDebug`: pass.
- `QA_DEVICE_SERIAL=R39M204WQ5K QA_POST_LAUNCH_WAIT_SEC=5 bash scripts/qa_milestone.sh mabi-plan-m2 app/build/outputs/apk/debug/app-debug.apk`: pass for physical-device install and launch smoke; full cross-app execution remains gated by enabling AccessibilityService.
