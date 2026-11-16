#!/usr/bin/env python3
"""Collect Astra Android crashes without changing the installed app or model.

Usage:
    python scripts/collect_astra_crash.py --output astra-crash.txt
    python scripts/collect_astra_crash.py --serial YOUR_DEVICE --output astra-crash.txt

Requires Android platform-tools (adb) in PATH. The report stays on your computer
until YOU choose to share it; review it for personal information before sharing.
This script never clears logcat, installs/uninstalls apps, opens app-private
files, runs inference, or sends network requests.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys

PACKAGE = "com.astra.agent"
MAX_LOG_LINES = 1200
MAX_REPORT_BYTES = 100_000
START = re.compile(r"FATAL EXCEPTION:|^\S+\s+\S+\s+\S+\s+\S+\s+.*\*\*\* \*\*\* \*\*\*|signal \d+ \(", re.I)
PACKAGE_LINE = re.compile(r"\bProcess:\s*com\.astra\.agent(?:,|\s|$)|>>> com\.astra\.agent <<<|\bCmdline:\s*com\.astra\.agent\b")
VERSION = re.compile(r"\bversionCode=(\d+)")
VERSION_NAME = re.compile(r"\bversionName=([^\s]+)")


def crash_excerpts(log: str, max_lines: int = 115) -> list[str]:
    """Only include crashes identifying Astra, not unrelated applications."""
    lines = log.splitlines()[-MAX_LOG_LINES:]
    starts = [index for index, line in enumerate(lines) if START.search(line)]
    segments: list[str] = []
    for pos, index in enumerate(starts):
        next_start = starts[pos + 1] if pos + 1 < len(starts) else len(lines)
        end = min(index + max_lines, next_start)
        candidate = lines[index:end]
        # A native backtrace can include package identification a few lines
        # after the signal. Never include crashes from other packages.
        if any(PACKAGE_LINE.search(line) for line in candidate[:20]):
            segments.append("\n".join(candidate))
    return segments[-3:]


def installed_version(dumpsys: str) -> str:
    code = VERSION.search(dumpsys)
    name = VERSION_NAME.search(dumpsys)
    if not code:
        return "unavailable"
    return f"{name.group(1) if name else 'unknown'} (versionCode {code.group(1)})"


def adb_read(adb: str, serial: str | None, *args: str, timeout: int = 25) -> str:
    cmd = [adb, *(["-s", serial] if serial else []), *args]
    result = subprocess.run(
        cmd, text=True, errors="replace", capture_output=True,
        timeout=timeout, check=False
    )
    if result.returncode:
        message = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"ADB read failed: {message[:250]}")
    return result.stdout


def select_device(adb: str, serial: str | None) -> str:
    output = adb_read(adb, None, "devices", "-l")
    authorized = [line.split()[0] for line in output.splitlines()[1:]
                  if "\tdevice" in line and len(line.split()) >= 2]
    if serial:
        if serial not in authorized:
            raise ValueError("Specified device not connected or not authorized for USB debugging.")
        return serial
    if len(authorized) != 1:
        raise ValueError("Connect exactly one authorized Android phone or specify --serial.")
    return authorized[0]


def build_report(version: str, crash_log: str) -> str:
    excerpts = crash_excerpts(crash_log)
    preface = (
        "Astra read-only Android crash report\n"
        "App ID: com.astra.agent\n"
        f"Installed version: {version}\n"
        "Source: ADB crash buffer (logcat -b crash -d -t 1200)\n"
        "No app data, imported GGUF, or log buffer was modified.\n"
        "Review for personal information before sharing.\n\n"
    )
    body = ("\n\n--- ASTRA CRASH ---\n\n".join(excerpts) if excerpts
            else "No Astra-specific crash found in the available crash buffer.\n"
                 "This does NOT prove that inference is stable.")
    report = preface + body + "\n"
    return report[:MAX_REPORT_BYTES]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB device serial, if multiple phones attached")
    parser.add_argument("--output", type=Path, default=Path("astra-crash.txt"),
                        help="Local diagnostic text file to create")
    args = parser.parse_args()
    adb = shutil.which("adb")
    if not adb:
        print("ADB is missing: install Android platform-tools and add adb to PATH.",
              file=sys.stderr)
        return 2
    try:
        serial = select_device(adb, args.serial)
        installed = adb_read(adb, serial, "shell", "pm", "path", PACKAGE)
        if not installed.strip():
            raise ValueError("Astra is not installed on the selected device.")
        version = installed_version(
            adb_read(adb, serial, "shell", "dumpsys", "package", PACKAGE))
        log = adb_read(adb, serial, "logcat", "-b", "crash",
                       "-d", "-t", str(MAX_LOG_LINES), timeout=35)
        report = build_report(version, log)
        args.output.write_text(report, encoding="utf-8")
        print(f"Saved Astra-only crash report to {args.output}. "
              "Review the file before sharing it.")
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as exc:
        print(f"Could not collect Astra crash report: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
