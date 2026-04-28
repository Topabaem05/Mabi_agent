#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <artifact-dir>"
  exit 64
fi

ARTIFACT_DIR="$1"
SESSION_FILE="$ARTIFACT_DIR/chat_session_latest.txt"
ACTION_LOGS_FILE="$ARTIFACT_DIR/action_logs_latest.txt"
TOP_FILE="$ARTIFACT_DIR/top.txt"
TRUST_FILE="$ARTIFACT_DIR/trust.txt"

if [[ ! -d "$ARTIFACT_DIR" ]]; then
  echo "[inspect] fail: artifact dir not found: $ARTIFACT_DIR"
  exit 2
fi

extract_line_value() {
  local key="$1"
  local file="$2"
  awk -F' = ' -v key="$key" '{
    field=$1
    sub(/^[[:space:]]+/, "", field)
    sub(/[[:space:]]+$/, "", field)
    if (field ~ key) {
      value=$2
      sub(/^[[:space:]]+/, "", value)
      print value
      exit
    }
  }' "$file" 2>/dev/null || true
}

session_status=""
failure_reason=""
confirm_gate_state=""
current_step_index=""
app_name=""

if [[ -f "$SESSION_FILE" ]]; then
  session_status="$(extract_line_value '^status$' "$SESSION_FILE")"
  failure_reason="$(extract_line_value '^failureReason$' "$SESSION_FILE")"
  confirm_gate_state="$(extract_line_value '^confirmGateState$' "$SESSION_FILE")"
  current_step_index="$(extract_line_value '^currentStepIndex$' "$SESSION_FILE")"
  app_name="$(extract_line_value '^appName$' "$SESSION_FILE")"
fi

send_tap_count=0
confirm_log_count=0
if [[ -f "$ACTION_LOGS_FILE" ]]; then
  send_tap_count="$(grep -c 'com.samsung.android.messaging:id/send_button' "$ACTION_LOGS_FILE" || true)"
  confirm_log_count="$(grep -c 'ConfirmUser' "$ACTION_LOGS_FILE" || true)"
fi

top_focus="$(sed -n '1p' "$TOP_FILE" 2>/dev/null | sed 's/^[[:space:]]*//')"
device_locked="$(grep -Eo 'deviceLocked=[01]' "$TRUST_FILE" 2>/dev/null | head -n1 || true)"

echo "[inspect] artifact dir: $ARTIFACT_DIR"
if [[ -n "$app_name" ]]; then
  echo "[inspect] appName: $app_name"
fi
if [[ -n "$session_status" ]]; then
  echo "[inspect] session status: $session_status"
fi
if [[ -n "$confirm_gate_state" ]]; then
  echo "[inspect] confirm gate: $confirm_gate_state"
fi
if [[ -n "$current_step_index" ]]; then
  echo "[inspect] current step index: $current_step_index"
fi
if [[ -n "$failure_reason" ]]; then
  echo "[inspect] failure reason: $failure_reason"
fi
if [[ -n "$top_focus" ]]; then
  echo "[inspect] top focus: $top_focus"
fi
if [[ -n "$device_locked" ]]; then
  echo "[inspect] trust: $device_locked"
fi
echo "[inspect] confirm logs: $confirm_log_count"
echo "[inspect] send button references in action logs: $send_tap_count"

if [[ "$session_status" == "confirm required" && "$send_tap_count" -eq 0 ]]; then
  echo "[inspect] verdict: PASS candidate - stopped at confirm gate before send."
elif [[ "$session_status" == "stopped" && "$confirm_log_count" -gt 0 && "$send_tap_count" -eq 0 ]]; then
  echo "[inspect] verdict: PASS candidate - user stopped after confirm gate before send."
elif [[ "$session_status" == "completed" && "$send_tap_count" -gt 0 ]]; then
  echo "[inspect] verdict: send path executed."
elif [[ -n "$failure_reason" ]]; then
  echo "[inspect] verdict: failed before confirm/send."
else
  echo "[inspect] verdict: inconclusive."
fi
