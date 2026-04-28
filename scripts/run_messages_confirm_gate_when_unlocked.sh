#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MILESTONE="${1:-step121-messages-confirm-gate-after-unlock}"
APK_PATH="${2:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
GOAL="${QA_SEED_PROMPT:-Open Messages and send hello to 12345}"
ARTIFACT_DIR="$ROOT_DIR/qa_artifacts/$MILESTONE"

cleanup_pid_file() {
  if [[ -n "${WATCH_PID_FILE:-}" ]]; then
    rm -f "$WATCH_PID_FILE"
  fi
}

trap cleanup_pid_file EXIT

export QA_ENABLE_ACCESSIBILITY="${QA_ENABLE_ACCESSIBILITY:-1}"
export QA_AUTO_QUEUE="${QA_AUTO_QUEUE:-1}"
export QA_POST_LAUNCH_WAIT_SEC="${QA_POST_LAUNCH_WAIT_SEC:-90}"
export QA_CAPTURE_UIAUTOMATOR_DUMP="${QA_CAPTURE_UIAUTOMATOR_DUMP:-0}"
export QA_SEED_PROMPT="$GOAL"

"$ROOT_DIR/scripts/wait_for_unlock_and_qa.sh" "$MILESTONE" "$APK_PATH"

if [[ -x "$ROOT_DIR/scripts/inspect_messages_confirm_gate.sh" && -d "$ARTIFACT_DIR" ]]; then
  "$ROOT_DIR/scripts/inspect_messages_confirm_gate.sh" "$ARTIFACT_DIR" | tee "$ARTIFACT_DIR/inspection.txt"
fi
