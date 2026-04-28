#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MILESTONE="${1:-step113-messages-confirm-gate-after-unlock}"
ARTIFACT_DIR="$ROOT_DIR/qa_artifacts/$MILESTONE"
PID_FILE="$ARTIFACT_DIR/watch.pid"
LOG_FILE="$ARTIFACT_DIR/watch.log"

mkdir -p "$ARTIFACT_DIR"

if [[ -f "$PID_FILE" ]]; then
  existing_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "${existing_pid:-}" ]] && kill -0 "$existing_pid" 2>/dev/null; then
    echo "[watch-start] already running pid=$existing_pid"
    echo "[watch-start] log: $LOG_FILE"
    exit 0
  fi
fi

: >>"$LOG_FILE"
printf '\n[%s] start watcher milestone=%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$MILESTONE" >>"$LOG_FILE"

(
  cd "$ROOT_DIR"
  export QA_DEVICE_SERIAL="${QA_DEVICE_SERIAL:-${ANDROID_SERIAL:-}}"
  export QA_CAPTURE_UIAUTOMATOR_DUMP="${QA_CAPTURE_UIAUTOMATOR_DUMP:-0}"
  export QA_UNLOCK_POLL_SEC="${QA_UNLOCK_POLL_SEC:-2}"
  export WATCH_PID_FILE="$PID_FILE"
  nohup bash "$ROOT_DIR/scripts/run_messages_confirm_gate_when_unlocked.sh" "$MILESTONE" \
    >>"$LOG_FILE" 2>&1 </dev/null &
  echo $! >"$PID_FILE"
)

new_pid="$(cat "$PID_FILE")"
sleep 1
if ! kill -0 "$new_pid" 2>/dev/null; then
  echo "[watch-start] fail: watcher exited immediately"
  tail -n 20 "$LOG_FILE" || true
  rm -f "$PID_FILE"
  exit 11
fi

echo "[watch-start] started pid=$new_pid"
echo "[watch-start] pid file: $PID_FILE"
echo "[watch-start] log: $LOG_FILE"
