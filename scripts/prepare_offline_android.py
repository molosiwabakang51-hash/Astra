#!/usr/bin/env python3
"""Prepare pinned llama.cpp Android binding and bundle Qwen3 GGUF with Astra.

Run before ./gradlew testDebugUnitTest assembleDebug, also in GitHub Actions.
Network is required ONLY on the build machine, never by the installed app.
No model weights or generated native binaries are committed to Git.
"""
from __future__ import annotations

import hashlib
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

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


def main() -> None:
    prepare_binding()
    prepare_model()
    if not check_model(MODEL_PATH):
        raise RuntimeError("Bundled model verification failed.")
    print("Offline build prerequisites ready. Phone will require NO model download.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Offline build preparation failed:", error, file=sys.stderr)
        sys.exit(1)
