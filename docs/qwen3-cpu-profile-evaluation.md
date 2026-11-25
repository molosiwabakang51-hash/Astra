# Actual model CPU sampling comparison

The 90/100 goal needs real model output evidence rather than CI test counts.
This manually dispatchable, path-scoped workflow uses a standard public
GitHub Actions CPU runner, downloads the exact already-pinned Qwen3 1.7B
Q4_K_M GGUF, checks its SHA-256, and compiles the pinned b12248 llama.cpp
CPU server. Inference runs only against its local loopback port. No paid
API, larger runner, private prompt, other model, or recurring schedule.

Six reproducibly generated synthetic problems cover deduction,
non-entailment, quantity, ordering, program trace and uncertainty. Both
direct and thinking modes compare the same question, RNG seed, production
system prompt, context size and output cap under the prior and recommended
sampling profiles. The order alternates; prefix cache reuse is disabled.
The runner checks returned sampling settings, exact response completion
status and strict answer correctness. Only case-level metrics are retained;
no hidden reasoning, raw prompt or answer text is included in the artifact.

This is a narrow experimental diagnostic, not a test of the Android JNI
integration, actual physical device latency or full open-ended intelligence.
It uses raw ChatML and soft mode markers; the Android template path and
hardware differ. Direct and thinking use reduced caps of 256 and 768
tokens for bounded CI cost. Do not treat the six cases as independent
held-out evidence or report a /100 general intelligence score. Repeat
with fresh seeds; separately run the established handset rubric and
independent H01–H12 review. No default changes should be justified by a
single exploratory run. Failure to download, compile, start, or confirm
configuration awards no score.

## First actual result (run 45712659939)

Verified artifact 15068248646, seed 2132437795, exact pinned model. Strict correct/complete of six: legacy direct 2/6, 6 complete; Qwen direct 2/6, 6 complete; legacy thinking 4/6, 6 complete; Qwen thinking 3/6, 5 complete. Thinking generation took 125988 ms legacy versus 167662 ms Qwen over six CPU requests. One Qwen thinking ordering case hit 768 tokens. No broad score, no demonstrated sampling gain. A strict failure can be format noncompliance rather than incorrect underlying reasoning. Actual handset behavior remains unknown.

## Controlled follow-up

Two separate ablations repeat the known diagnostic seed, never claim held-out independence: hard non-thinking assistant prefill versus soft /no_think at fixed legacy sampling (12 requests); compact domain-general system prompt versus production at fixed legacy sampling across both modes (24 requests). See Qwen3 model card: https://huggingface.co/Qwen/Qwen3-1.7B. The production Android prompt and decoding policy are unchanged pending evidence. Report incomplete, no visible answer, wrong scalar and noncanonical/ungraded separately. No raw model response or private reasoning is exported. A noncanonical response earns no strict correctness credit, but is not labeled an incorrect substantive conclusion. The experiment does not itself validate the native Android template path.
