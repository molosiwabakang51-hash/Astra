#!/usr/bin/env python3
"""Ensure partial JNI model loads are reclaimed without changing Error recovery.

Pinned Error-state cleanUp() deliberately unloads only when
_nativeModelPrepared is true. A load cancelled after llama_model_load_from_file
has begun may already hold model allocations even when prepare() has not
completed and that flag remains false.

Once fix_native_unload_safety.py has installed C++ null guards, mark native
resources as needing cleanup immediately BEFORE entering load(pathToModel).
The original Error-state branch from fix_native_prompt_error.py remains
intact, preserving its idempotent source-verification contract. Any failure
from load() or prepare() now unloads even a partially initialized model.

Only the llamaDispatcher thread writes preparation state; Stop merely sets an
atomic cancellation flag. There is no second JNI load or forced unlock.
"""
from __future__ import annotations
import argparse
from pathlib import Path

IMPL = Path(
    "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
)

BEFORE = """                _state.value = InferenceEngine.State.LoadingModel
                load(pathToModel).let {"""

AFTER = """                _state.value = InferenceEngine.State.LoadingModel
                // A JNI model allocation may exist even if load() or
                // prepare() throws. With null-safe C++ unload installed,
                // preserve Error cleanup eligibility from this point.
                _nativeModelPrepared = true
                load(pathToModel).let {"""


def hardened(source: str) -> str:
    before_count, after_count = source.count(BEFORE), source.count(AFTER)
    if before_count == 1 and after_count == 0:
        result = source.replace(BEFORE, AFTER, 1)
    elif before_count == 0 and after_count == 1:
        result = source
    else:
        raise RuntimeError(
            "Pinned partial-model-load marker drift or duplicate; "
            f"original={before_count}, patched={after_count}"
        )
    if result.count("_nativeModelPrepared = true") < 2:
        raise RuntimeError("Native model preparation state must cover failed load and successful prepare")
    required = (
        'is InferenceEngine.State.Error -> {',
        'if (_nativeModelPrepared) {',
        'unload()',
        '_nativeModelPrepared = false',
    )
    if not all(part in result for part in required):
        raise RuntimeError("Pinned native Error-state cleanup guard missing")
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
    print("PASS: Error cleanup includes partially allocated native model resources", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--upstream", type=Path, default=(
        Path(__file__).resolve().parent.parent / "third_party/llama.cpp"
    ))
    opts = parser.parse_args()
    apply(opts.upstream, opts.verify)
