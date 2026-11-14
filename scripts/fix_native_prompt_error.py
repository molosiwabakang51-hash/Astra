#!/usr/bin/env python3
"""Correct fail-open error states in the pinned llama.cpp Android JNI wrapper.

The pinned binding leaves State.ProcessingUserPrompt permanently set when its
native prompt-processing function returns nonzero. With Astra's state-waiting
client, the next request then times out instead of recovering.

Patch only reviewed, exact upstream snippets; fail closed if the upstream
implementation changes. The patch is idempotent and applied after log redaction.
"""
from __future__ import annotations

import argparse
from pathlib import Path

IMPL = Path(
    "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
)

REWRITES = (
    (
        """    private var _readyForSystemPrompt = false
    @Volatile
    private var _cancelGeneration = false""",
        """    private var _readyForSystemPrompt = false
    // Native unload() is only safe after both load() and prepare() succeeded.
    // Error recovery must release a fully prepared model, but must not call
    // unload() on partially initialised native pointers.
    private var _nativeModelPrepared = false
    @Volatile
    private var _cancelGeneration = false""",
    ),
    (
        """                _readyForSystemPrompt = false
                _state.value = InferenceEngine.State.LoadingModel""",
        """                _readyForSystemPrompt = false
                _nativeModelPrepared = false
                _state.value = InferenceEngine.State.LoadingModel""",
    ),
    (
        """                prepare().let {
                    if (it != 0) throw IOException("Failed to prepare resources")
                }
                Log.i(TAG, "Model loaded!")""",
        """                prepare().let {
                    if (it != 0) throw IOException("Failed to prepare resources")
                }
                _nativeModelPrepared = true
                Log.i(TAG, "Model loaded!")""",
    ),
    (
        """                    Log.e(TAG, "Failed to process user prompt: $result")
                    return@flow""",
        """                    // Returning here silently strands the JNI wrapper in
                    // ProcessingUserPrompt. Throw so the existing catch sets
                    // State.Error, allowing Astra to reset and retry.
                    throw IllegalStateException("Native prompt processing failed (code $result)")""",
    ),
    (
        """                    unload()

                    _state.value = InferenceEngine.State.Initialized""",
        """                    unload()
                    _nativeModelPrepared = false

                    _state.value = InferenceEngine.State.Initialized""",
    ),
    (
        """                is InferenceEngine.State.Error -> {
                    Log.i(TAG, "Resetting error states...")
                    _state.value = InferenceEngine.State.Initialized
                    Log.i(TAG, "States reset!")
                    Unit
                }""",
        """                is InferenceEngine.State.Error -> {
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
                }""",
    ),
)

GUARDS = (
    "Native prompt processing failed (code $result)",
    "_nativeModelPrepared = true",
    "if (_nativeModelPrepared) {",
    "unload()\n                        _nativeModelPrepared = false",
    "_state.value = InferenceEngine.State.Error(e)",
)


def hardened(source: str) -> str:
    out = source
    for before, after in REWRITES:
        original_count, fixed_count = out.count(before), out.count(after)
        if original_count == 1 and fixed_count == 0:
            out = out.replace(before, after, 1)
        elif original_count == 0 and fixed_count == 1:
            pass  # Already patched; never apply twice.
        else:
            raise RuntimeError(
                "Pinned JNI error-recovery source drift or duplicated marker: "
                + repr(before[:100])
            )
    if any(marker not in out for marker in GUARDS):
        raise RuntimeError("Missing required native error-recovery guard")
    if 'Log.e(TAG, "Failed to process user prompt: $result")' in out:
        raise RuntimeError("Silent failed-prompt path remains")
    return out


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / IMPL
    if not path.is_file():
        raise RuntimeError("Pinned JNI Kotlin file missing: " + str(path))
    source = path.read_text(encoding="utf-8")
    patched = hardened(source)
    if verify_only and patched != source:
        raise RuntimeError("Native prompt error recovery was not applied")
    if patched != source:
        path.write_text(patched, encoding="utf-8")
    print("PASS: native prompt failure throws; Error cleanup resets prepared model", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--upstream", type=Path,
        default=Path(__file__).resolve().parent.parent / "third_party/llama.cpp")
    args = parser.parse_args()
    apply(args.upstream, args.verify)
