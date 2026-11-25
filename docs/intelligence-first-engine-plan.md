# Astra intelligence-first engine plan — 25 November 2026

Owner direction: build a general conversational intelligence engine, not a command launcher. The original November 13 concept was an offline, zero-cost assistant/agent. Preserve all 119 parent requirements (#166), and the later directives (#308, #313, #317, #345). This is a recoverable-history reconciliation, not a claim that every prior prompt was available or that requirements are accepted.

| Recovered period | Owner objective | Traceability and remaining proof |
| --- | --- | --- |
| Nov 13–14 | Free local assistant/agent, natural dialogue, memory, approved actions; multilingual and broad learning | #6, #7, #62; actual generative behavior, permission boundaries and knowledge coverage outstanding |
| Nov 15–17 | 8.5/10 ambition, parallel work, two-day tests, preserve device data, ingestion, persistent offline reasoning, interface quality | #166 R001–R119, #111, #113, #142; handset checks required |
| Nov 18–20 | Correct factual errors, maintain follow-up context, use 1.7B only, improve reasoning rather than commands; multilingual voice/text; exhaustive study | #166 R019–R084; #387–#391; actual dialogue and voice tests outstanding |
| Nov 21–24 | Native reliability, release readiness, comprehensive cognitive/human development, security and local learning | #308, #313, #315, #317, #331, #345; feature expansion deferred |
| Nov 25 | General intelligence first, preserve historical requests and achieve measured 8–9/10 before expanding unrelated features | #308; independent generative evaluation, not PR counts |

Hard constraints: Qwen3 1.7B Q4_K_M only; no paid dependency, no 0.6B fallback, offline-first, consented local evidence, signed data-preserving updates, 300 MB decimal LITE APK cap. Keep the original ledger and all downstream study/voice/UI/security/agent goals; do not accept untested features. Do not describe local topic adaptation as training the GGUF weights. No frontier-model parity claim.

## Engineering order

1. Reliable native inference: import, 20+ consecutive turns, cold/warm start, stop/retry, ANR/memory/thermal instrumentation (#331).
2. Model-led language comprehension and reasoning: assess unseen logical, factual, creative, explanatory, ambiguous and constraint tasks; use errors to improve system instructions, context and decoding rather than adding answer-specific handlers.
3. Context/memory: correct topic, entities, current assumptions, cross-language references, source provenance; authorized retrieval only; deterministic tools are separate evidence.
4. Measure standard and adaptive modes on matching independent variants; report correctness, latency, peak RAM, interruption and response completeness; enable adaptive automatically only after net benefit.
5. After intelligence gate: integrate CDITS, CHDCES, ALRIIS and remaining 119-parent work without losing prior requests. Safety and runtime are prerequisites, not optional features.

## Independent generalization gate

The 30-case official quality scorecard remains unchanged. It mixes useful tools and release checks with AI; it must not be called pure model intelligence. The public H01–H12 examples in [held-out challenge](intelligence-heldout-challenge.md) are **public examples**, not genuinely unseen tests. An independent reviewer writes private equivalent variants after implementation freezes, with a fresh variant for each run and no training/tuning on them. H01–H07 and H09–H12 must use native model-only generation (no deterministic answers or retrieval); H08 is separately scored retrieval/source-trust evaluation. Record route, native-generation evidence and independent review. An independent reviewer assigns 0, 1 or 2 points using the dimension criteria. No prompts, transcripts or private records should be committed.

Run twice on the same phone, build, model, context, prompt revision, sampling and mode, with a restart before each run. Compare standard/adaptive in separate paired review records; do not change configuration between repeatability runs. Use fresh matched difficulty variants, and preserve latency/peak RAM and failure cause separately. Require at least 20/24 overall, 18/22 model-only and full H08 source-trust credit in BOTH runs before asserting this supplemental gate. These conservative thresholds are proposed engineering acceptance, not a claim of current ability or an official rating.

Private record format: top-level runs array of exactly two objects; each run has configuration (commit, apk_sha256, device_label, android_version, model_sha256, prompt_revision, context_tokens, mode, sampling_revision), all seven true attestations named in the validator, and cases mapping H01–H12 to points (0/1/2), route (model or retrieval), native_generated (true), reviewed (true), unique opaque variant_id and evidence_id. Use opaque labels only; record original results and independent review privately. Run `python3 scripts/quality/evaluate_generalization.py /path/to/private-record.json`. The script validates attestations only; cannot prove that a device or independent reviewer exists. Synthetic CI records award no intelligence points.

Do not move beyond the general-AI priority merely because the supplemental gate passes: require the original official handset rubric, independent evidence and #308 mandatory runtime conditions. No score based on compilation, number of commands or engineering estimates.
