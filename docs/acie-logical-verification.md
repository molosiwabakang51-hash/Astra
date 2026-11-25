# Independent propositional logic verification

Astra's explicit "Verify your answer" path now supports a bounded formal argument checker as an independently computed assisted verification. It does not rewrite model-only answers or silently turn arbitrary natural language into logic. Example question:

Premises: A -> B; B. Conclusion: A. Does the conclusion follow? Reply YES or NO only.

If the model answered YES, the review reports NO with the counterexample A=false, B=true. Operators: uppercase single-letter variables, ! negation, & conjunction, | disjunction, -> material implication, and parentheses. Operator precedence is documented in the parser. The entire request must match, maximum 240 characters, six premises, eight distinct variables and 256 truth assignments. The verifier distinguishes inconsistent premises (vacuous classical entailment) from a substantive proof. Unsupported syntax and ambiguous answers stay on the existing clearly labeled model-led review path. No model-weight change, private retrieval, new permissions or external APIs.

Six JVM regression methods cover valid inferences, invalid inferences, precedence, contradictions, resource bounds and integration. ACIE-058 remains open until Android CI, independent review of logic scope, and actual model-assisted review on the handset. This does not establish general logical intelligence or improve an unassisted benchmark score. ACIE master ledger remains 27/119 until evidence supports an additional completion.
