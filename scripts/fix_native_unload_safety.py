#!/usr/bin/env python3
"""Idempotent native model unload for pinned Android llama.cpp b12248.

Upstream reset_long_term_states() dereferences g_context unconditionally,
even when a model load/prepare failed before a context existed. Upstream
unload() frees model/context/sampler globals without nulling them, leaving
stale pointers that can be reused after an Error-state destroy/retry.

Ensure unload is safe for a partially initialized model and a second cleanup:
skip absent KV cache, free each non-null native object once, null global
pointers immediately after free. The JNI model dispatcher/global gate remains
responsible for exclusive access. This does not make asynchronous unload
safe while llama_process is executing, nor does it cure SIGSEGV/OOM.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")

BEFORE_CONTEXT = """    if (clear_kv_cache)
        llama_memory_clear(llama_get_memory(g_context), false);"""

AFTER_CONTEXT = """    if (clear_kv_cache && g_context != nullptr)
        llama_memory_clear(llama_get_memory(g_context), false);"""

BEFORE_UNLOAD = """    // Free up resources
    common_sampler_free(g_sampler);
    g_chat_templates.reset();
    g_batch = common_batch();
    llama_free(g_context);
    llama_model_free(g_model);"""

AFTER_UNLOAD = """    // Free only live native objects. Cleanup can follow a failed model
    // load or be repeated by a lifecycle teardown. Never use stale pointers.
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    g_batch = common_batch();
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }"""


def hardened(source: str) -> str:
    result = source
    for before, after, label in (
        (BEFORE_CONTEXT, AFTER_CONTEXT, "KV cache"),
        (BEFORE_UNLOAD, AFTER_UNLOAD, "model unload"),
    ):
        old, fixed = result.count(before), result.count(after)
        if old == 1 and fixed == 0:
            result = result.replace(before, after, 1)
        elif old == 0 and fixed == 1:
            pass
        else:
            raise RuntimeError(
                f"Pinned native {label} teardown drift or duplicate: "
                f"original={old}, patched={fixed}"
            )
    for pointer in ("g_sampler", "g_context", "g_model"):
        if result.count(pointer + " = nullptr;") != 1:
            raise RuntimeError(f"Native teardown must invalidate {pointer} exactly once")
    return result


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / CPP
    if not path.is_file():
        raise RuntimeError("Pinned native teardown source is missing: " + str(path))
    original = path.read_text(encoding="utf-8")
    patched = hardened(original)
    if verify_only and patched != original:
        raise RuntimeError("Native teardown safety patch has not been applied")
    if patched != original:
        path.write_text(patched, encoding="utf-8")
    print("PASS: native model/context/sampler unload null-safe and idempotent", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--upstream", type=Path, default=(
        Path(__file__).resolve().parent.parent / "third_party/llama.cpp"
    ))
    args = parser.parse_args()
    apply(args.upstream, args.verify)
