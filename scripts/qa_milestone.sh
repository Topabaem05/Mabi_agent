#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <milestone-name> [apk-path] [launch-component]"
  echo "env: QA_SEED_PROMPT=... QA_AUTO_QUEUE=1 QA_ENABLE_ACCESSIBILITY=1 QA_POST_LAUNCH_WAIT_SEC=12 QA_FORCE_STOP_PACKAGE=com.android.chrome"
  exit 64
fi

MILESTONE="$1"
APK_PATH="${2:-}"
LAUNCH_COMPONENT="${3-com.guribbong.phoneappagent/.MainActivity}"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
MIN_GIB_NORMAL=10
MIN_FREE_MEM_MB=300
EMULATOR_MEMORY_MB=4096
EMULATOR_CORES=4
QA_SEED_PROMPT="${QA_SEED_PROMPT:-}"
QA_AUTO_QUEUE="${QA_AUTO_QUEUE:-0}"
QA_ENABLE_ACCESSIBILITY="${QA_ENABLE_ACCESSIBILITY:-0}"
QA_ACCESSIBILITY_SERVICE="${QA_ACCESSIBILITY_SERVICE:-com.guribbong.phoneappagent/com.guribbong.phoneappagent.accessibility.AgentAccessibilityService}"
QA_POST_LAUNCH_WAIT_SEC="${QA_POST_LAUNCH_WAIT_SEC:-}"
QA_APP_PACKAGE="${QA_APP_PACKAGE:-com.guribbong.phoneappagent}"
QA_DB_NAME="${QA_DB_NAME:-phone_app_agent.db}"
QA_FORCE_STOP_PACKAGE="${QA_FORCE_STOP_PACKAGE:-}"
QA_ALLOW_FORCE_STOP_WITH_ACCESSIBILITY="${QA_ALLOW_FORCE_STOP_WITH_ACCESSIBILITY:-0}"
QA_DEVICE_SERIAL="${QA_DEVICE_SERIAL:-${ANDROID_SERIAL:-}}"
QA_CAPTURE_UIAUTOMATOR_DUMP="${QA_CAPTURE_UIAUTOMATOR_DUMP:-1}"
QA_WAIT_FOR_SESSION_TERMINAL="${QA_WAIT_FOR_SESSION_TERMINAL:-0}"
QA_SESSION_TIMEOUT_SEC="${QA_SESSION_TIMEOUT_SEC:-240}"
ARTIFACT_DIR="qa_artifacts/$MILESTONE"
SANITIZED_MILESTONE="$(printf '%s' "$MILESTONE" | tr -cs 'A-Za-z0-9_.-' '_')"
NOISY_EMULATOR_PACKAGES=(
  com.google.android.apps.wellbeing
  com.google.android.googlequicksearchbox
  com.google.android.as
  com.google.android.as.oss
)

echo "[qa] milestone: $MILESTONE"

mkdir -p "$ARTIFACT_DIR"

avail_gib="$(df -g . | awk 'NR==2 { print $4 }')"
if [[ -n "${avail_gib:-}" && "$avail_gib" -lt "$MIN_GIB_NORMAL" ]]; then
  echo "[qa] fail: only ${avail_gib}Gi free. Need >= ${MIN_GIB_NORMAL}Gi for normal emulator QA."
  exit 2
fi

echo "[qa] capacity snapshot"
"$(dirname "$0")/check_capacity.sh" "$(pwd)"

ADB_BIN="$(command -v adb || true)"
EMU_BIN="$(command -v emulator || true)"
if [[ -z "$ADB_BIN" && -x "$SDK_DIR/platform-tools/adb" ]]; then
  ADB_BIN="$SDK_DIR/platform-tools/adb"
fi
if [[ -z "$EMU_BIN" && -x "$SDK_DIR/emulator/emulator" ]]; then
  EMU_BIN="$SDK_DIR/emulator/emulator"
fi

if [[ -z "$ADB_BIN" || -z "$EMU_BIN" ]]; then
  echo "[qa] blocked: adb/emulator missing from PATH and SDK dir."
  echo "[qa] sdk dir checked: $SDK_DIR"
  exit 3
fi

pick_device_serial() {
  if [[ -n "$QA_DEVICE_SERIAL" ]]; then
    if "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }' | grep -Fxq "$QA_DEVICE_SERIAL"; then
      printf '%s\n' "$QA_DEVICE_SERIAL"
      return 0
    fi
    echo "[qa] fail: requested QA_DEVICE_SERIAL not connected: $QA_DEVICE_SERIAL" >&2
    exit 8
  fi

  "$ADB_BIN" devices |
    awk 'NR > 1 && $2 == "device" { print $1 }' |
    awk '
      BEGIN { emulator = ""; other = "" }
      /^emulator-/ { if (emulator == "") emulator = $0; next }
      { if (other == "") other = $0 }
      END {
        if (emulator != "") {
          print emulator
        } else if (other != "") {
          print other
        }
      }
    '
}

wait_for_emulator_settle() {
  local deadline focused_window free_mem_mb
  deadline=$((SECONDS + 180))
  while [[ $SECONDS -lt $deadline ]]; do
    focused_window="$("${ADB_TARGET[@]}" shell dumpsys activity activities 2>/dev/null | awk -F= '/mFocusedWindow=/{print $2; exit}')"
    free_mem_mb="$("${ADB_TARGET[@]}" shell free -m 2>/dev/null | awk 'NR == 2 { print $4; exit }')"
    if [[ "$focused_window" != *"Application Not Responding"* ]] && [[ -n "${free_mem_mb:-}" ]] && [[ "$free_mem_mb" -ge "$MIN_FREE_MEM_MB" ]]; then
      echo "[qa] emulator settled: focused=$focused_window free_mem=${free_mem_mb}MB"
      return 0
    fi
    sleep 5
  done

  echo "[qa] warn: emulator did not fully settle before deadline."
  if [[ -n "${focused_window:-}" ]]; then
    echo "[qa] last focused window: $focused_window"
  fi
  if [[ -n "${free_mem_mb:-}" ]]; then
    echo "[qa] last free mem: ${free_mem_mb}MB"
  fi
}

connected_devices="$("$ADB_BIN" devices | awk 'NR>1 && $2 == "device" { print $1 }')"
if [[ -z "$connected_devices" ]]; then
  avds_raw="$("$EMU_BIN" -list-avds)"
  IFS=$'\n' read -r -d '' -a avds < <(printf '%s\0' "$avds_raw")
  if [[ ${#avds[@]} -eq 0 ]]; then
    echo "[qa] blocked: no connected device and no AVD available."
    exit 4
  fi

  avd_name="${avds[0]}"
  echo "[qa] booting avd: $avd_name"
  "$EMU_BIN" @"$avd_name" \
    -no-window \
    -no-audio \
    -no-snapshot-load \
    -no-snapshot-save \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    -memory "$EMULATOR_MEMORY_MB" \
    -cores "$EMULATOR_CORES" >"$ARTIFACT_DIR/emulator.log" 2>&1 &

  "$ADB_BIN" wait-for-device

  boot_deadline=$((SECONDS + 240))
  while [[ $SECONDS -lt $boot_deadline ]]; do
    boot_completed="$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$boot_completed" == "1" ]]; then
      break
    fi
    sleep 2
  done

  if [[ "${boot_completed:-}" != "1" ]]; then
    echo "[qa] fail: emulator boot did not complete within 240s."
    exit 6
  fi
else
  echo "[qa] using connected device(s):"
  echo "$connected_devices"
fi

device_serial="$(pick_device_serial)"
if [[ -z "${device_serial:-}" ]]; then
  echo "[qa] fail: could not resolve a target device serial."
  exit 7
fi

ADB_TARGET=("$ADB_BIN" -s "$device_serial")
echo "[qa] target device: $device_serial"
"${ADB_TARGET[@]}" logcat -c >/dev/null 2>&1 || true

if [[ "$device_serial" == emulator-* ]]; then
  echo "[qa] reducing emulator background noise"
  "${ADB_TARGET[@]}" shell settings put global verifier_verify_adb_installs 0 >/dev/null 2>&1 || true
  "${ADB_TARGET[@]}" shell settings put global package_verifier_enable 0 >/dev/null 2>&1 || true
  for pkg in "${NOISY_EMULATOR_PACKAGES[@]}"; do
    "${ADB_TARGET[@]}" shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
  done
  "${ADB_TARGET[@]}" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  echo "[qa] waiting for launcher + free memory"
  wait_for_emulator_settle
else
  echo "[qa] post-boot settle window"
  sleep 15
fi

if [[ -n "$APK_PATH" ]]; then
  if [[ ! -f "$APK_PATH" ]]; then
    echo "[qa] fail: apk not found at $APK_PATH"
    exit 5
  fi
  "${ADB_TARGET[@]}" install --no-streaming -r "$APK_PATH"
fi

is_truthy() {
  case "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

shell_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

copy_db_snapshot() {
  local target_dir="$1"
  if [[ -z "$QA_APP_PACKAGE" || -z "$QA_DB_NAME" ]]; then
    return 1
  fi

  if ! "${ADB_TARGET[@]}" exec-out run-as "$QA_APP_PACKAGE" cat "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME" >"$target_dir/$QA_DB_NAME" 2>/dev/null; then
    return 1
  fi

  if "${ADB_TARGET[@]}" shell run-as "$QA_APP_PACKAGE" test -f "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-wal"; then
    "${ADB_TARGET[@]}" exec-out run-as "$QA_APP_PACKAGE" cat "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-wal" >"$target_dir/$QA_DB_NAME-wal" 2>/dev/null || true
  fi
  if "${ADB_TARGET[@]}" shell run-as "$QA_APP_PACKAGE" test -f "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-shm"; then
    "${ADB_TARGET[@]}" exec-out run-as "$QA_APP_PACKAGE" cat "/data/data/$QA_APP_PACKAGE/databases/$QA_DB_NAME-shm" >"$target_dir/$QA_DB_NAME-shm" 2>/dev/null || true
  fi
  return 0
}

latest_session_field() {
  local db_path="$1"
  local field="$2"
  sqlite3 "$db_path" \
    "select ${field} from chat_sessions order by updatedAtEpochMs desc limit 1;" \
    2>/dev/null || true
}

SESSION_BASELINE_ID=""
SESSION_BASELINE_UPDATED_AT=""

capture_session_baseline() {
  if ! command -v sqlite3 >/dev/null 2>&1; then
    return 0
  fi

  local tmp_db_dir
  tmp_db_dir="$(mktemp -d)"
  if copy_db_snapshot "$tmp_db_dir"; then
    SESSION_BASELINE_ID="$(latest_session_field "$tmp_db_dir/$QA_DB_NAME" "id")"
    SESSION_BASELINE_UPDATED_AT="$(latest_session_field "$tmp_db_dir/$QA_DB_NAME" "updatedAtEpochMs")"
  fi
  rm -rf "$tmp_db_dir"

  if [[ -n "${SESSION_BASELINE_ID:-}" || -n "${SESSION_BASELINE_UPDATED_AT:-}" ]]; then
    echo "[qa] session baseline id=${SESSION_BASELINE_ID:-none} updatedAt=${SESSION_BASELINE_UPDATED_AT:-none}"
  else
    echo "[qa] session baseline unavailable"
  fi
}

wait_for_latest_session_terminal() {
  if ! command -v sqlite3 >/dev/null 2>&1; then
    echo "[qa] skip terminal wait: sqlite3 not available"
    return 0
  fi

  local deadline tmp_db_dir latest_status latest_id latest_updated_at
  deadline=$((SECONDS + QA_SESSION_TIMEOUT_SEC))
  while [[ $SECONDS -lt $deadline ]]; do
    tmp_db_dir="$(mktemp -d)"
    if copy_db_snapshot "$tmp_db_dir"; then
      latest_id="$(latest_session_field "$tmp_db_dir/$QA_DB_NAME" "id")"
      latest_status="$(latest_session_field "$tmp_db_dir/$QA_DB_NAME" "status")"
      latest_updated_at="$(latest_session_field "$tmp_db_dir/$QA_DB_NAME" "updatedAtEpochMs")"
      rm -rf "$tmp_db_dir"

      if [[ -n "${latest_id:-}" || -n "${latest_status:-}" ]]; then
        echo "[qa] latest session id=${latest_id:-unknown} status=${latest_status:-unknown} updatedAt=${latest_updated_at:-unknown}"
      fi

      if [[ -n "${SESSION_BASELINE_ID:-}" || -n "${SESSION_BASELINE_UPDATED_AT:-}" ]]; then
        if [[ "${latest_id:-}" == "${SESSION_BASELINE_ID:-}" ]] &&
          [[ "${latest_updated_at:-}" == "${SESSION_BASELINE_UPDATED_AT:-}" ]]; then
          sleep 5
          continue
        fi
      fi

      case "$latest_status" in
        "completed"|"failed"|"confirm required"|"paused"|"stopped")
          return 0
          ;;
      esac
    else
      rm -rf "$tmp_db_dir"
    fi
    sleep 5
  done

  echo "[qa] warn: timed out waiting ${QA_SESSION_TIMEOUT_SEC}s for latest session to reach terminal state."
}

if is_truthy "$QA_ENABLE_ACCESSIBILITY"; then
  echo "[qa] enabling accessibility service: $QA_ACCESSIBILITY_SERVICE"
  "${ADB_TARGET[@]}" shell settings put secure enabled_accessibility_services "$QA_ACCESSIBILITY_SERVICE" >/dev/null 2>&1 || true
  "${ADB_TARGET[@]}" shell settings put secure accessibility_enabled 1 >/dev/null 2>&1 || true
  sleep 2
fi

if [[ -n "$QA_FORCE_STOP_PACKAGE" ]]; then
  if is_truthy "$QA_ENABLE_ACCESSIBILITY" &&
    [[ "$QA_FORCE_STOP_PACKAGE" == "$QA_APP_PACKAGE" ]] &&
    ! is_truthy "$QA_ALLOW_FORCE_STOP_WITH_ACCESSIBILITY"; then
    echo "[qa] skip force-stop for $QA_FORCE_STOP_PACKAGE because it can disable the accessibility service."
    echo "[qa] override with QA_ALLOW_FORCE_STOP_WITH_ACCESSIBILITY=1 if this is intentional."
  else
    echo "[qa] force-stopping package: $QA_FORCE_STOP_PACKAGE"
    "${ADB_TARGET[@]}" shell am force-stop "$QA_FORCE_STOP_PACKAGE" >/dev/null 2>&1 || true
  fi
fi

if [[ -n "$QA_SEED_PROMPT" ]] && is_truthy "$QA_AUTO_QUEUE" && is_truthy "$QA_WAIT_FOR_SESSION_TERMINAL"; then
  capture_session_baseline
fi

if [[ -n "$LAUNCH_COMPONENT" ]]; then
  "${ADB_TARGET[@]}" shell cmd statusbar collapse >/dev/null 2>&1 || true
  remote_launch_cmd="am start -W -n $(shell_quote "$LAUNCH_COMPONENT")"
  if [[ -n "$QA_SEED_PROMPT" ]]; then
    echo "[qa] launch seed prompt: $QA_SEED_PROMPT"
    remote_launch_cmd+=" --es seed_prompt $(shell_quote "$QA_SEED_PROMPT")"
    if is_truthy "$QA_AUTO_QUEUE"; then
      remote_launch_cmd+=" --ez auto_queue true"
    fi
  fi
  "${ADB_TARGET[@]}" shell "$remote_launch_cmd"
fi

post_launch_wait_sec="$QA_POST_LAUNCH_WAIT_SEC"
if [[ -z "$post_launch_wait_sec" && -n "$QA_SEED_PROMPT" ]] && is_truthy "$QA_AUTO_QUEUE"; then
  post_launch_wait_sec=12
fi
if [[ -n "$post_launch_wait_sec" && "$post_launch_wait_sec" -gt 0 ]]; then
  echo "[qa] waiting ${post_launch_wait_sec}s for seeded actions to finish"
  sleep "$post_launch_wait_sec"
fi
if [[ -n "$QA_SEED_PROMPT" ]] && is_truthy "$QA_AUTO_QUEUE" && is_truthy "$QA_WAIT_FOR_SESSION_TERMINAL"; then
  echo "[qa] waiting up to ${QA_SESSION_TIMEOUT_SEC}s for latest session to reach terminal state"
  wait_for_latest_session_terminal
fi

"${ADB_TARGET[@]}" exec-out screencap -p >"$ARTIFACT_DIR/screen.png" 2>/dev/null || true
"${ADB_TARGET[@]}" shell dumpsys accessibility >"$ARTIFACT_DIR/accessibility_pre_ui_dump.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" shell "printf 'accessibility_enabled=' && settings get secure accessibility_enabled && printf 'enabled_accessibility_services=' && settings get secure enabled_accessibility_services" \
  >"$ARTIFACT_DIR/accessibility_settings_pre_ui_dump.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" logcat -d -v brief | grep -E 'PhoneAppAgent|OpenRouterRuntime|AndroidRuntime' >"$ARTIFACT_DIR/logcat_pre_ui_dump.txt" 2>/dev/null || true
if is_truthy "$QA_CAPTURE_UIAUTOMATOR_DUMP"; then
  "${ADB_TARGET[@]}" shell "uiautomator dump /sdcard/${SANITIZED_MILESTONE}.xml >/dev/null && cat /sdcard/${SANITIZED_MILESTONE}.xml" >"$ARTIFACT_DIR/ui.xml" 2>/dev/null || true
else
  printf 'uiautomator dump skipped because QA_CAPTURE_UIAUTOMATOR_DUMP=%s\n' "$QA_CAPTURE_UIAUTOMATOR_DUMP" \
    >"$ARTIFACT_DIR/ui_dump_skipped.txt"
fi
"${ADB_TARGET[@]}" shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >"$ARTIFACT_DIR/top.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" logcat -d -v brief | grep -E 'PhoneAppAgent|OpenRouterRuntime|AndroidRuntime' >"$ARTIFACT_DIR/logcat.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" shell dumpsys trust >"$ARTIFACT_DIR/trust.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" shell dumpsys power >"$ARTIFACT_DIR/power.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" shell dumpsys accessibility >"$ARTIFACT_DIR/accessibility.txt" 2>/dev/null || true
"${ADB_TARGET[@]}" shell "printf 'accessibility_enabled=' && settings get secure accessibility_enabled && printf 'enabled_accessibility_services=' && settings get secure enabled_accessibility_services" \
  >"$ARTIFACT_DIR/accessibility_settings.txt" 2>/dev/null || true

if command -v sqlite3 >/dev/null 2>&1 && [[ -n "$QA_APP_PACKAGE" && -n "$QA_DB_NAME" ]]; then
  tmp_db_dir="$(mktemp -d)"
  if copy_db_snapshot "$tmp_db_dir"; then
    sqlite3 "$tmp_db_dir/$QA_DB_NAME" \
      "select id,title,appName,status,updatedAtEpochMs from chat_sessions order by updatedAtEpochMs desc limit 8;" \
      >"$ARTIFACT_DIR/chat_sessions.txt" 2>/dev/null || true
    latest_session_id="$(
      sqlite3 "$tmp_db_dir/$QA_DB_NAME" \
        "select id from chat_sessions order by updatedAtEpochMs desc limit 1;" \
        2>/dev/null || true
    )"
    if [[ -n "${latest_session_id:-}" ]]; then
      sqlite3 -line "$tmp_db_dir/$QA_DB_NAME" \
        "select * from chat_sessions where id = ${latest_session_id};" \
        >"$ARTIFACT_DIR/chat_session_latest.txt" 2>/dev/null || true
      sqlite3 -line "$tmp_db_dir/$QA_DB_NAME" \
        "select stepIndex,actionType,selectorSummary,resultStatus,detail,observedPackage,createdAtEpochMs from agent_action_logs where sessionId = ${latest_session_id} order by id;" \
        >"$ARTIFACT_DIR/action_logs_latest.txt" 2>/dev/null || true
    fi
  fi
  rm -rf "$tmp_db_dir"
fi

echo "[qa] ready: manual-check next"
echo "[qa] artifacts:"
echo "  - $ARTIFACT_DIR/screen.png"
echo "  - $ARTIFACT_DIR/top.txt"
echo "  - $ARTIFACT_DIR/logcat_pre_ui_dump.txt"
echo "  - $ARTIFACT_DIR/logcat.txt"
echo "  - $ARTIFACT_DIR/trust.txt"
echo "  - $ARTIFACT_DIR/power.txt"
echo "  - $ARTIFACT_DIR/accessibility_pre_ui_dump.txt"
echo "  - $ARTIFACT_DIR/accessibility_settings_pre_ui_dump.txt"
echo "  - $ARTIFACT_DIR/accessibility.txt"
echo "  - $ARTIFACT_DIR/accessibility_settings.txt"
if [[ -f "$ARTIFACT_DIR/ui.xml" ]]; then
  echo "  - $ARTIFACT_DIR/ui.xml"
fi
if [[ -f "$ARTIFACT_DIR/ui_dump_skipped.txt" ]]; then
  echo "  - $ARTIFACT_DIR/ui_dump_skipped.txt"
fi
if [[ -f "$ARTIFACT_DIR/chat_sessions.txt" ]]; then
  echo "  - $ARTIFACT_DIR/chat_sessions.txt"
fi
if [[ -f "$ARTIFACT_DIR/chat_session_latest.txt" ]]; then
  echo "  - $ARTIFACT_DIR/chat_session_latest.txt"
fi
if [[ -f "$ARTIFACT_DIR/action_logs_latest.txt" ]]; then
  echo "  - $ARTIFACT_DIR/action_logs_latest.txt"
fi
