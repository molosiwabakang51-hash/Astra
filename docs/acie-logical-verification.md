# Independent propositional logic verification

Astra's explicit "Verify your answer" path now supports a bounded formal argument checker as an independently computed assisted verification. It does not rewrite model-only answers or silently turn arbitrary natural language into logic. Example question:

Premises: A -> B; B. Conclusion: A. Does the conclusion follow? Reply YES or NO only.

If the model answered YES, the review reports NO with the counterexample A=false, B=true. Operators: uppercase single-letter variables, ! negation, & conjunction, | disjunction, -> material implication, and parentheses. Operator precedence is documented in the parser. The entire request must match, maximum 240 characters, six premises, eight distinct variables and 256 truth assignments. The verifier distinguishes inconsistent premises (vacuous classical entailment) from a substantive proof. Unsupported syntax and ambiguous answers stay on the existing clearly labeled model-led review path. No model-weight change, private retrieval, new permissions or external APIs.

Six JVM regression methods cover valid inferences, invalid inferences, precedence, contradictions, resource bounds and integration. ACIE-058 remains open until Android CI, independent review of logic scope, and actual model-assisted review on the handset. This does not establish general logical intelligence or improve an unassisted benchmark score. ACIE master ledger remains 27/119 until evidence supports an additional completion.

## Model-led interpretation bridge

The ordinary answer audit may now propose an explicitly unverified mapping of natural-language statements into propositional formulas. The owner can review the proposed mapping and submit the full checked argument with `Verify this formal argument: Premises: A -> B; B. Conclusion: A. Does the conclusion follow?` for a deterministic check without needing a prior exchange. A bare affirmation never accepts or executes a model proposal. No independent claim about the original natural-language meaning is made. The verifier deliberately refuses unsupported syntax and cannot establish first-order, modal, causal or probabilistic entailment.

## Fresh actual-model evaluation

`fresh_reasoning` generates eight new public synthetic cases with balanced yes/no outcomes, quantitative and constraint tasks, and randomized entities, order and operands. At deployed Qwen sampling, direct compares soft vs hard framing; thinking compares baseline vs a generic constraint-decomposition prompt. Each pair fixes seed, model, system, budget and decoding settings. No answer or solver enters the prompt. This is exploratory CPU evidence, not independent generalization, not Android JNI and not a /100 rating. Existing experiments remain CLI-accessible. No production prompt or sampling changes until results justify them.
