#!/usr/bin/env python3
"""Remove prompt and token-content logging from pinned llama.cpp Android JNI.

Applied at build time to the pinned upstream tree. Both FULL and LITE variants
use the same hardened native runtime. Fail closed if upstream log sites drift:
a model update must not silently reintroduce private conversation logging.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP_PATH = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")
KT_PATH = Path(
    "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
)

# (original marker, sanitized replacement statement/comment, expected occurrences)
CPP_RULES = (
    ("Loading model from:", 'LOGd("%s: Model file selected", __func__);', 1),
    ("Formatted and added %s message:", 'LOGd("%s: Message formatted", __func__);', 1),
    ("System prompt received:", 'LOGd("%s: System prompt supplied", __func__);', 1),
    ("User prompt received:", 'LOGd("%s: User prompt supplied", __func__);', 1),
    ("common_token_to_piece(g_context, id).c_str()",
     "// PRIVATE: token content is deliberately not logged.", 2),
    ("new_token_chars.c_str()",
     "// PRIVATE: generated token text is deliberately not logged.", 1),
    ('LOGv("id: %d,', "// PRIVATE: per-token log suppressed.", 1),
)
KT_RULES = (
    ('"Checking access to model file...', 'Log.i(TAG, "Checking local model file access")', 1),
    ('"Loading model...', 'Log.i(TAG, "Loading local model")', 1),
    ('(e.message ?: "Error loading model")',
     'Log.e(TAG, "Local model loading failed: " + e.javaClass.simpleName)', 1),
    ('"Native library loaded! System info:',
     'Log.i(TAG, "Native runtime initialised")', 1),
    ('"Failed to load native library from $nativeLibDir"',
     'Log.e(TAG, "Cannot load local native runtime")', 1),
    ('"Failed to load native library", e',
     'Log.e(TAG, "Local native runtime initialisation failed")', 1),
    ('"Error during generation!", e',
     'Log.e(TAG, "Local generation failed: " + e.javaClass.simpleName)', 1),
)

# Assert no remaining logging of any raw messages after the rewrite.
FORBIDDEN = (
    "Loading model from:", "Formatted and added %s message:",
    "System prompt received:", "User prompt received:",
    "common_token_to_piece(g_context, id).c_str()",
    "new_token_chars.c_str()",
    '"Checking access to model file...', '"Loading model...',
    '(e.message ?: "Error loading model")',
    '"Native library loaded! System info:',
    '"Failed to load native library from $nativeLibDir"',
    '"Failed to load native library", e',
    '"Error during generation!", e',
)


def patch_lines(source: str, rules: tuple[tuple[str, str, int], ...]) -> str:
    lines = source.splitlines(keepends=True)
    for needle, replacement, count in rules:
        hits = [i for i, line in enumerate(lines) if needle in line]
        if hits:
            if len(hits) != count:
                raise RuntimeError(
                    f"Sensitive upstream logger {needle!r} appeared "
                    f"{len(hits)} times; expected {count}."
                )
            for index in hits:
                old = lines[index]
                indent = old[:len(old) - len(old.lstrip())]
                ending = "\r\n" if old.endswith("\r\n") else (
                    "\n" if old.endswith("\n") else ""
                )
                lines[index] = indent + replacement + ending
        elif sum(replacement in line for line in lines) != count:
            raise RuntimeError(
                f"Sensitive upstream logger changed: {needle!r}. "
                "Review the pinned JNI library before shipping."
            )
    result = "".join(lines)
    for forbidden in FORBIDDEN:
        if forbidden in result:
            raise RuntimeError(f"Potential private data log remains: {forbidden}")
    return result


def apply_privacy_patch(upstream: Path, verify_only: bool = False) -> None:
    for relative, rules in ((CPP_PATH, CPP_RULES), (KT_PATH, KT_RULES)):
        path = upstream / relative
        if not path.is_file():
            raise RuntimeError("Pinned JNI privacy source not found: " + str(relative))
        original = path.read_text(encoding="utf-8")
        secured = patch_lines(original, rules)
        if verify_only and secured != original:
            raise RuntimeError("Native source was not hardened: " + str(relative))
        if secured != original:
            path.write_text(secured, encoding="utf-8")
        print("PASS: no user prompt/token logs in", relative, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument(
        "--upstream", type=Path,
        default=Path(__file__).resolve().parent.parent / "third_party/llama.cpp"
    )
    arguments = parser.parse_args()
    apply_privacy_patch(arguments.upstream, arguments.verify)
