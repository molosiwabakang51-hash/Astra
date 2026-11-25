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
