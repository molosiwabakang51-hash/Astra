#!/usr/bin/env python3
"""Repair bounded native conversation continuity in pinned llama.cpp b12248.

The upstream JNI stop position uses a KV-cache *position*, which can move
backwards when older context tokens are discarded. At the output cap it also
returns nullptr without adding the assistant's response to chat_msgs, so
subsequent turns lose the previous assistant message in template history.

Use a generated-token countdown independent of KV positions, and finalise the
assistant's bounded answer when the countdown reaches zero. This applies after
the v0.11.9 prompt-token accounting patch. Fail closed if the pinned source
changes and needs another review.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")

REWRITES = (
    (
        """static llama_pos stop_generation_position;
static std::string cached_token_chars;""",
        """// Generation budget is independent of context-window KV positions.
static int remaining_generation_tokens;
static std::string cached_token_chars;""",
    ),
    (
        """    stop_generation_position = 0;
    cached_token_chars.clear();""",
        """    remaining_generation_tokens = 0;
    cached_token_chars.clear();""",
    ),
    (
        """    stop_generation_position = current_position + n_predict;
    return 0;""",
        """    // Bound assistant tokens, not absolute positions after a shifting context.
    remaining_generation_tokens = std::max(0, static_cast<int>(n_predict));
    return 0;""",
    ),
    (
        """    // Infinite text generation via context shifting
    if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Stop if reaching the marked position
    if (current_position >= stop_generation_position) {
        LOGw("%s: STOP: hitting stop position: %d", __func__, stop_generation_position);
        return nullptr;
    }

    // Sample next token""",
        """    // Check the number of assistant tokens, not a shifting KV-cache position.
    // Record a max-length answer in chat_msgs just like an EOG answer, so the
    // next user turn is formatted with the prior assistant turn in history.
    if (remaining_generation_tokens <= 0) {
        LOGd("%s: STOP: assistant token budget exhausted", __func__);
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    // Older tokens can be discarded without altering the response budget.
    if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Sample next token""",
    ),
    (
        """    // Update position
    current_position++;

    // Stop if next token is EOG""",
        """    // The output count decreases exactly once for every decoded token.
    // A context shift must not grant extra assistant generation tokens.
    current_position++;
    --remaining_generation_tokens;

    // Stop if next token is EOG""",
    ),
)

GUARDS = (
    "static int remaining_generation_tokens;",
    "remaining_generation_tokens = std::max(0, static_cast<int>(n_predict));",
    "if (remaining_generation_tokens <= 0) {",
    "--remaining_generation_tokens;",
    "chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());",
)
FORBIDDEN = (
    "stop_generation_position",
    "current_position + user_prompt_size + n_predict",
)


def hardened(source: str) -> str:
    result = source
    for before, after in REWRITES:
        a, b = result.count(before), result.count(after)
        if a == 1 and b == 0:
            result = result.replace(before, after, 1)
        elif a == 0 and b == 1:
            pass
        else:
            raise RuntimeError(
                "Native chat budget source drift or duplicated snippet: "
                f"original={a}, patched={b}; first={before[:75]!r}"
            )
    if any(item in result for item in FORBIDDEN):
        raise RuntimeError("Legacy position-based generation cap remains in native source")
    if any(item not in result for item in GUARDS):
        raise RuntimeError("Missing count-based generation or assistant chat history update")
    if result.count("chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());") != 2:
        raise RuntimeError("EOG and token-budget paths must both record assistant turns")
    return result


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / CPP
    if not path.is_file():
        raise RuntimeError("Pinned llama.cpp native chat source missing: " + str(path))
    original = path.read_text(encoding="utf-8")
    corrected = hardened(original)
    if verify_only and corrected != original:
        raise RuntimeError("Native bounded-chat fix has not been applied")
    if corrected != original:
        path.write_text(corrected, encoding="utf-8")
    print(
        "PASS: bounded assistant tokens survive KV-cache shifts and capped turns enter chat history",
        flush=True,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path,
        default=Path(__file__).resolve().parent.parent / "third_party" / "llama.cpp")
    parser.add_argument("--verify", action="store_true")
    arguments = parser.parse_args()
    apply(arguments.upstream, arguments.verify)
