"""Pure host-side smoke checks for the pinned Android SDK bootstrap retry path."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "install_android_sdk.sh"


class AndroidSdkBootstrapTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory(prefix="astra-sdk-ci-")
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.sdk = self.base / "android"
        self.sdkmanager = self.sdk / "cmdline-tools/latest/bin/sdkmanager"
        self.sdkmanager.parent.mkdir(parents=True)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        silent_sleep = self.bin / "sleep"
        silent_sleep.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        silent_sleep.chmod(0o755)
        (self.sdk / "ndk/28.0.0").mkdir(parents=True)
        (self.sdk / "ndk/28.0.0/OWNER_MARKER").write_text(
            "leave intact", encoding="utf-8"
        )
        self.counter = self.base / "attempts"

    def install_fake_sdkmanager(self, fail_attempts: int) -> None:
        fake = """#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == "--licenses" ]] && exit 0
[[ "$1" == "--install" ]] || exit 98
n=0
[[ -f "$FAKE_ATTEMPTS" ]] && n="$(cat "$FAKE_ATTEMPTS")"
n=$((n + 1))
printf '%s' "$n" > "$FAKE_ATTEMPTS"
if (( n <= FAKE_FAIL_UNTIL )); then
    mkdir -p "$ANDROID_HOME/.temp/PackageOperation-stale"
    mkdir -p "$ANDROID_HOME/ndk/29.0.13113456"
    exit 23
fi
mkdir -p "$ANDROID_HOME/platforms/android-36"
printf 'apk' > "$ANDROID_HOME/platforms/android-36/android.jar"
mkdir -p "$ANDROID_HOME/ndk/29.0.13113456"
printf 'ndk' > "$ANDROID_HOME/ndk/29.0.13113456/source.properties"
mkdir -p "$ANDROID_HOME/ndk/29.0.13113456/toolchains/llvm/prebuilt/linux-x86_64/bin"
printf '#!/bin/sh\\nexit 0\\n' > "$ANDROID_HOME/ndk/29.0.13113456/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
chmod +x "$ANDROID_HOME/ndk/29.0.13113456/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
mkdir -p "$ANDROID_HOME/cmake/3.31.6/bin"
printf '#!/bin/sh\\nexit 0\\n' > "$ANDROID_HOME/cmake/3.31.6/bin/cmake"
chmod +x "$ANDROID_HOME/cmake/3.31.6/bin/cmake"
"""
        self.sdkmanager.write_text(fake, encoding="utf-8")
        self.sdkmanager.chmod(0o755)
        self.fail_attempts = fail_attempts

    def run_bootstrap(self) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env.update({
            "ANDROID_HOME": str(self.sdk),
            "FAKE_ATTEMPTS": str(self.counter),
            "FAKE_FAIL_UNTIL": str(self.fail_attempts),
            "PATH": str(self.bin) + os.pathsep + env.get("PATH", ""),
        })
        return subprocess.run(
            ["bash", str(SCRIPT)], cwd=ROOT, env=env,
            text=True, capture_output=True, timeout=20, check=False
        )

    def test_script_parses_as_valid_bash(self) -> None:
        subprocess.run(["bash", "-n", str(SCRIPT)], check=True, timeout=10)

    def test_retry_recovers_after_corrupt_download_and_preserves_other_ndk(self) -> None:
        self.install_fake_sdkmanager(fail_attempts=1)
        run = self.run_bootstrap()
        self.assertEqual(0, run.returncode, run.stdout + run.stderr)
        self.assertEqual("2", self.counter.read_text())
        self.assertFalse((self.sdk / ".temp/PackageOperation-stale").exists())
        self.assertTrue((self.sdk / "ndk/28.0.0/OWNER_MARKER").exists())
        self.assertTrue((self.sdk / "cmake/3.31.6/bin/cmake").is_file())

    def test_three_real_failures_are_not_silently_accepted(self) -> None:
        self.install_fake_sdkmanager(fail_attempts=3)
        run = self.run_bootstrap()
        self.assertNotEqual(0, run.returncode)
        self.assertEqual("3", self.counter.read_text())
        self.assertIn("failed after three attempts", run.stderr)
        self.assertTrue((self.sdk / "ndk/28.0.0/OWNER_MARKER").exists())


if __name__ == "__main__":
    unittest.main()
