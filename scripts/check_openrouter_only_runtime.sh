#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$(pwd)}"
cd "$ROOT_DIR"

SCAN_PATHS=(
  "app/build.gradle.kts"
  "app/src/main"
  "runtime-litertlm/build.gradle.kts"
  "runtime-litertlm/src/main"
  "gradle/libs.versions.toml"
  "README.md"
  "docs/architecture.md"
  "docs/qa/adb-free-safety-hardening-report.md"
  "docs/qa/physical-golden-scenarios-2026-05-01.md"
)

FORBIDDEN='LiteRtLm(LocalAgentRuntime|RuntimeConfig|Runtime)|com\.google\.ai\.edge\.litertlm|litertlm-android|MABI_REMOTE_RUNTIME_ENABLED|MABI_LOCAL_MODEL_NAME|OpenCL|Backend\.GPU|Backend\.CPU|Gemma 4 E2B|local Gemma'

echo "[openrouter-only] scanning active runtime code, build files, and current docs"

if rg -n --pcre2 "$FORBIDDEN" "${SCAN_PATHS[@]}"; then
  echo
  echo "[openrouter-only] FAILED: local LiteRT/Gemma/OpenCL runtime references remain."
  echo "Every agent session must use OpenRouter; delete local runtime code instead of keeping fallback paths."
  exit 1
fi

echo "[openrouter-only] OK: active runtime path is OpenRouter-only."
