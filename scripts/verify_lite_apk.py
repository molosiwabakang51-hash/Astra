#!/usr/bin/env python3
"""Enforce the owner's 300 MB cap on every Astra LITE installable APK.

300 MB means 300,000,000 bytes (decimal), not 300 MiB. The APK must
retain its local ARM64 inference runtime and never bundle GGUF weights.
Size inspection is structural, not evidence of a working phone install.
"""
from pathlib import Path
from zipfile import ZipFile
import sys

MAX_APK_SIZE_BYTES = 300_000_000
# Legacy alias retained for any repository consumers importing the constant.
MAX_APK_SIZE = MAX_APK_SIZE_BYTES


def check_lite_size(size_bytes: int) -> None:
    """The cap is inclusive; a one-byte overshoot is a build failure."""
    if size_bytes <= 0:
        raise RuntimeError("Lite APK is empty")
    if size_bytes > MAX_APK_SIZE_BYTES:
        raise RuntimeError(
            "Lite APK exceeds the 300 MB maximum: "
            f"{size_bytes:,} bytes ({size_bytes / 1_000_000:.2f} MB)"
        )


def main(path: Path) -> None:
    if not path.is_file():
        raise RuntimeError("Lite APK missing: " + str(path))
    size_bytes = path.stat().st_size
    check_lite_size(size_bytes)
    with ZipFile(path) as apk:
        entries = apk.namelist()
        bundled_models = [
            name for name in entries
            if name.lower().endswith(".gguf")
        ]
        if bundled_models:
            raise RuntimeError(
                "LITE must not bundle any GGUF model weights: "
                + ", ".join(bundled_models[:3])
            )
        lib = [p for p in entries if
               p.startswith("lib/arm64-v8a/") and p.endswith(".so")]
        if not any(p.endswith("libai-chat.so") for p in lib):
            raise RuntimeError("Lite APK is missing the Android llama.cpp engine")
        for asset in ("assets/open_source_licenses/Qwen3-Apache-2.0.txt",
                      "assets/open_source_licenses/llama.cpp-MIT.txt",
                      "assets/open_source_licenses/PdfBox-Android-Apache-2.0.txt"):
            if asset not in entries:
                raise RuntimeError("Lite APK is missing license notices")
    print("PASS: model-free LITE APK with on-device ARM64 inference engine")
    print("PASS: %d ARM64 libraries, licensing notices present" % len(lib))
    print(f"PASS: LITE size {size_bytes:,} / {MAX_APK_SIZE_BYTES:,} bytes "
          f"({size_bytes / 1_000_000:.2f} MB / 300 MB maximum)")


if __name__ == "__main__":
    try:
        main(Path(sys.argv[1] if len(sys.argv) > 1 else
                  "android/app/build/outputs/apk/debug/app-debug.apk"))
    except Exception as error:
        sys.exit("LITE APK VERIFICATION FAILED: " + str(error))
