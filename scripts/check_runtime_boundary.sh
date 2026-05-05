#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$(pwd)}"
cd "$ROOT_DIR"

RUNTIME_PATHS=(
  "app/src/main"
  "core-dsl/src/main"
  "core-policy/src/main"
  "core-runner/src/main"
  "driver-accessibility/src/main"
  "overlay-ui/src/main"
  "runtime-litertlm/src/main"
  "skill-registry/src/main"
  "data-history/src/main"
)

PATTERN='adb|adbutils|uiautomator2|Appium|appium|desktop bridge|desktop controller|shell input|input tap|input swipe|am start|mobile-use runtime'

echo "[runtime-boundary] scanning runtime source sets"

if rg -n --pcre2 "$PATTERN" "${RUNTIME_PATHS[@]}"; then
  echo
  echo "[runtime-boundary] FAILED: runtime source set contains a forbidden desktop/ADB automation term."
  echo "Tests, docs, and QA scripts may use ADB; shipped runtime source must not."
  exit 1
fi

echo "[runtime-boundary] OK: no forbidden desktop/ADB automation terms in runtime source sets."
