#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: $0 <milestone-name> <locked-app-package> <goal>"
  exit 64
fi

MILESTONE="$1"
LOCKED_APP_PACKAGE="$2"
GOAL="$3"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MOBILE_USE_DIR="$ROOT_DIR/.mobile-use-src"
ARTIFACT_DIR="$ROOT_DIR/qa_artifacts/$MILESTONE"
MIN_GIB_NORMAL=10

avail_gib="$(df -g "$ROOT_DIR" | awk 'NR==2 { print $4 }')"
if [[ -n "${avail_gib:-}" && "$avail_gib" -lt "$MIN_GIB_NORMAL" ]]; then
  echo "[mobile-use-qa] fail: only ${avail_gib}Gi free. Need >= ${MIN_GIB_NORMAL}Gi."
  exit 2
fi

"$ROOT_DIR/scripts/check_capacity.sh" "$ROOT_DIR"
mkdir -p "$ARTIFACT_DIR"

if [[ ! -x "$MOBILE_USE_DIR/.venv/bin/python" ]]; then
  echo "[mobile-use-qa] fail: mobile-use venv missing at $MOBILE_USE_DIR/.venv/bin/python"
  exit 3
fi

cd "$MOBILE_USE_DIR"
"$MOBILE_USE_DIR/.venv/bin/python" \
  "$ROOT_DIR/scripts/mobile_use_locked_qa.py" \
  --goal "$GOAL" \
  --locked-app-package "$LOCKED_APP_PACKAGE" \
  --artifact-dir "$ARTIFACT_DIR" \
  --test-name "$MILESTONE"

echo "[mobile-use-qa] artifacts:"
echo "  - $ARTIFACT_DIR/events.json"
echo "  - $ARTIFACT_DIR/results.json"
echo "  - $ARTIFACT_DIR/top.txt"
echo "  - $ARTIFACT_DIR/logcat.txt"
echo "  - $ARTIFACT_DIR/screen.png"
