#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MILESTONE="${1:-step113-messages-confirm-gate-after-unlock}"
ARTIFACT_DIR="$ROOT_DIR/qa_artifacts/$MILESTONE"
PID_FILE="$ARTIFACT_DIR/watch.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "[watch-stop] no pid file: $PID_FILE"
  exit 0
fi

pid="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
  kill "$pid" 2>/dev/null || true
  echo "[watch-stop] stopped pid=$pid"
else
  echo "[watch-stop] pid not running: ${pid:-unknown}"
fi

rm -f "$PID_FILE"
