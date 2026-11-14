#!/usr/bin/env python3
"""Prepare pinned llama.cpp Android binding and bundle Qwen3 GGUF with Astra.

Run before ./gradlew testDebugUnitTest assembleDebug, also in GitHub Actions.
Network is required ONLY on the build machine, never by the installed app.
No model weights or generated native binaries are committed to Git.
"""
from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path
from harden_native_inference import apply_privacy_patch
from fix_native_prompt_error import apply as patch_native_prompt_error
from fix_native_generation_budget import apply as patch_native_generation_budget
from fix_native_chat_continuity import apply as patch_native_chat_continuity

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = ROOT / "third_party" / "llama.cpp"
LIB_DIR = UPSTREAM / "examples" / "llama.android" / "lib"
GRADLE_SRC = ROOT / "android" / "llama-library.gradle.kts"
MODEL_NAME = "qwen3-0.6b-q4km.gguf"
MODEL_PATH = ROOT / "android" / "app" / "src" / "main" / "assets" / MODEL_NAME

# Release b12248; prevents source code from drifting between builds.
LLAMA_TAG = "b12248"
LLAMA_COMMIT_PREFIX = "3837bf0"
MODEL_URL = (
    "https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/main/"
    "Qwen3-0.6B.Q4_K_M.gguf"
)
MODEL_SHA256 = "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d"
MIN_MODEL_BYTES = 450_000_000


def check_model(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < MIN_MODEL_BYTES:
        return False
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while data := stream.read(1024 * 1024 * 4):
            digest.update(data)
    return digest.hexdigest() == MODEL_SHA256


def prepare_binding() -> None:
    if not UPSTREAM.exists():
        UPSTREAM.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([
            "git", "clone", "--depth", "1", "--branch", LLAMA_TAG,
            "https://github.com/ggml-org/llama.cpp.git", str(UPSTREAM)
        ], check=True)
    commit = subprocess.check_output(
        ["git", "-C", str(UPSTREAM), "rev-parse", "HEAD"], text=True
    ).strip()
    if not commit.startswith(LLAMA_COMMIT_PREFIX):
        raise RuntimeError("Wrong llama.cpp checkout: " + commit)
    if not (LIB_DIR / "src/main/cpp/CMakeLists.txt").is_file():
        raise RuntimeError("Upstream Android JNI module is missing.")
    shutil.copyfile(GRADLE_SRC, LIB_DIR / "build.gradle.kts")
    apply_privacy_patch(UPSTREAM)
    patch_native_prompt_error(UPSTREAM)
    patch_native_generation_budget(UPSTREAM)
    patch_native_chat_continuity(UPSTREAM)
    print("Pinned native Android inference binding:", commit, flush=True)


def prepare_model() -> None:
    if check_model(MODEL_PATH):
        print("Verified bundled model:", MODEL_PATH, flush=True)
        return
    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    partial = MODEL_PATH.with_name(MODEL_NAME + ".partial")
    partial.unlink(missing_ok=True)
    try:
        request = urllib.request.Request(
            MODEL_URL, headers={"User-Agent": "Astra-offline-Android-build"}
        )
        with urllib.request.urlopen(request, timeout=120) as source:
            with partial.open("wb") as sink:
                shutil.copyfileobj(source, sink, length=4 * 1024 * 1024)
        if not check_model(partial):
            raise RuntimeError("Offline GGUF SHA-256 mismatch; refusing APK build.")
        partial.replace(MODEL_PATH)
    finally:
        partial.unlink(missing_ok=True)
    print("Verified and bundled model:", MODEL_PATH, flush=True)


def prepare_licenses() -> None:
    """Include full model/native-library license notices inside the offline APK."""
    from urllib.request import urlopen
    notices = MODEL_PATH.parent / "open_source_licenses"
    notices.mkdir(parents=True, exist_ok=True)

    upstream_license = UPSTREAM / "LICENSE"
    if not upstream_license.is_file():
        raise RuntimeError("Pinned llama.cpp MIT license missing.")
    shutil.copyfile(upstream_license, notices / "llama.cpp-MIT.txt")

    upstream_dependencies = UPSTREAM / "licenses"
    if upstream_dependencies.is_dir():
        shutil.copytree(
            upstream_dependencies, notices / "llama.cpp-dependencies",
            dirs_exist_ok=True
        )

    qwen_license = notices / "Qwen3-Apache-2.0.txt"
    if not qwen_license.exists():
        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        with urlopen(url, timeout=30) as source:
            license_data = source.read(32_768)
        if (not license_data.lstrip().startswith(b"Apache License") or
                b"END OF TERMS AND CONDITIONS" not in license_data):
            raise RuntimeError("Model license download is invalid.")
        qwen_license.write_bytes(license_data)

    (notices / "SOURCES.txt").write_text(
        "Qwen3 0.6B original model: https://huggingface.co/Qwen/Qwen3-0.6B\\n"
        "QuantFactory Q4_K_M GGUF: "
        "https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF\\n"
        "Qwen3 model weights: Apache License 2.0.\\n"
        "llama.cpp native runtime: https://github.com/ggml-org/llama.cpp\\n"
        "llama.cpp: MIT license; dependency notices are included in this folder.\\n",
        encoding="utf-8"
    )
    print("Packaged model and inference open-source licenses.", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--lite", action="store_true",
        help="Build small APK with native model engine but no GGUF assets."
    )
    opts = parser.parse_args()
    prepare_binding()
    if opts.lite:
        MODEL_PATH.unlink(missing_ok=True)
        print("Lite build: GGUF omitted from APK.", flush=True)
    else:
        prepare_model()
    prepare_licenses()
    if not opts.lite and not check_model(MODEL_PATH):
        raise RuntimeError("Bundled model verification failed.")
    print("Android offline inference build preparation complete.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Offline build preparation failed:", error, file=sys.stderr)
        sys.exit(1)
