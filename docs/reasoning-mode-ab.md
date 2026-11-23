# Qwen3 1.7B paired reasoning check · owner handset protocol

**Status: experimental and unscored.** This is not the 30-case / 100-point
official handset quality rubric, which is maintained in
[`ai-quality-evaluation.md`](ai-quality-evaluation.md). Passing Android CI
cannot create an intelligence score.

## What the new Settings button does

Settings → Reasoning depth checks → **Compare fast and thinking modes**
runs four fixed, synthetic, privacy-safe questions, each **twice with identical
wording**. Fast (`/no_think`) and think (`/think`) receive the same
512-token cap. Trial order alternates across questions to reduce one-sided
warm/cold load bias. The benchmark disables personal-book retrieval, archived
memory and automatic user-model language preferences; only the explicit mode
override changes. Each request uses a distinct synthetic native generation
identifier to prevent KV-cache cross-contamination. Stop cancels the
experiment. An interrupted run has no reportable score.

Prompts span weighted returns, project NPV, formal deduction and exchange-rate
risk. The output reports correctness from narrow exact-answer checks and total
request milliseconds. It does **not** report private reasoning text, raw
responses, financial holdings or user notes. Exact-answer tests may not capture
explanation quality or longer-form reasoning.

## Repeatable owner-side method

1. Install an owner-approved build **without uninstalling** the existing Astra
   installation. Do not try to install a mismatched debug-signing APK over the
   owner's installed app. Check verified Qwen3 **1.7B Q4_K_M** identity.
2. Turn on airplane mode, Wi-Fi off. Avoid using cloud lookup. Check sufficient
   RAM/storage and record the phone's Android version, battery and available RAM
   locally; do not publish device identifiers.
3. From Settings run **Compare fast and thinking modes**. Record all four
   fast/think verdicts and milliseconds, not only favorable outcomes.
4. Leave Astra, reopen the same signed installation, rerun the four pairs, and
   compare paired correctness plus delays across **both** independent runs.
5. Separately run the canonical 30-case on-phone rubric with ordinary
   open-ended prompts, multi-turn corrections and multilingual dialogue.
   Synthetic paired results must **never** count toward that 100-point score
   as if they were independent conversational evidence.

## Interpretation limits

An improvement on these four questions is evidence only for **this subset**,
not for general intelligence. A think run might be slower, wrong, or contain
only hidden thought within the 512-token cap, producing an unusable answer;
count that as failure rather than silently raising the cap on only one side.
Model loading may dominate the reported time, so each pair is alternated and
repeat runs are required. No inference-speed or intelligence improvement is
claimed until physical phone measurements support it.
