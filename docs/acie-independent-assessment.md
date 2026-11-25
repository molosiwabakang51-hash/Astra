# ACIE independent general intelligence assessment v1 (proposed freeze)

The owner requires >=90/100 before unrelated component development. This protocol measures general intelligence on a defined distribution, not frontier-model parity. It does not supersede the original 119 release requirements, 30-case official handset rubric or H01–H12 supplemental challenge.

## Frozen design before acceptance testing

An independent reviewer creates TWO disjoint private forms, A and B, of 200 cases each, after the assessed APK, GGUF, system instructions and policy are frozen. Neither development prompts nor private answers may enter training or tuning. Each form has the identical allocation below. At least half the cases in each dimension use unfamiliar fictional entities, fresh numerical parameters, original scenarios or newly written passages; avoid public benchmark copies. Include both affirmative and negative correct conclusions, unanswerable cases, misleading premises, corrections and paraphrases. Match form difficulty independently of Astra outputs. The evaluator does not create or grade cases.

| Dimension | Cases per form | Maximum points | Proposed minimum |
|---|---:|---:|---:|
| Logical and abstract reasoning | 40 | 20 | 18 |
| Semantic comprehension | 30 | 15 | 14 |
| Factual accuracy | 30 | 15 | 14 |
| Contextual intelligence | 30 | 15 | 14 |
| Unfamiliar problem-solving | 20 | 10 | 9 |
| Quantitative reasoning | 20 | 10 | 9 |
| Verification and self-correction | 20 | 10 | 8 |
| Creativity and adaptability | 10 | 5 | 4 |
| Total | 200 | 100 | 90 |

Each case receives 0, 1 or 2 integer review credits: 2 meets all prespecified essential correctness, constraints and format requirements; 1 materially useful and partially correct, with no critical error; 0 materially incorrect, fabricated, unsafe, incomplete, refusal of an ordinary answerable task, or wrong essential constraint. A missing answer is zero. Dimension points = sum of credits / 4. Do not normalize away failures. A creative task uses prespecified observable requirements (coherence, novelty relative to supplied constraints, usefulness) rather than personal taste. For a multi-turn case, freeze all follow-up prompts and essential checkpoints, and score the entire case once. Do not award points for mere retrieval or commands when the case requires neural reasoning.

### Route design and reporting

For EACH form, score two separate tracks, model-only and assisted. Use the same distribution and equivalent fresh matched variants for the two tracks; no repeat of any variant or evidence ID across all four runs. Model-only must use the pinned Qwen neural generation with no calculators, answer substitution, document retrieval or output normalization. Assisted can use authorized local retrieval and deterministic computation but must include actual neural synthesis. Both tracks are required to reach >=90 on BOTH forms AND dimension floors above; do not average away a weaker form or route. This is a proposed conservative acceptance rule; the owner approved the 90 target, not a guarantee of this distribution's difficulty.

An independent reviewer privately retains rubrics, transcripts and evidence. Record complete/failed outcome, duration, peak RAM, route, model generation, reviewer credit, opaque unique case and evidence identifiers. Freeze APK SHA, commit, pinned model SHA, system/policy fingerprint, context size and device label across runs. Airplane mode and Wi-Fi disabled; restart before each form; no external AI, paid services or smaller fallback. Report handset reliability separately: 20 consecutive native turns, 10 cancellations/retries, no ANR or crash, measured memory and thermal behavior. A successful benchmark alone does not establish release readiness.

A private record uses four runs, exactly one per (form A/B, track model/assisted), with configuration and attestations per validator. No transcript, private prompt, personal data or reviewer name is exported. `python scripts/quality/evaluate_acie_assessment.py private-record.json` reports separate scores and lower score by track. The validator checks record structure and attestations, not actual device behavior, reviewer independence, case difficulty or answer correctness. Synthetic tests are not intelligence evidence. Before genuine acceptance data is gathered, freeze this protocol version, private rubrics and independent authorship; no post-hoc scoring changes.
