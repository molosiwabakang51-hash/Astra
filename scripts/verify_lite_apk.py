#!/usr/bin/env python3
"""Check the Astra small-update APK has native local AI but no bundled GGUF.

This is a structural build check. A real Android phone is still needed to
verify GGUF import, model token generation and update installation.
"""
from pathlib import Path
from zipfile import ZipFile
import sys

MAX_APK_SIZE = 150 * 1024 * 1024
MODEL_NAME = "assets/qwen3-0.6b-q4km.gguf"


def main(path: Path) -> None:
    if not path.is_file():
        raise RuntimeError("Lite APK missing: " + str(path))
    if path.stat().st_size >= MAX_APK_SIZE:
        raise RuntimeError("Lite APK too large: %.1f MiB" %
                           (path.stat().st_size / 1048576))
    with ZipFile(path) as apk:
        entries = apk.namelist()
        if MODEL_NAME in entries:
            raise RuntimeError("The model was accidentally packaged in the lite APK")
        lib = [p for p in entries if
               p.startswith("lib/arm64-v8a/") and p.endswith(".so")]
        if not any(p.endswith("libai-chat.so") for p in lib):
            raise RuntimeError("Lite APK is missing the Android llama.cpp engine")
        for asset in ("assets/open_source_licenses/Qwen3-Apache-2.0.txt",
                      "assets/open_source_licenses/llama.cpp-MIT.txt"):
            if asset not in entries:
                raise RuntimeError("Lite APK is missing license notices")
    print("PASS: model-free lite APK, with on-device inference engine")
    print("PASS: %d ARM64 libraries, licensing notices present" % len(lib))
    print("Lite APK size: %.1f MiB" % (path.stat().st_size / 1048576))


if __name__ == "__main__":
    try:
        main(Path(sys.argv[1] if len(sys.argv) > 1 else
                  "android/app/build/outputs/apk/debug/app-debug.apk"))
    except Exception as error:
        sys.exit("LITE APK VERIFICATION FAILED: " + str(error))
