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

## Latest engineering session

All 119 IDs reviewed; 27/119 unchanged. Artifact 15069384240 confirms hard-direct generated 168 vs 287 tokens, both 6/6 complete; compact thinking completed 4/6 vs production 6/6. Most strict failures are noncanonical/ungraded, not proven wrong conclusions. New explicit integer/yes-no format normalizer strips only unambiguous wrappers and leaves model-only, diagnostic, grounded and capped replies unchanged. No claim of improved reasoning score. ACIE-102 remains unchecked pending Android CI and broader native adherence evidence.

## Latest session: integrated source attribution and token allocation

All 119 ACIE IDs reviewed and preserved. #411 Android CI passed and the response-format layer merged at 688e4d3537dce92213cadab26ccbaae67c8521d0. #412 is integrated after Android/Python passes; independent 200-case protocol freeze and test evidence still pending. #413 Python and compiled arithmetic tests passed; Android validation pending at reconciliation. #414 added whole-brief attribution, streamed and final memory citation validation, and literal ID escaping; Python 703-test run had one outdated source assertion, corrected here. Reconcile both changes against merged #411, retaining the scalar contract and original model-only/diagnostic behavior; rerun combined native Android/Python CI. ACIE-010, ACIE-029, ACIE-059, ACIE-102 and ACIE-109 remain open until their actual acceptance evidence exists. No new master checkmarks; 27/119. Citation identity is not proof of entailment and token admission is not demonstrated reasoning gain.

## Session update — verified integrations and model-led formalization

#415 passed both Python (45719691746) and Android (45719690509) and merged as 5e53ec72b8e222be707b2063409d1307f2d4e04d. #416 Android passed (45720596505) and merged as cd1bf9f7853fcc71996780673ba81142cf51114b. Integrated engineering includes typed memory attribution checks, exact-token context admission and bounded propositional verification. Broader actual grounding and handset verification remain pending for ACIE-010/029/058/059. This session adds an explicitly owner-confirmed formal argument verification path and an optional model-generated, unverified proposition mapping in the existing audit path. No auto-acceptance of model interpretations. Fresh CPU diagnostic compares 8 balanced new cases under deployed decoding profiles (32 requests), soft/hard direct and baseline/decomposition thinking separately; no production decoding change. Tests and actual CPU comparison pending. All 119 IDs preserved; 27 checked, 92 outstanding; no new broad acceptance or intelligence score.

## Latest session — verified neural comparison and integrated calculator

#417 passed Python, Android and actual Qwen3 CPU evaluation 45722476790; merged 78bf93adc5289585f74be1b1327ae7dc4854de3d. Artifact 15073607086 (seed 283083470, pinned model): standard thinking 8/8 strict, direct soft/hard each 3/8, decomposed thinking 3/8, all 32 complete. Standard thinking 1949 tokens/175060 ms versus decomposition 2439 tokens/201602 ms. Direct each one wrong scalar and four ungraded noncanonical answers; decomposition five ungraded noncanonical answers. Retain standard system prompt and deployed sampling. This is one public synthetic sample, not independent or physical-device evidence.

#418 passed Python and Android and merged 3e570fc64d6b22c9dd99685cc25abcc4052fa143. #419 revised calculator branch passed Python 45723825148 and Android 45723825010; conflicts after #418 reconciled here with both current-owner intent and calculator safeguards preserved. Existing source/privacy checks retained. Fresh integration CI required. All 119 IDs reviewed, 27 checked, 92 outstanding; no broad acceptance yet. ACIE-010/016/023/028/029/030/058/059/078/079/080/082/097/099/102/109 remain under validation. No official general intelligence score.

## Acceptance

90/100 independent repeated general intelligence score; separate model-only/assisted results; no model substitution, paid API or unsupported capability claims. Freeze protocol before held-out testing. Preserve offline and memory constraints. Recheck any completed engineering item if a later regression invalidates it.
