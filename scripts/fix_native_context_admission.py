#!/usr/bin/env python3
"""Reserve the complete current prompt and response budget before native prefill.

Pinned b12248 shifts KV positions mid-batch yet uses the original start_pos.
It can also truncate the user's final question and discard current-turn tokens
during generation. Evict only old dialogue before prefill, preserve the system
prompt and full current question, and fail closed if a turn cannot fit.
Only the existing ordinary 768/1152/1792-token tiers may shrink to a useful
minimum when exact tokenizer counts demand it. Smaller diagnostic caps stay
unchanged. No larger KV cache or change to model weights is required.
"""
from __future__ import annotations
import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")

RULES = (
    (
        """        // Shift context if current batch cannot fit into the context
        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            LOGw("%s: Current batch won't fit into context! Shifting...", __func__);
            shift_context();
        }""",
        """        // The caller reserves the complete turn before prefill.
        // Never shift mid-batch: start_pos would refer to stale KV positions.
        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            LOGw("%s: Prompt batch exceeds reserved context", __func__);
            return 1;
        }""",
    ),
    (
        """    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int original_user_prompt_size = (int) user_tokens.size();""",
        """    // Preserve the full question and system prefix; adapt only the ordinary
    // answer allowance when the exact tokenizer count requires it.
    const int astra_context_capacity = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM - 1;
    if (n_predict <= 0 || system_prompt_position < 0 ||
        current_position < system_prompt_position || current_position > astra_context_capacity) {
        return 5;
    }
    const int64_t astra_available = static_cast<int64_t>(astra_context_capacity) -
        system_prompt_position - static_cast<int64_t>(user_tokens.size());
    // Match only the existing production long-budget tiers. Short and
    // diagnostic caps (including paired 512-token checks) remain exact.
    const int astra_minimum = n_predict == 1792 ? 768 :
        (n_predict == 768 || n_predict == 1152 ? 256 : n_predict);
    if (astra_available < astra_minimum) return 5;
    n_predict = static_cast<jint>(std::min<int64_t>(n_predict, astra_available));
    // Remove only preceding dialogue. Do this once, before decoding any
    // current-turn tokens, and compact surviving KV positions.
    const int astra_discard = std::max(0, current_position +
        static_cast<int>(user_tokens.size()) + n_predict - astra_context_capacity);
    if (astra_discard > 0) {
        const llama_pos end = system_prompt_position + astra_discard;
        if (end > current_position ||
            !llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, end)) {
            return 6;
        }
        llama_memory_seq_add(llama_get_memory(g_context), 0, end, current_position, -astra_discard);
        current_position -= astra_discard;
    }

    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int original_user_prompt_size = (int) user_tokens.size();""",
    ),
)

def hardened(source: str) -> str:
    for old, new in RULES:
        before, after = source.count(old), source.count(new)
        if before == 1 and after == 0:
            source = source.replace(old, new, 1)
        elif after == 1 and (before == 0 or (before == 1 and old in new)):
            continue
        else:
            raise RuntimeError("Pinned native context admission drift or duplicate: " + repr(old[:80]))
    for required in ("const int astra_context_capacity", "current_position -= astra_discard;",
                     "!llama_memory_seq_rm(", "static_cast<int64_t>(user_tokens.size())",
                     "if (astra_available < astra_minimum) return 5;",
                     "std::min<int64_t>(n_predict, astra_available)"):
        if source.count(required) != 1:
            raise RuntimeError("Incomplete native context admission")
    return source

def apply(upstream: Path, verify_only: bool = False) -> None:
    file = upstream / CPP
    source = file.read_text(encoding="utf-8")
    fixed = hardened(source)
    if verify_only and source != fixed:
        raise RuntimeError("Native context admission not installed")
    if source != fixed:
        file.write_text(fixed, encoding="utf-8")
    print("PASS: full current prompt and bounded effective output budget reserved before native prefill", flush=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path,
                        default=Path(__file__).resolve().parent.parent / "third_party/llama.cpp")
    parser.add_argument("--verify", action="store_true")
    opts = parser.parse_args()
    apply(opts.upstream, opts.verify)
