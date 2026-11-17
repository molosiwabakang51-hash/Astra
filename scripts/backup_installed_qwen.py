#!/usr/bin/env python3
"""Read-only USB backup of an imported Astra Qwen3 1.7B GGUF.

An Android package signed with a different certificate cannot update Astra in
place. This tool can preserve the already-imported 1.7B model over USB without
downloading it again, ONLY if the currently installed APK is debuggable and
Android permits "run-as com.astra.agent". It never installs, uninstalls, clears
data, changes permissions, roots the phone or sends files to a server.

Windows CMD example (with Android platform-tools in PATH):
    python scripts/backup_installed_qwen.py --output Qwen3-1.7B-Q4_K_M.gguf

A successfully verified computer-side copy is NOT a backup of conversations.
Keep the existing Astra installation until model/chat preservation is planned.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from typing import BinaryIO, Callable

PACKAGE = "com.astra.agent"
MODEL_NAME = "Qwen3-1.7B-Q4_K_M.gguf"
MODEL_PATH = "no_backup/offline-models/" + MODEL_NAME
SHA256 = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
MIN_BYTES = 1_150_000_000
MAX_BYTES = 1_350_000_000
DISK_MARGIN = 64 * 1024 * 1024
CHUNK = 1024 * 1024


def find_adb() -> str:
    resolved = shutil.which("adb")
    if resolved:
        return resolved
    for root in (
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("LOCALAPPDATA", "") + "/Android/Sdk"
        if os.name == "nt" else None,
    ):
        if not root:
            continue
        for name in ("adb.exe", "adb"):
            candidate = Path(root) / "platform-tools" / name
            if candidate.is_file():
                return str(candidate)
    raise RuntimeError("adb unavailable. Install Android platform-tools or add it to PATH.")


def active_device(adb: str, requested: str | None) -> str:
    result = subprocess.run([adb, "devices", "-l"], capture_output=True,
                            text=True, errors="replace", timeout=20, check=False)
    if result.returncode:
        raise RuntimeError("ADB could not list devices. Check the USB connection.")
    connected = [
        parts[0] for line in result.stdout.splitlines()[1:]
        if (parts := line.split()) and len(parts) >= 2 and parts[1] == "device"
    ]
    if requested:
        if requested not in connected:
            raise RuntimeError("Requested phone is not authorised for USB debugging.")
        return requested
    if len(connected) != 1:
        raise RuntimeError("Connect exactly one authorised Android phone, or use --serial.")
    return connected[0]


def save_verified_stream(
    source: BinaryIO,
    destination: Path,
    expected_sha256: str = SHA256,
    minimum_bytes: int = MIN_BYTES,
    maximum_bytes: int = MAX_BYTES,
    completed: Callable[[], None] | None = None,
) -> int:
    """Bounded streaming copy; publishes only an exact SHA-256 match."""
    if destination.exists():
        raise FileExistsError(f"Refusing to overwrite existing file: {destination}")
    if not destination.parent.is_dir():
        raise FileNotFoundError(f"Output directory does not exist: {destination.parent}")
    stage: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb", prefix=".astra-qwen-backup-", suffix=".partial",
            dir=destination.parent, delete=False
        ) as sink:
            stage = Path(sink.name)
            digest = hashlib.sha256()
            total = 0
            while chunk := source.read(CHUNK):
                total += len(chunk)
                if total > maximum_bytes:
                    raise ValueError("Unexpected model size; refusing oversized backup.")
                digest.update(chunk)
                sink.write(chunk)
            sink.flush()
            os.fsync(sink.fileno())
        if completed:
            completed()
        if not minimum_bytes <= total <= maximum_bytes:
            raise ValueError("Incomplete or incorrectly sized model backup.")
        if digest.hexdigest() != expected_sha256.lower():
            raise ValueError("Model SHA-256 mismatch. Backup was not published.")
        # os.replace() overwrites a file created after the check above.
        # Publish using an atomic hard-link instead: link() fails with
        # FileExistsError if another process has claimed the destination.
        # Both paths are in the same directory, so no cross-volume move.
        os.link(stage, destination)
        stage.unlink()
        stage = None
        return total
    finally:
        if stage is not None:
            stage.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path,
                        help="Destination for a verified GGUF on your computer")
    parser.add_argument("--serial", help="ADB serial when multiple phones are connected")
    args = parser.parse_args()
    output = args.output.expanduser().absolute()
    if output.exists():
        print("Refusing to overwrite a local file: " + str(output), file=sys.stderr)
        return 2
    try:
        if shutil.disk_usage(output.parent).free < MAX_BYTES + DISK_MARGIN:
            raise RuntimeError("Free at least 1.42 GB on the computer for USB backup.")
        adb = find_adb()
        serial = active_device(adb, args.serial)
        prefix = [adb, "-s", serial]
        # run-as is allowed only for debuggable apps; no privileged access is
        # attempted if it is unavailable on the current installation.
        probe = subprocess.run(
            [*prefix, "shell", "run-as", PACKAGE, "ls", MODEL_PATH],
            capture_output=True, text=True, errors="replace",
            timeout=20, check=False
        )
        if probe.returncode:
            raise RuntimeError(
                "The installed Astra model is not readable through Android run-as. "
                "Your current APK may not be debuggable. Look for the original "
                "1.7B GGUF in Downloads; keep Astra installed."
            )
        process = subprocess.Popen(
            [*prefix, "exec-out", "run-as", PACKAGE, "cat", MODEL_PATH],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        try:
            if process.stdout is None:
                raise RuntimeError("ADB did not start the model backup stream.")
            def check_adb_completed() -> None:
                if process.wait(timeout=40) != 0:
                    raise RuntimeError("ADB stopped before completing the USB backup.")
            with process.stdout:
                transferred = save_verified_stream(
                    process.stdout, output, completed=check_adb_completed)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=15)
            if process.stderr is not None:
                process.stderr.close()
        print(f"VERIFIED: {transferred:,} bytes, SHA-256 {SHA256}")
        print(f"Saved to {output}. Existing Astra/model untouched.")
        print("This copy does not include chats. Do not uninstall Astra yet.")
        return 0
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as exc:
        print(f"Backup not verified: {exc}", file=sys.stderr)
        print("Keep Astra installed. No existing app data was modified.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
