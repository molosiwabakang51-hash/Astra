# Qwen3 1.7B native decoding experiment

The pinned llama.cpp b12248 Android sample initializes a generic temperature
of 0.3, with common defaults top-k 40, top-p 0.95 and min-p 0.05.
The Qwen3 1.7B [official model card](https://huggingface.co/Qwen/Qwen3-1.7B#best-practices)
recommends the following settings:

| Profile | Temperature | Top-p | Top-k | Min-p |
| --- | ---: | ---: | ---: | ---: |
| Direct | 0.7 | 0.8 | 20 | 0 |
| Thinking | 0.6 | 0.95 | 20 | 0 |

The patch uses the final mode suffix produced by Astra, before chat-template
formatting, to select the sampler. It recreates only the sampler each turn,
not the model or KV cache. Presence penalty remains at the existing default,
as the model card notes possible language mixing and performance trade-offs.

This is an inference-quality hypothesis, not a measured score improvement.
Compare prior and new builds on independent, matching-difficulty prompts
with separate direct and thinking runs. Record pass/partial/fail, answer
completeness, repetitions, language mixing, latency and RAM. Keep model,
prompt revision, context and token caps otherwise fixed; report the full
results rather than cherry-picking successes. Actual Android 4096-token
context and bounded generation differ substantially from Qwen's long-output
reference configuration. A quality improvement must be established on-device.
