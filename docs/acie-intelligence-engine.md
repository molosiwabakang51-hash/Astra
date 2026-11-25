# Astra Cognitive Intelligence Engine (ACIE)

Owner-authorized implementation begins with general AI exclusively.
Target: >=90/100 on an independently reviewed, frozen assessment.
Preserve existing 119 requirements and later development directives.
Use only pinned Qwen3 1.7B, no paid services, offline-first.

## Workstreams and acceptance

| Workstream | Initial implementation | Acceptance evidence |
| --- | --- | --- |
| Native reasoning | Qwen3 sampling #404, completion status #403, context admission #406 | Correctness/completeness A/B and handset stress |
| Actual model evaluation | #407 native CPU profile comparison | Generated answers, not CI pass count |
| Semantic comprehension | Planned hybrid contextual relevance + bounded model-led query interpretation | Unseen paraphrases, multilingual and ambiguous references |
| Adaptive reasoning | Existing opt-in routing; next evaluate matched difficulty modes | Better correctness at measured resource cost |
| Verification | Initial explicit answer audit in this PR | Exact arithmetic independent; isolated model critique clearly labeled; unseen correction tests |
| Cognitive memory | Historical corrected premises and source-aware excerpts | Correct recall after reload and topic switch |
| Knowledge synthesis | Local authorized sources, distinct evidence and inference | Unseen conflicting-source tasks |
| Continuous adaptation | Validated corrections, no claim of GGUF weight update | Repeated unseen evaluation, no tuning on held-out variants |

The first audit is invoked through natural-language requests such as
"Verify your answer". It reviews only the immediately preceding complete
same-chat exchange. A local exact calculation can independently confirm or
correct an unambiguous numeric answer; other answers are passed to the
same model in a fresh isolated session, with an explicit unverified label.
It does not retrieve documents, run commands, modify model weights, or
automatically double inference latency. Oversized or incomplete exchanges
are refused instead of silently truncated. A later automatic verification
policy must be justified by measured A/B results and external feedback.
The 90/100 target is not yet demonstrated; the 200-case independent
assessment must be designed and frozen before collecting acceptance data.
