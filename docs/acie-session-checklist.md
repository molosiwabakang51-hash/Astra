# ACIE session engineering checklist — 26 November 2026

Owner directive: maintain every item, update at the end of each engineering session using verified evidence, and keep general AI the exclusive priority until independently demonstrated >=90/100. This separate ACIE ledger preserves the original 119 parent release requirements. Engineering completion is not an intelligence score. Existing checked statuses from the owner-approved session checklist denote integrated engineering, not complete capability acceptance. No new item checked merely from a proposed change. 27/119 engineering items checked.

Newly checked: concept-aware retrieval integration (#409, Android/Python passed); initial verification merge and arithmetic verification integration (#408, Android/Python passed); narrow actual-model CPU baseline (run 45712659939, artifact 15068248646). CPU baseline is exploratory only, not the independent 200-case baseline. Isolated review quality remains unchecked until actual native review outcomes. Sampling optimization is integrated, not demonstrated superior; retain that distinction. #407 workflow successfully executed but merge conflict; reconciled in this PR. Hard direct and compact system experiments completed (run 45715461508); #410 merged. Hard direct 3/6 vs soft 2/6 strict; compact thinking 2/6 vs production 4/6. Retain production prompt pending fresh evidence. No verified /100 rating.

## 1. Native inference engine (7/12)

- [x] ACIE-001 Exclusive Qwen3 1.7B model architecture
- [x] ACIE-002 Model integrity verification
- [x] ACIE-003 Native inference error recovery
- [x] ACIE-004 Cooperative generation cancellation
- [x] ACIE-005 Qwen3-specific sampling optimisation
- [x] ACIE-006 Incomplete-response detection
- [x] ACIE-007 Native context protection
- [ ] ACIE-008 Optimise CPU inference throughput
- [ ] ACIE-009 Optimise memory consumption
- [ ] ACIE-010 Optimise reasoning-token allocation
- [ ] ACIE-011 Evaluate quantisation trade-offs
- [ ] ACIE-012 Verify sustained physical-device inference

## 2. Semantic comprehension (3/12)

- [x] ACIE-013 Unrestricted natural-language model pathway
- [x] ACIE-014 Model-only conversation isolation
- [x] ACIE-015 Complete concept-aware retrieval integration
- [ ] ACIE-016 Model-led intent interpretation
- [ ] ACIE-017 Contextual entity recognition
- [ ] ACIE-018 Semantic similarity retrieval
- [ ] ACIE-019 Implicit meaning recognition
- [ ] ACIE-020 Ambiguity resolution
- [ ] ACIE-021 Complex instruction comprehension
- [ ] ACIE-022 Multilingual semantic comprehension
- [ ] ACIE-023 Distinguish instructions from quoted information
- [ ] ACIE-024 Verify comprehension across unfamiliar domains

## 3. Reasoning and problem-solving (3/16)

- [x] ACIE-025 Domain-general reasoning instructions
- [x] ACIE-026 Opt-in adaptive reasoning
- [x] ACIE-027 Structured multiline reasoning
- [ ] ACIE-028 Dynamic difficulty assessment
- [ ] ACIE-029 Adaptive reasoning-budget allocation
- [ ] ACIE-030 Complex problem decomposition
- [ ] ACIE-031 Deductive reasoning optimisation
- [ ] ACIE-032 Inductive reasoning optimisation
- [ ] ACIE-033 Abductive reasoning optimisation
- [ ] ACIE-034 Abstract and relational reasoning
- [ ] ACIE-035 Counterfactual reasoning
- [ ] ACIE-036 Causal reasoning
- [ ] ACIE-037 Strategic planning and decision-making
- [ ] ACIE-038 Multi-constraint optimisation
- [ ] ACIE-039 Alternative hypothesis evaluation
- [ ] ACIE-040 Unfamiliar problem-solving validation

## 4. Cognitive memory (4/12)

- [x] ACIE-041 Persistent conversation recovery
- [x] ACIE-042 Topic-relevant historical context retrieval
- [x] ACIE-043 Correction-chain recovery
- [x] ACIE-044 Source-role differentiation
- [ ] ACIE-045 Semantic long-term memory
- [ ] ACIE-046 Contextual entity relationships
- [ ] ACIE-047 Temporal understanding
- [ ] ACIE-048 Contradictory memory resolution
- [ ] ACIE-049 Relevant cross-conversation synthesis
- [ ] ACIE-050 Intelligent memory prioritisation
- [ ] ACIE-051 Dynamic context compression
- [ ] ACIE-052 Long-horizon conversational consistency

## 5. Verification and self-correction (2/13)

- [x] ACIE-053 Merge initial ACIE verification engine
- [x] ACIE-054 Independent arithmetic verification integration
- [ ] ACIE-055 Isolated model-led answer review validation
- [ ] ACIE-056 Automatic detection of reasoning inconsistencies
- [ ] ACIE-057 Mathematical verification
- [ ] ACIE-058 Logical conclusion verification
- [ ] ACIE-059 Source attribution verification
- [ ] ACIE-060 Contradiction detection
- [ ] ACIE-061 False-premise identification
- [ ] ACIE-062 Calibrated uncertainty assessment
- [ ] ACIE-063 Conditional answer regeneration
- [ ] ACIE-064 Independent feedback integration
- [ ] ACIE-065 Verify that correction improves original answers

## 6. Knowledge intelligence (2/11)

- [x] ACIE-066 Explicit local knowledge retrieval
- [x] ACIE-067 Source provenance preservation
- [ ] ACIE-068 Hybrid lexical-semantic retrieval
- [ ] ACIE-069 Multi-document reasoning
- [ ] ACIE-070 Cross-disciplinary knowledge synthesis
- [ ] ACIE-071 Conflicting-source evaluation
- [ ] ACIE-072 Evidence quality assessment
- [ ] ACIE-073 Knowledge relationship mapping
- [ ] ACIE-074 Distinguish established facts from hypotheses
- [ ] ACIE-075 Reduce unsupported factual claims
- [ ] ACIE-076 Evaluate unfamiliar knowledge-synthesis tasks

## 7. Model-led tool integration (1/8)

- [x] ACIE-077 Deterministic calculation tools
- [ ] ACIE-078 Natural-language tool selection
- [ ] ACIE-079 Model-generated structured tool requests
- [ ] ACIE-080 Tool argument validation
- [ ] ACIE-081 Multi-step tool orchestration
- [ ] ACIE-082 Integration of tool results into reasoning
- [ ] ACIE-083 Independent verification of tool-assisted conclusions
- [ ] ACIE-084 Separate assisted and unassisted intelligence scores

## 8. Continuous cognitive adaptation (0/11)

- [ ] ACIE-085 Structured learning from validated corrections
- [ ] ACIE-086 Persistent error-pattern analysis
- [ ] ACIE-087 Feedback quality assessment
- [ ] ACIE-088 Adaptive reasoning strategy selection
- [ ] ACIE-089 Knowledge consolidation
- [ ] ACIE-090 Prevent reinforcement of incorrect information
- [ ] ACIE-091 Investigate zero-cost LoRA adaptation feasibility
- [ ] ACIE-092 Investigate knowledge distillation feasibility
- [ ] ACIE-093 Evaluate neural adaptation where feasible
- [ ] ACIE-094 Test for catastrophic forgetting if weights change
- [ ] ACIE-095 Measure generalisation improvements

## 9. Conversational intelligence (1/9)

- [x] ACIE-096 Native multi-turn conversation pathway
- [ ] ACIE-097 Natural follow-up interpretation across topics
- [ ] ACIE-098 Consistent handling of corrected assumptions
- [ ] ACIE-099 Context-sensitive response depth
- [ ] ACIE-100 Natural explanatory ability
- [ ] ACIE-101 Creative problem-solving
- [ ] ACIE-102 Instruction and format adherence
- [ ] ACIE-103 Intellectual adaptability
- [ ] ACIE-104 Extended conversation stress testing

## 10. Intelligence evaluation (4/15)

- [x] ACIE-105 Establish supplemental held-out evaluation protocol
- [x] ACIE-106 Separate model-only and assisted evaluation routes
- [x] ACIE-107 Implement actual-model CPU comparison workflow
- [x] ACIE-108 Complete actual-model baseline measurement
- [ ] ACIE-109 Freeze 200-case independent assessment protocol
- [ ] ACIE-110 Measure standard versus adaptive reasoning
- [ ] ACIE-111 Measure accuracy and completeness
- [ ] ACIE-112 Measure hallucination frequency
- [ ] ACIE-113 Measure semantic comprehension
- [ ] ACIE-114 Measure reasoning consistency
- [ ] ACIE-115 Measure verification effectiveness
- [ ] ACIE-116 Measure latency and peak memory
- [ ] ACIE-117 Evaluate regressions after major improvements
- [ ] ACIE-118 Complete independent physical-device assessment
- [ ] ACIE-119 Demonstrate repeatable intelligence score of at least 90/100

## Session update — independent assessment freeze evidence (26 November)

Parallel to #426 (Python 45738948900 passed; Android 45738948938 and native CPU 45738948918 still running at verification), add a candidate pre-run commitment to the existing ACIE-v1 assessor. An independent reviewer privately retains a canonical manifest SHA-256 before any run, committing to the frozen configuration and all 800 variant/dimension/rubric-hash tuples across four tracks/forms. Validator compares the supplied externally retained digest and exact planned case set with the final record; separate numerical target from acceptance-record eligibility. Reject altered rubric, variants, config, duplicate IDs and unexpected manifest fields. Preserve existing scoring and legacy uncommitted numerical reports, which cannot be acceptance-record eligible. Three regression tests and procedure documentation added. Digest matching does not establish prior timing or independent review; actual private case authorship, reviewer freeze, physical-device assessment and CI remain outstanding. All 119 sequential IDs verified; 27 checked, 92 open. ACIE-109/118/119 remain unchecked. No change to deployed intelligence, no independent score or release claim.

## Latest engineering session

All 119 IDs reviewed; 27/119 unchanged. Artifact 15069384240 confirms hard-direct generated 168 vs 287 tokens, both 6/6 complete; compact thinking completed 4/6 vs production 6/6. Most strict failures are noncanonical/ungraded, not proven wrong conclusions. New explicit integer/yes-no format normalizer strips only unambiguous wrappers and leaves model-only, diagnostic, grounded and capped replies unchanged. No claim of improved reasoning score. ACIE-102 remains unchecked pending Android CI and broader native adherence evidence.

## Latest session: integrated source attribution and token allocation

All 119 ACIE IDs reviewed and preserved. #411 Android CI passed and the response-format layer merged at 688e4d3537dce92213cadab26ccbaae67c8521d0. #412 is integrated after Android/Python passes; independent 200-case protocol freeze and test evidence still pending. #413 Python and compiled arithmetic tests passed; Android validation pending at reconciliation. #414 added whole-brief attribution, streamed and final memory citation validation, and literal ID escaping; Python 703-test run had one outdated source assertion, corrected here. Reconcile both changes against merged #411, retaining the scalar contract and original model-only/diagnostic behavior; rerun combined native Android/Python CI. ACIE-010, ACIE-029, ACIE-059, ACIE-102 and ACIE-109 remain open until their actual acceptance evidence exists. No new master checkmarks; 27/119. Citation identity is not proof of entailment and token admission is not demonstrated reasoning gain.

## Session update — verified integrations and model-led formalization

#415 passed both Python (45719691746) and Android (45719690509) and merged as 5e53ec72b8e222be707b2063409d1307f2d4e04d. #416 Android passed (45720596505) and merged as cd1bf9f7853fcc71996780673ba81142cf51114b. Integrated engineering includes typed memory attribution checks, exact-token context admission and bounded propositional verification. Broader actual grounding and handset verification remain pending for ACIE-010/029/058/059. This session adds an explicitly owner-confirmed formal argument verification path and an optional model-generated, unverified proposition mapping in the existing audit path. No auto-acceptance of model interpretations. Fresh CPU diagnostic compares 8 balanced new cases under deployed decoding profiles (32 requests), soft/hard direct and baseline/decomposition thinking separately; no production decoding change. Tests and actual CPU comparison pending. All 119 IDs preserved; 27 checked, 92 outstanding; no new broad acceptance or intelligence score.

## Latest session — verified neural comparison and integrated calculator

#417 passed Python, Android and actual Qwen3 CPU evaluation 45722476790; merged 78bf93adc5289585f74be1b1327ae7dc4854de3d. Artifact 15073607086 (seed 283083470, pinned model): standard thinking 8/8 strict, direct soft/hard each 3/8, decomposed thinking 3/8, all 32 complete. Standard thinking 1949 tokens/175060 ms versus decomposition 2439 tokens/201602 ms. Direct each one wrong scalar and four ungraded noncanonical answers; decomposition five ungraded noncanonical answers. Retain standard system prompt and deployed sampling. This is one public synthetic sample, not independent or physical-device evidence.

#418 passed Python and Android and merged 3e570fc64d6b22c9dd99685cc25abcc4052fa143. #419 revised calculator branch passed Python 45723825148 and Android 45723825010; conflicts after #418 reconciled here with both current-owner intent and calculator safeguards preserved. Existing source/privacy checks retained. Fresh integration CI required. All 119 IDs reviewed, 27 checked, 92 outstanding; no broad acceptance yet. ACIE-010/016/023/028/029/030/058/059/078/079/080/082/097/099/102/109 remain under validation. No official general intelligence score.

## Session update — verified calculator and broader neural evaluation

#420 passed Python 45725968772 and Android 45725969052 and merged as 1171323fcb98a61bb95559c74a142703ca17e6f2. #421 failed one Kotlin regression 45726062537 (leading brevity modifier blocked anchored reasoning classification); revised head 51c3f0b005a70fed5c26e68e941a3d032fd2ce2c aligns deduction, task-opening, tradeoff and language guards, pending fresh Android verification. New 24-case/48-call breadth experiment uses four equally sized domains, predefined reproducible seed, standard direct/thinking diagnostic configurations, alternating matched order, strict failure accounting and domain-specific denominators. No change to deployed model, prompt, sampling, budgets or default-off adaptation. All 119 IDs reviewed; 27 checked, 92 outstanding. Calculator implementation is merged but actual model-led selection and synthesis still require validation. Broader model and independent handset results are pending. No general intelligence score.

## Session update — breadth and tool results, paired review

#421 passed corrected Android and merged 6e47090b5d78fa2ce3e2aa222e9d1a1bcede0386. #422 passed Python/Android/native CPU and merged 11085ed6bd5e4a10059dacf1dd782bb7d450eb50. Artifact 15075689325 verified: direct 5/24, thinking 18/24; all 48 completed. Logic 3/6 vs 5/6, quantity 0/6 vs 3/6, context 1/6 vs 5/6, strategy 1/6 vs 5/6. Thinking-only 14, direct-only 1, both 4, neither 5. Direct 4 wrong scalars/15 ungraded noncanonical; thinking 2 wrong scalars/4 ungraded noncanonical. Both failed quoted instruction problem. CPU totals 288627 ms direct, 555090 ms thinking. Do not infer a general intelligence score or handset speed.

#423 passed Python/Android/actual calculator workflow and merged a2912799afb38ef48e74cdbf8d45a3861ed62b9b. Artifact 15074824849 verified: 8/8 supported expressions yielded correct computed values, 4/4 unsuitable plans rejected by Kotlin, 0/4 explicit NONE, 0/8 strict synthesis, all 12 planning and 8 attempted synthesis completed. Do not equate rejection with model understanding or noncanonical synthesis with wrong substantive results. Keep actual tool capability acceptance open for broader selection/synthesis validation.

This session adds a 12-case actual-model paired revision experiment (up to 36 calls) using fresh questions and matched thinking retry/review arms, with original wrong-scalar recovery, correct-answer loss and fixed-denominator paired scoring. Candidate prompt only; not a deployed review change. All 119 IDs preserved; 27 checked and 92 outstanding. ACIE-055/063/065/115 remain pending production/handset evidence; no new broad acceptance or official score. 

## Session update — actual self-review and calculator synthesis contract

#424 passed all three checks and merged 2ffff9e60582853611a688eac05f9131965f6133. Inspected artifact 15077604549: all 36 calls complete. Original direct 4/12; fresh thinking retry 9/12; candidate review 3/12. Retry recovered five strict failures, review recovered zero and lost one previously correct answer; neither recovered a confirmed wrong scalar. Retry 2634 tokens/223320 ms vs review 3500 tokens/302431 ms. Paired retry-only six, review-only zero, both three, neither three. Do not deploy candidate review prompt. Keep existing explicit audit; no broad claim of self-review ineffectiveness, since most failures were noncanonical. No independent score.

New calculator synthesis contract recognizes explicit terminal numeric-only owner instruction outside quotes and promotes it outside untrusted question data. Model may return MISMATCH rather than endorsing a wrong expression. Output warning for exact numeric disagreement, incomplete interpretation, or noncanonical scalar format; retain original response and independent expression/value footer. No silent substitution or claim of semantic verification. Added fresh 12-case probe suite; historical suite remains default for reproducible rerun. Regression checks and actual fresh model evaluation pending. All 119 IDs reviewed, 27 checked, 92 outstanding; ACIE-078/079/080/082/083/102 still require broader evidence; ACIE-055/065/115 not accepted. 

## Session update — merged calculator contract and paired synthesis diagnostic

#425 passed Python 45735800788, Android 45735800877 and actual-model calculator 45735800822; merged 5322a666e94e9660d816c55c3b4fc529c7264134. Inspected artifact 15078559636: historical suite still 8/8 correct calculation values, 0/8 strict synthesis, 4/4 unsuitable proposals rejected, 0/4 explicit NONE, all planning and attempted synthesis complete. Retain disagreement and completion warnings, not an accuracy improvement claim. Fresh paired diagnostic adds direct versus thinking synthesis from the same accepted plan and exact Kotlin prompt, matched seed and 768-token cap, alternating order, fixed positive denominator including rejected plans, separate outcomes for mismatch, numeric disagreement, noncanonical/ungraded and incomplete output. No answer keys or raw response export; no deployed inference change. Existing historical suite runs unchanged alongside fresh paired suite. Two evaluator regressions added. All 119 IDs verified, 27 checked and 92 outstanding. ACIE-078/079/080/082/083/102/110/115/117 remain pending broader evidence. CI and fresh actual-model results pending; no new master checkmarks or independent score.

## Session update — evidence-led calculator thinking integration

#427 passed Python 45739204379 and Android 45739204431 and merged 7ecb849f7e41e4c8e5330e841b93bf7678bd338d. Independent reviewer commitment and physical-device assessment remain pending. #426 passed Python 45738948900, Android 45738948938 and CPU 45738948918; merged e53730c9c48a2d8b9aacdd9e235f337d6873b3ab. Artifact 15080014797: historical direct strict synthesis 0/8; fresh matched direct 1/8 versus thinking 7/8, both 1, thinking-only 6, neither 1; all 8 correct computed values, 4/4 unsuitable rejected, 0/4 explicit NONE. Direct synthesis 199 tokens/46354 ms versus thinking 1953 tokens/171414 ms. #428 passed Python 45739511942, Android 45739511872 and CPU 45739511835; artifact 15081985603: authority candidate and baseline both 6/8, quote 2/4 and control 4/4 each; no paired gain. Retain production system prompt. Separate breadth seed gave direct 6/24, thinking 15/24; quantitative 0/6 vs 1/6; previous 18/24 thinking result not reproduced on this different seed. #428 is now conflicted after #426 merge; do not merge stale ledger. New production candidate limits evidence-led thinking synthesis to the explicit numeric-only calculator route, excluding explicit fast preferences. Exact typed computed prompt, no prior turns, no memory or book/course references, model-only inference isolation, 768-token cap. Standard chat, diagnostics and default-off automatic adaptation unchanged. Existing disagreement warning and independently calculated footer preserved. Regression tests added; Android/native handset acceptance outstanding. All 119 IDs reviewed; 27 checked, 92 outstanding. ACIE-023/078/079/080/082/083/102/109/110/117 remain open. No independent intelligence score or release claim.

## Session update — calculator integration regression reconciliation

#429 initial Python 45787483197 and Android 45787483239 failed the same existing static owner-intent assertion (test_inference_intent_source.py line 23). The Android workflow stopped before compilation. Updated the assertion to require both the calculator exclusion and the existing owner-intent reference guard, plus the brain-side isolation check; no relaxation of retrieval safeguards. Initial actual-model run 45787483195 still in progress at review. Fresh CI required. All 119 sequential requirement IDs checked against ledger; 27 integrated engineering items, 92 outstanding. ACIE-078/079/080/082/083/102/110/117 remain unaccepted. No new model-quality or release claim.

## Session update — instruction diagnostic conflict reconciliation

Reconciled all three non-ledger files from #428 (profile evaluator, its tests and workflow) into #429 after confirming each current blob matched its exact original ancestor at 5322a666. Resulting blobs exactly match the validated #428 candidate. Preserve newer master ledger instead of overwriting it. #428 evidence remains neutral (6/8 both arms); candidate authority suffix remains experimental and is not added to production. Fresh combined Python, Android and native CPU checks required before integration. All 119 sequential IDs verified again; 27 checked and 92 outstanding. No new master acceptance or general intelligence rating.

## Session update — opt-in multi-quantity reasoning route

#429 merged 3056246d374a793773223df4c989bcf3ae2d8915 after Python 45787857335, Android 45787861266, calculator CPU 45787857298 and profile CPU 45787857349 passed. #430 head 1758bce599c198e44b77444e1f8c73d0e5eae8fb passed Python 45794330333; Android 45794330336 and actual quantitative evaluation 45794330334 remain in progress at review. On separate branch from current main, extend existing owner-opt-in adaptive route to multi-quantity numerical questions: bounded current request, at least three explicit numeric literals and a numerical question cue, reject quoted/source/code/grouped-number material for this new heuristic, preserve the two-number simple arithmetic fast path and explicit fast requests including /no_think. Existing explicit thinking, diagnostic isolation and default-off adaptation remain unchanged. Two JVM regression methods cover positive numerical cases, opt-in/default-off, diagnostic and negative cases. No new model weights, system prompt, generation cap or independent score; routing heuristic is not a semantic solver or proof of accuracy. All 119 sequential requirement IDs reviewed, 27 checked and 92 outstanding. ACIE-028/029/057/110/117 remain open until CI and actual comparative evidence, including handset latency, exist. #430 remains a separate candidate experiment, not deployed. Reconcile its ledger if merged later.

## Acceptance

90/100 independent repeated general intelligence score; separate model-only/assisted results; no model substitution, paid API or unsupported capability claims. Freeze protocol before held-out testing. Preserve offline and memory constraints. Recheck any completed engineering item if a later regression invalidates it.
