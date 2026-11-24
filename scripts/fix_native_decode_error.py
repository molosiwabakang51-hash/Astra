#!/usr/bin/env python3
"""Fail closed on native token decoding errors in pinned llama.cpp Android.

Upstream b12248's generateNextToken() returns nullptr after a failing
llama_process(). The Kotlin Flow treats nullptr as successful end-of-answer
and transitions to ModelReady, even though the native KV cache and sampler
can be inconsistent and a partial answer may have been saved.

Throw a fixed, content-free JNI IllegalStateException instead. The audited
Kotlin wrapper moves to State.Error; the next Astra request unloads and
reloads the native context rather than reusing a corrupted session.

Applied AFTER native privacy, prompt-error, generation-budget, continuity,
and memory patches. Strict match counts make the build fail on source drift.
This does not resolve a native stall or process crash.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")

BEFORE = """    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return nullptr;
    }"""

AFTER = """    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        // A native decode failure is NOT normal end-of-generation. Tell
        // Kotlin to invalidate and unload the compromised KV-cache.
        // Never expose the prompt, private weights path or token text.
        jclass failure_class = env->FindClass("java/lang/IllegalStateException");
        if (failure_class != nullptr) {
            env->ThrowNew(failure_class, "Offline native token decode failed.");
            env->DeleteLocalRef(failure_class);
        }
        return nullptr;
    }"""


def hardened(source: str) -> str:
    old, fixed = source.count(BEFORE), source.count(AFTER)
    if old == 1 and fixed == 0:
        source = source.replace(BEFORE, AFTER, 1)
    elif old == 0 and fixed == 1:
        pass
    else:
        raise RuntimeError(
            "Pinned JNI token-decode failure handler drift or duplicate; "
            f"original={old}, patched={fixed}"
        )
    if source.count("Offline native token decode failed.") != 1:
        raise RuntimeError("Native decode errors must be visible to Kotlin exactly once")
    if source.count('env->ThrowNew(failure_class, "Offline native token decode failed.");') != 1:
        raise RuntimeError("JNI decode failure is still a silent end-of-response")
    return source


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / CPP
    if not path.is_file():
        raise RuntimeError("Pinned Android JNI source is missing: " + str(path))
    original = path.read_text(encoding="utf-8")
    patched = hardened(original)
    if verify_only and patched != original:
        raise RuntimeError("JNI decode failure handler has not been applied")
    if patched != original:
        path.write_text(patched, encoding="utf-8")
    print("PASS: failed native token decode throws a content-free JNI exception", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument(
        "--upstream", type=Path,
        default=Path(__file__).resolve().parent.parent / "third_party" / "llama.cpp"
    )
    args = parser.parse_args()
    apply(args.upstream, args.verify)
