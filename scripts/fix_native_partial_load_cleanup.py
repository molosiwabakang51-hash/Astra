#!/usr/bin/env python3
"""Discard partially allocated native resources after any model-loader error.

Pinned InferenceEngineImpl.cleanUp() only unloaded State.Error when
_nativeModelPrepared == true. During a failed/cancelled GGUF load or prepare,
g_model (and sometimes g_context) can already be non-null even while
_nativeModelPrepared is false. The next 1.7B retry would retain that previous
allocation and risk memory exhaustion / stale model state.

Only after fix_native_unload_safety.py has installed null-safe C++ cleanup,
convert Error cleanup to always call native unload(). That handler runs on
the existing llamaDispatcher and retains Astra's global native ownership
mutex. Unload never runs concurrently with active JNI decode or load.

The code must not treat cancellation as a corrupt model: it preserves the
verified GGUF file and causes Astra to reload a clean native context.
"""
from __future__ import annotations
import argparse
from pathlib import Path

IMPL = Path(
    "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
)

BEFORE = """                is InferenceEngine.State.Error -> {
                    Log.i(TAG, "Resetting error states...")
                    // If prompt generation failed after a successful load,
                    // discard the native context and its corrupted KV-cache.
                    if (_nativeModelPrepared) {
                        unload()
                        _nativeModelPrepared = false
                    }
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.Initialized
                    Log.i(TAG, "States reset!")
                    Unit
                }"""

AFTER = """                is InferenceEngine.State.Error -> {
                    Log.i(TAG, "Resetting error states...")
                    // Load or prepare can fail with a model or context
                    // already allocated, before _nativeModelPrepared becomes
                    // true. The pinned C++ null-safe unload frees every live
                    // allocation exactly once, including partial sessions.
                    unload()
                    _nativeModelPrepared = false
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.Initialized
                    Log.i(TAG, "States reset!")
                    Unit
                }"""


def hardened(source: str) -> str:
    before_count, after_count = source.count(BEFORE), source.count(AFTER)
    if before_count == 1 and after_count == 0:
        result = source.replace(BEFORE, AFTER, 1)
    elif before_count == 0 and after_count == 1:
        result = source
    else:
        raise RuntimeError(
            "Pinned Error-state cleanup drift or duplicate; "
            f"original={before_count}, patched={after_count}"
        )
    if result.count("_nativeModelPrepared = false") < 2:
        raise RuntimeError("Native preparation state must be invalidated on cleanup")
    return result


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / IMPL
    if not path.is_file():
        raise RuntimeError("Pinned native Error cleanup source missing: " + str(path))
    original = path.read_text(encoding="utf-8")
    patched = hardened(original)
    if verify_only and patched != original:
        raise RuntimeError("Native partial load cleanup patch was not applied")
    if patched != original:
        path.write_text(patched, encoding="utf-8")
    print("PASS: Error state frees partially allocated native model and context", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--upstream", type=Path, default=(
        Path(__file__).resolve().parent.parent / "third_party/llama.cpp"
    ))
    opts = parser.parse_args()
    apply(opts.upstream, opts.verify)
