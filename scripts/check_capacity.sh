#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$(pwd)}"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

echo "[capacity] filesystem"
df -h "$ROOT_DIR" /tmp

echo
echo "[capacity] large android dirs"
du -sh "$HOME/.gradle" "$HOME/.android" "$SDK_DIR" 2>/dev/null || true

echo
echo "[capacity] biggest AVDs"
du -sh "$HOME/.android/avd"/* 2>/dev/null | sort -h || true

