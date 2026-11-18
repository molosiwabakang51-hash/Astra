#!/usr/bin/env python3
"""Fail the build if the APK is missing native inference or verified GGUF."""
import hashlib
import sys
from pathlib import Path
from zipfile import ZipFile

MODEL = "assets/qwen3-0.6b-q4km.gguf"
EXPECTED_SHA256 = "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d"


def main(apk_path: Path) -> None:
    if not apk_path.is_file():
        raise RuntimeError("APK not found: " + str(apk_path))
    with ZipFile(apk_path) as apk:
        names = apk.namelist()
        if MODEL not in names:
            raise RuntimeError("APK has no bundled Qwen3 weights: " + MODEL)
        for notice in (
            "assets/open_source_licenses/Qwen3-Apache-2.0.txt",
            "assets/open_source_licenses/llama.cpp-MIT.txt",
            "assets/open_source_licenses/PdfBox-Android-Apache-2.0.txt",
            "assets/open_source_licenses/SOURCES.txt",
        ):
            if notice not in names:
                raise RuntimeError("APK missing required open-source license: " + notice)
        libraries = [name for name in names
                     if name.startswith("lib/arm64-v8a/") and name.endswith(".so")]
        if not libraries:
            raise RuntimeError("APK has no arm64 inference native libraries")
        digest = hashlib.sha256()
        with apk.open(MODEL) as source:
            while block := source.read(4 * 1024 * 1024):
                digest.update(block)
        if digest.hexdigest() != EXPECTED_SHA256:
            raise RuntimeError("The APK contains incorrect GGUF model weights")

    print("PASS: bundled on-device Qwen3 GGUF matches pinned SHA-256")
    print("PASS: native arm64 libraries included:", len(libraries))
    print("PASS: open-source model and inference notices packaged.")
    print("APK size: %.1f MiB" % (apk_path.stat().st_size / (1024 * 1024)))


if __name__ == "__main__":
    try:
        main(Path(sys.argv[1] if len(sys.argv) > 1 else
                  "android/app/build/outputs/apk/debug/app-debug.apk"))
    except Exception as error:
        sys.exit("OFFLINE APK VALIDATION FAILED: " + str(error))
