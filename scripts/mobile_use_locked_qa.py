#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
import os
import subprocess
from pathlib import Path

from minitap.mobile_use.main import run_automation


def capture_screen(artifact_dir: Path) -> None:
    screenshot_path = artifact_dir / "screen.png"
    with screenshot_path.open("wb") as screenshot:
        subprocess.run(
            ["adb", "exec-out", "screencap", "-p"],
            check=False,
            stdout=screenshot,
            stderr=subprocess.DEVNULL,
        )


def capture_top_window(artifact_dir: Path) -> None:
    top = subprocess.run(
        ["adb", "shell", "dumpsys", "window"],
        check=False,
        capture_output=True,
        text=True,
    )
    filtered = [
        line
        for line in top.stdout.splitlines()
        if "mCurrentFocus" in line or "mFocusedApp" in line
    ]
    (artifact_dir / "top.txt").write_text("\n".join(filtered) + ("\n" if filtered else ""), encoding="utf-8")


def capture_logcat(artifact_dir: Path) -> None:
    logcat = subprocess.run(
        ["adb", "logcat", "-d", "-v", "brief"],
        check=False,
        capture_output=True,
        text=True,
    )
    interesting = [
        line
        for line in logcat.stdout.splitlines()
        if "AndroidRuntime" in line
        or "UiAutomation" in line
        or "uiautomator" in line.lower()
        or "ollama" in line.lower()
    ]
    (artifact_dir / "logcat.txt").write_text(
        "\n".join(interesting) + ("\n" if interesting else ""),
        encoding="utf-8",
    )


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--goal", required=True)
    parser.add_argument("--locked-app-package", required=True)
    parser.add_argument("--artifact-dir", required=True)
    parser.add_argument("--test-name", required=True)
    parser.add_argument("--output-description", default="A short plain-text result.")
    args = parser.parse_args()

    artifact_dir = Path(args.artifact_dir).resolve()
    artifact_dir.mkdir(parents=True, exist_ok=True)

    os.environ.setdefault("MOBILE_USE_TELEMETRY_ENABLED", "false")
    os.environ.setdefault("EVENTS_OUTPUT_PATH", str(artifact_dir / "events.json"))
    os.environ.setdefault("RESULTS_OUTPUT_PATH", str(artifact_dir / "results.json"))

    await run_automation(
        goal=args.goal,
        locked_app_package=args.locked_app_package,
        test_name=args.test_name,
        traces_output_path_str=str(artifact_dir / "traces"),
        output_description=args.output_description,
    )

    capture_screen(artifact_dir)
    capture_top_window(artifact_dir)
    capture_logcat(artifact_dir)


if __name__ == "__main__":
    asyncio.run(main())
