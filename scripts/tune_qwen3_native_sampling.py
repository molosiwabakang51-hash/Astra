#!/usr/bin/env python3
"""Use Qwen3's documented mode-specific decoding parameters in pinned Android JNI.

Upstream b12248 creates a generic sampler at temperature 0.3, top-k 40,
top-p 0.95, min-p 0.05. Qwen3-1.7B's model card recommends different
thinking/non-thinking values. Read only Astra's final mode suffix before
chat-template formatting and rebuild the small sampler for each turn.
Do not reload model weights or alter context size, output caps or privacy.

https://huggingface.co/Qwen/Qwen3-1.7B#best-practices
"""
from __future__ import annotations
import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")
RULES = (
    (
        "constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;",
        "// Qwen3 decoding profiles are selected per owner turn below.",
    ),
    (
        """static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    return common_sampler_init(g_model, sparams);
}""",
        """static common_sampler *new_sampler(bool thinking) {
    common_params_sampling sparams;
    sparams.temp = thinking ? 0.6f : 0.7f;
    sparams.top_p = thinking ? 0.95f : 0.8f;
    sparams.top_k = 20;
    sparams.min_p = 0.0f;
    // Keep the optional presence penalty unchanged until independent A/B.
    return common_sampler_init(g_model, sparams);
}

static bool astra_thinking_suffix(const std::string & prompt) {
    // Only the final marker added by Kotlin is authoritative. Quoted
    // /think occurrences in documents or previous turns cannot select it.
    const std::string suffix = "\\n/think";
    return prompt.size() >= suffix.size() &&
        prompt.compare(prompt.size() - suffix.size(), suffix.size(), suffix) == 0;
}""",
    ),
    (
        "    g_sampler = new_sampler(DEFAULT_SAMPLER_TEMP);\n    return 0;",
        "    g_sampler = new_sampler(false);\n    if (g_sampler == nullptr) return 1;\n    return 0;",
    ),
    (
        "    std::string formatted_user_prompt(user_prompt);\n",
        """    std::string formatted_user_prompt(user_prompt);
    // This runs on the exclusive llama worker before prompt decoding.
    // Allocate first; on failure the existing sampler remains valid for cleanup.
    auto * next_sampler = new_sampler(astra_thinking_suffix(formatted_user_prompt));
    if (next_sampler == nullptr) {
        env->ReleaseStringUTFChars(juser_prompt, user_prompt);
        return 4;
    }
    if (g_sampler != nullptr) common_sampler_free(g_sampler);
    g_sampler = next_sampler;
""",
    ),
)


def hardened(source: str) -> str:
    for before, after in RULES:
        old, new = source.count(before), source.count(after)
        if old == 1 and new == 0:
            source = source.replace(before, after, 1)
        elif new == 1 and (old == 0 or (old == 1 and before in after)):
            continue
        else:
            raise RuntimeError("Pinned Qwen3 sampling source drift or duplicate: "
                               f"original={old}, patched={new}; {before[:70]!r}")
    for marker in ("sparams.temp = thinking ? 0.6f : 0.7f;",
                   "sparams.top_p = thinking ? 0.95f : 0.8f;",
                   "sparams.top_k = 20;", "sparams.min_p = 0.0f;",
                   "g_sampler = next_sampler;"):
        if source.count(marker) != 1:
            raise RuntimeError("Qwen3 native sampling profile incomplete")
    if "DEFAULT_SAMPLER_TEMP" in source:
        raise RuntimeError("Legacy generic temperature remains")
    return source


def apply(upstream: Path, verify_only: bool = False) -> None:
    path = upstream / CPP
    if not path.is_file():
        raise RuntimeError("Pinned JNI source missing: " + str(path))
    before = path.read_text(encoding="utf-8")
    after = hardened(before)
    if verify_only and after != before:
        raise RuntimeError("Qwen3 sampling patch not installed")
    if after != before:
        path.write_text(after, encoding="utf-8")
    print("PASS: Qwen3 per-turn native sampling profiles installed", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path,
                        default=Path(__file__).resolve().parent.parent / "third_party/llama.cpp")
    parser.add_argument("--verify", action="store_true")
    opts = parser.parse_args()
    apply(opts.upstream, opts.verify)
