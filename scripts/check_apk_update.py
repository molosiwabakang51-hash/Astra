#!/usr/bin/env python3
"""Read-only ADB preflight for Astra APK updates.

Checks signing identity and version before suggesting an in-place install.
Never installs, uninstalls, clears data, or reads application-private files.
Requires Android SDK Build Tools (apksigner and aapt) and platform-tools (adb).
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

PACKAGE = "com.astra.agent"
CERT_RE = re.compile(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]{64,95})")
VERSION_RE = re.compile(r"\bversionCode='(\d+)'")
PACKAGE_RE = re.compile(r"\bname='([^']+)'")
INSTALLED_VERSION_RE = re.compile(r"\bversionCode=(\d+)")


def cert_fingerprint(output: str) -> str:
    # An APK may have multiple signers. Checking only signer #1 can produce
    # false compatibility when Android requires an exact signer set.
    matches = re.findall(
        r"Signer #([0-9]+) certificate SHA-256 digest:\s*([^\r\n]+)",
        output,
    )
    if len(matches) != 1 or matches[0][0] != "1":
        raise ValueError("Expected exactly one APK signing certificate.")
    fingerprint = matches[0][1].strip().replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", fingerprint):
        raise ValueError("Invalid APK certificate digest.")
    return fingerprint


def apk_identity(aapt_output: str) -> tuple[str, int]:
    line = next((line for line in aapt_output.splitlines()
                 if line.startswith("package:")), "")
    package = PACKAGE_RE.search(line)
    version = VERSION_RE.search(line)
    if not package or not version:
        raise ValueError("Could not verify APK package name and version code.")
    return package.group(1), int(version.group(1))


def installed_version(dumpsys: str) -> int:
    match = INSTALLED_VERSION_RE.search(dumpsys)
    if not match:
        raise ValueError("Could not determine installed version code.")
    return int(match.group(1))


def package_apk_path(pm_output: str) -> str:
    paths = [line.removeprefix("package:").strip()
             for line in pm_output.splitlines()
             if line.startswith("package:")]
    base = next((path for path in paths if path.endswith("/base.apk")), None)
    if not base:
        raise ValueError("Cannot locate installed Astra base APK.")
    return base


def find_tool(name: str, explicit: str | None) -> str:
    if explicit:
        candidate = Path(explicit).expanduser()
        if candidate.is_file():
            return str(candidate)
        raise ValueError(f"{name} not found at the supplied path.")
    found = shutil.which(name) or (shutil.which(name + ".bat") if os.name == "nt" else None)
    if found:
        return found
    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        str(Path.home() / "AppData/Local/Android/Sdk") if os.name == "nt" else None,
    ]
    for root in filter(None, roots):
        build_tools = Path(root) / "build-tools"
        if not build_tools.is_dir():
            continue
        candidates = sorted(build_tools.glob("*/" + name + (".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else "")), reverse=True)
        if candidates:
            return str(candidates[0])
    raise ValueError(f"{name} not found. Install Android SDK Build Tools or supply --{name}.")


def run(command: list[str], *, timeout: int = 30) -> str:
    result = subprocess.run(command, text=True, errors="replace",
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=timeout, check=False)
    if result.returncode:
        message = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"Command failed ({result.returncode}): {message[:500]}")
    return result.stdout


def check(apk: Path, adb: str, apksigner: str, aapt: str,
          serial: str | None = None) -> tuple[bool, str]:
    if not apk.is_file():
        raise ValueError("Replacement APK was not found.")
    adb_cmd = [adb] + (["-s", serial] if serial else [])
    devices = run([adb, "devices", "-l"])
    attached = [line for line in devices.splitlines()[1:]
                if "\tdevice " in line or line.endswith("\tdevice")]
    if serial:
        if not any(line.split()[0] == serial for line in attached):
            raise ValueError("Selected device is not connected and authorized.")
    elif len(attached) != 1:
        raise ValueError("Connect exactly one authorized Android device or pass --serial.")

    new_certs = run([apksigner, "verify", "--print-certs", str(apk)])
    new_cert = cert_fingerprint(new_certs)
    package, new_version = apk_identity(run([aapt, "dump", "badging", str(apk)]))
    if package != PACKAGE:
        return False, f"BLOCKED: APK package is {package}, not {PACKAGE}."

    pm_output = run(adb_cmd + ["shell", "pm", "path", PACKAGE])
    if not pm_output.strip():
        # This tool is an update-safety checker, not an installer. A fresh
        # install cannot preserve application-private chats or model weights.
        return False, ("BLOCKED: Astra is not currently installed. "
                       "A fresh installation cannot recover existing app-private "
                       "chats or imported GGUF weights. Verify your backup and "
                       "model source before proceeding separately.")
    try:
        path = package_apk_path(pm_output)
    except ValueError as exc:
        return False, f"BLOCKED: {exc}"
    with tempfile.TemporaryDirectory(prefix="astra-apk-check-") as tmp:
        local_copy = Path(tmp) / "installed-base.apk"
        # Pulls only the public installed base APK; does not access app data.
        run(adb_cmd + ["pull", path, str(local_copy)], timeout=90)
        old_cert = cert_fingerprint(
            run([apksigner, "verify", "--print-certs", str(local_copy)]))
    if old_cert != new_cert:
        return False, ("BLOCKED: installed and replacement APK certificates differ. "
                       "Do not uninstall or clear storage: the imported GGUF and "
                       "local chats could be lost. Use a matching signing key.")
    existing_version = installed_version(
        run(adb_cmd + ["shell", "dumpsys", "package", PACKAGE]))
    if new_version < existing_version:
        return False, (f"BLOCKED: APK versionCode {new_version} is older than "
                       f"installed versionCode {existing_version}.")
    return True, (f"COMPATIBLE: signing certificate matches and APK versionCode "
                  f"{new_version} >= installed {existing_version}. "
                  f"An in-place update may be attempted with adb install -r; "
                  f"this checker has not installed anything.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="Astra LITE APK to check")
    parser.add_argument("--adb", help="Path to adb executable")
    parser.add_argument("--apksigner", help="Path to apksigner")
    parser.add_argument("--aapt", help="Path to aapt")
    parser.add_argument("--serial", help="ADB serial for a specific phone")
    args = parser.parse_args()
    try:
        adb = find_tool("adb", args.adb)
        signer = find_tool("apksigner", args.apksigner)
        aapt = find_tool("aapt", args.aapt)
        safe, message = check(args.apk, adb, signer, aapt, args.serial)
        print(message)
        return 0 if safe else 2
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"BLOCKED: preflight could not verify update safety: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
