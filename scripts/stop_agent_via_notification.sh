#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <artifact-dir>"
  echo "env: QA_DEVICE_SERIAL=... QA_APP_PACKAGE=com.guribbong.phoneappagent QA_DB_NAME=phone_app_agent.db"
  exit 64
fi

ARTIFACT_DIR="$1"
QA_DEVICE_SERIAL="${QA_DEVICE_SERIAL:-${ANDROID_SERIAL:-}}"
QA_APP_PACKAGE="${QA_APP_PACKAGE:-com.guribbong.phoneappagent}"
QA_DB_NAME="${QA_DB_NAME:-phone_app_agent.db}"
STOP_TEXT="${STOP_TEXT:-Stop}"
NOTIFICATION_TITLE_PRIMARY="${NOTIFICATION_TITLE_PRIMARY:-Phone Agent}"
NOTIFICATION_TITLE_SECONDARY="${NOTIFICATION_TITLE_SECONDARY:-phone_app_agent}"
ADB_BIN="$(command -v adb || true)"

if [[ -z "$ADB_BIN" ]]; then
  echo "[stop] fail: adb not found"
  exit 2
fi

mkdir -p "$ARTIFACT_DIR"

pick_device_serial() {
  if [[ -n "$QA_DEVICE_SERIAL" ]]; then
    echo "$QA_DEVICE_SERIAL"
    return 0
  fi

  "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

copy_db_snapshot() {
  local target_dir="$1"
  "${ADB_TARGET[@]}" exec-out run-as "$QA_APP_PACKAGE" cat "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME" >"$target_dir/$QA_DB_NAME"
  if "${ADB_TARGET[@]}" shell run-as "$QA_APP_PACKAGE" test -f "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-wal"; then
    "${ADB_TARGET[@]}" exec-out run-as "$QA_APP_PACKAGE" cat "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-wal" >"$target_dir/$QA_DB_NAME-wal" 2>/dev/null || true
  fi
  if "${ADB_TARGET[@]}" shell run-as "$QA_APP_PACKAGE" test -f "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-shm"; then
    "${ADB_TARGET[@]}" exec-out run-as "$QA_APP_PACKAGE" cat "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-shm" >"$target_dir/$QA_DB_NAME-shm" 2>/dev/null || true
  fi
}

device_serial="$(pick_device_serial)"
if [[ -z "${device_serial:-}" ]]; then
  echo "[stop] fail: no connected device"
  exit 3
fi

ADB_TARGET=("$ADB_BIN" -s "$device_serial")
echo "[stop] target device: $device_serial"

dump_notification_ui() {
  local dump_name="$1"
  local target_file="$2"
  "${ADB_TARGET[@]}" shell "uiautomator dump /sdcard/${dump_name}.xml >/dev/null && cat /sdcard/${dump_name}.xml" >"$target_file"
}

find_button_center() {
  local xml_path="$1"
  local mode="$2"
  python3 - "$xml_path" "$mode" "$STOP_TEXT" "$NOTIFICATION_TITLE_PRIMARY" "$NOTIFICATION_TITLE_SECONDARY" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, mode, stop_text, title_primary, title_secondary = sys.argv[1:]
tree = ET.parse(xml_path)
root = tree.getroot()

def center(bounds: str) -> str:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return ""
    l, t, r, b = map(int, m.groups())
    return f"{(l + r) // 2} {(t + b) // 2}"

def iter_nodes(node):
    yield node
    for child in list(node):
        yield from iter_nodes(child)

titles = {title_primary, title_secondary}

if mode == "stop":
    for node in iter_nodes(root):
        if node.attrib.get("text") == stop_text:
            print(center(node.attrib.get("bounds", "")))
            sys.exit(0)
    sys.exit(1)

if mode == "expand":
    targets = []
    for frame in iter_nodes(root):
        if frame.attrib.get("class") != "android.widget.FrameLayout":
            continue
        found_title = False
        expand_button = None
        for child in iter_nodes(frame):
            text = child.attrib.get("text")
            if text in titles:
                found_title = True
            if child.attrib.get("resource-id") == "android:id/expand_button":
                expand_button = child
        if found_title and expand_button is not None:
            targets.append(expand_button)
    if not targets:
        sys.exit(1)
    print(center(targets[0].attrib.get("bounds", "")))
    sys.exit(0)

sys.exit(2)
PY
}

echo "[stop] expanding notifications"
"${ADB_TARGET[@]}" shell cmd statusbar expand-notifications
sleep 2
"${ADB_TARGET[@]}" exec-out screencap -p >"$ARTIFACT_DIR/notification-before.png"
dump_notification_ui "stop_agent_before" "$ARTIFACT_DIR/notification-before.xml"

stop_center="$(find_button_center "$ARTIFACT_DIR/notification-before.xml" stop || true)"
if [[ -z "${stop_center:-}" ]]; then
  expand_center="$(find_button_center "$ARTIFACT_DIR/notification-before.xml" expand || true)"
  if [[ -z "${expand_center:-}" ]]; then
    echo "[stop] fail: could not find Phone Agent notification expand button or Stop action"
    exit 4
  fi
  echo "[stop] expanding Phone Agent notification at $expand_center"
  "${ADB_TARGET[@]}" shell input tap $expand_center
  sleep 2
  "${ADB_TARGET[@]}" exec-out screencap -p >"$ARTIFACT_DIR/notification-expanded.png"
  dump_notification_ui "stop_agent_expanded" "$ARTIFACT_DIR/notification-expanded.xml"
  stop_center="$(find_button_center "$ARTIFACT_DIR/notification-expanded.xml" stop || true)"
fi

if [[ -z "${stop_center:-}" ]]; then
  echo "[stop] fail: could not find Stop action in expanded notification"
  exit 5
fi

echo "[stop] tapping Stop at $stop_center"
"${ADB_TARGET[@]}" shell input tap $stop_center
sleep 3
"${ADB_TARGET[@]}" shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1

"${ADB_TARGET[@]}" exec-out screencap -p >"$ARTIFACT_DIR/screen.png"
"${ADB_TARGET[@]}" shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >"$ARTIFACT_DIR/top.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" logcat -d -v brief | grep -E 'PhoneAppAgent|OpenRouterRuntime|AndroidRuntime' >"$ARTIFACT_DIR/logcat.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" shell dumpsys trust >"$ARTIFACT_DIR/trust.txt" 2>/dev/null || true

tmp_db_dir="$(mktemp -d)"
copy_db_snapshot "$tmp_db_dir"
sqlite3 "$tmp_db_dir/$QA_DB_NAME" \
  "select id,title,appName,status,updatedAtEpochMs from chat_sessions order by updatedAtEpochMs desc limit 8;" \
  >"$ARTIFACT_DIR/chat_sessions.txt"
latest_id="$(
  sqlite3 "$tmp_db_dir/$QA_DB_NAME" \
    "select id from chat_sessions order by updatedAtEpochMs desc limit 1;"
)"
printf '%s\n' "$latest_id" >"$ARTIFACT_DIR/latest_id.txt"
sqlite3 -line "$tmp_db_dir/$QA_DB_NAME" \
  "select * from chat_sessions where id = ${latest_id};" \
  >"$ARTIFACT_DIR/chat_session_latest.txt"
sqlite3 -line "$tmp_db_dir/$QA_DB_NAME" \
  "select stepIndex,actionType,selectorSummary,resultStatus,detail,observedPackage,createdAtEpochMs from agent_action_logs where sessionId = ${latest_id} order by id;" \
  >"$ARTIFACT_DIR/action_logs_latest.txt"
rm -rf "$tmp_db_dir"

echo "[stop] artifacts written to $ARTIFACT_DIR"
