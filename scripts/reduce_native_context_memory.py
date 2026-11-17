#!/usr/bin/env python3
"""Reduce the pinned Android llama.cpp context allocation for low-memory phones.

Qwen3 1.7B can be imported and SHA-256 verified yet crash during native
initialization or generation. This is a bounded memory-pressure mitigation,
not evidence that every handset crash is fixed. The pinned upstream Android
example defaults to 8192 context tokens / 512 batch tokens. Astra uses a
smaller 4096 context / 256 batch to reduce KV-cache and batch allocations.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")
OLD_CONTEXT = "constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;"
NEW_CONTEXT = "constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;"
OLD_BATCH = "constexpr int   BATCH_SIZE              = 512;"
NEW_BATCH = "constexpr int   BATCH_SIZE              = 256;"


def hardened(source: str) -> str:
    for before, after, label in (
        (OLD_CONTEXT, NEW_CONTEXT, "context"),
        (OLD_BATCH, NEW_BATCH, "batch"),
    ):
        if source.count(before) + source.count(after) != 1:
            raise RuntimeError(f"Pinned Android native {label} default drift; review upstream")
        source = source.replace(before, after, 1)
    if source.count(NEW_CONTEXT) != 1 or source.count(NEW_BATCH) != 1:
        raise RuntimeError("Android native memory policy not applied")
    return source


def apply(upstream: Path, verify_only: bool = False) -> None:
    file = upstream / CPP
    if not file.is_file():
        raise RuntimeError(f"Pinned JNI source not found: {file}")
    original = file.read_text(encoding="utf-8")
    corrected = hardened(original)
    if verify_only and corrected != original:
        raise RuntimeError("Reduced-context Android native patch has not been applied")
    if corrected != original:
        file.write_text(corrected, encoding="utf-8")
    print("PASS: Android context 4096 and batch 256 verified", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path, default=(
        Path(__file__).resolve().parent.parent / "third_party" / "llama.cpp"
    ))
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    apply(args.upstream, args.verify)
