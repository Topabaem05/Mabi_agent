#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <milestone-name> [apk-path] [launch-component]"
  echo "env: QA_DEVICE_SERIAL=... QA_UNLOCK_POLL_SEC=2 QA_UNLOCK_TIMEOUT_SEC=0"
  exit 64
fi

MILESTONE="$1"
APK_PATH="${2:-}"
LAUNCH_COMPONENT="${3-com.guribbong.phoneappagent/.MainActivity}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
QA_DEVICE_SERIAL="${QA_DEVICE_SERIAL:-${ANDROID_SERIAL:-}}"
QA_UNLOCK_POLL_SEC="${QA_UNLOCK_POLL_SEC:-2}"
QA_UNLOCK_TIMEOUT_SEC="${QA_UNLOCK_TIMEOUT_SEC:-0}"

ADB_BIN="$(command -v adb || true)"
if [[ -z "$ADB_BIN" && -x "$SDK_DIR/platform-tools/adb" ]]; then
  ADB_BIN="$SDK_DIR/platform-tools/adb"
fi
if [[ -z "$ADB_BIN" ]]; then
  echo "[unlock-watch] fail: adb missing from PATH and SDK dir."
  echo "[unlock-watch] sdk dir checked: $SDK_DIR"
  exit 3
fi

pick_device_serial() {
  if [[ -n "$QA_DEVICE_SERIAL" ]]; then
    if "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }' | grep -Fxq "$QA_DEVICE_SERIAL"; then
      printf '%s\n' "$QA_DEVICE_SERIAL"
      return 0
    fi
    echo "[unlock-watch] fail: requested QA_DEVICE_SERIAL not connected: $QA_DEVICE_SERIAL" >&2
    exit 8
  fi

  "$ADB_BIN" devices |
    awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

device_serial="$(pick_device_serial)"
if [[ -z "${device_serial:-}" ]]; then
  echo "[unlock-watch] fail: no connected device."
  exit 4
fi

ADB_TARGET=("$ADB_BIN" -s "$device_serial")
echo "[unlock-watch] target device: $device_serial"
echo "[unlock-watch] milestone: $MILESTONE"

start_epoch="$(date +%s)"
while true; do
  trust_dump="$("${ADB_TARGET[@]}" shell dumpsys trust 2>/dev/null | tr -d '\r' || true)"
  power_dump="$("${ADB_TARGET[@]}" shell dumpsys power 2>/dev/null | tr -d '\r' || true)"

  if grep -q 'deviceLocked=0' <<<"$trust_dump" && grep -q 'mWakefulness=Awake' <<<"$power_dump"; then
    echo "[unlock-watch] device unlocked and awake"
    break
  fi

  if [[ "$QA_UNLOCK_TIMEOUT_SEC" -gt 0 ]]; then
    now_epoch="$(date +%s)"
    if (( now_epoch - start_epoch >= QA_UNLOCK_TIMEOUT_SEC )); then
      echo "[unlock-watch] timeout waiting for unlock after ${QA_UNLOCK_TIMEOUT_SEC}s"
      exit 10
    fi
  fi

  echo "[unlock-watch] still locked or asleep"
  sleep "$QA_UNLOCK_POLL_SEC"
done

QA_DEVICE_SERIAL="$device_serial" \
  "$ROOT_DIR/scripts/qa_milestone.sh" "$MILESTONE" "$APK_PATH" "$LAUNCH_COMPONENT"
