#!/usr/bin/env python3
"""Correct pinned llama.cpp Android JNI generation and context-position limits.

Upstream b12248 advances current_position by the decoded user prompt length,
then *adds that length a second time* when computing the output stop position.
Astra asks for 384 generated tokens, but JNI can generate 384 + user_tokens,
wasting battery, increasing latency and potentially exhausting its context.

Upstream also records user_prompt_size BEFORE truncating oversized prompts;
correct that position accounting to use the number of tokens actually decoded.

Fail closed on upstream drift; run after native privacy and error-state patches.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")

BEFORE = (
    """    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if (user_prompt_size > max_batch_size) {
        const int skipped_tokens = user_prompt_size - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Decode user tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    current_position += user_prompt_size;
    stop_generation_position = current_position + user_prompt_size + n_predict;
    return 0;"""
)

AFTER = (
    """    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int original_user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if (original_user_prompt_size > max_batch_size) {
        const int skipped_tokens = original_user_prompt_size - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Count only decoded tokens; the original prompt may have been truncated.
    const int user_prompt_size = (int) user_tokens.size();
    // Decode user tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position exactly once for the user prompt.
    current_position += user_prompt_size;
    // n_predict is the number of assistant tokens AFTER the decoded prompt.
    stop_generation_position = current_position + n_predict;
    return 0;"""
)

# The v0.12.0 continuity patch replaces the positional budget with a
# countdown but must preserve this precise correction to truncated prompt
# accounting. Accept that *fully reviewed* downstream result, not arbitrary
# text that happens to contain a new counter name.
AFTER_CONTINUITY = AFTER.replace(
    "    stop_generation_position = current_position + n_predict;",
    "    // Bound assistant tokens, not absolute positions after a shifting context.\n"
    "    remaining_generation_tokens = std::max(0, static_cast<int>(n_predict));",
)


def hardened(source: str) -> str:
    original_count = source.count(BEFORE)
    patched_count = source.count(AFTER)
    continuity_count = source.count(AFTER_CONTINUITY)
    if (original_count, patched_count, continuity_count) == (1, 0, 0):
        out = source.replace(BEFORE, AFTER, 1)
    elif (original_count, patched_count, continuity_count) == (0, 1, 0):
        out = source
    elif (original_count, patched_count, continuity_count) == (0, 0, 1):
        # Recheck the rest of the native chat patch as well. A build must not
        # accept a partial or tampered migration from positional to counted
        # generation limits.
        from fix_native_chat_continuity import hardened as verify_continuity
        if verify_continuity(source) != source:
            raise RuntimeError("Native chat continuity patch is incomplete")
        return source
    else:
        raise RuntimeError(
            "Pinned native generation-budget source drift or duplicated blocks; "
            f"original={original_count}, patched={patched_count}, "
            f"continuity={continuity_count}. Review upstream."
        )
    if "stop_generation_position = current_position + user_prompt_size + n_predict;" in out:
        raise RuntimeError("Native generation still double counts the user prompt")
    if out.count("stop_generation_position = current_position + n_predict;") != 1:
        raise RuntimeError("Native assistant-token limit was not installed exactly once")
    return out


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / CPP
    if not path.is_file():
        raise RuntimeError("Pinned llama.cpp JNI source is missing: " + str(path))
    original = path.read_text(encoding="utf-8")
    corrected = hardened(original)
    if verify_only and corrected != original:
        raise RuntimeError("Native token-budget correction has not been applied")
    if corrected != original:
        path.write_text(corrected, encoding="utf-8")
    print(
        "PASS: native assistant generation limited to n_predict after actual decoded prompt",
        flush=True,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path, default=(
        Path(__file__).resolve().parent.parent / "third_party" / "llama.cpp"
    ))
    parser.add_argument("--verify", action="store_true")
    arguments = parser.parse_args()
    apply(arguments.upstream, arguments.verify)
