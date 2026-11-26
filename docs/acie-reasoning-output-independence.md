# Independent reasoning-mode and output-length selection

Actual CPU experiment 45722476790, artifact 15073607086: eight balanced public synthetic cases; ordinary thinking 8/8 strict, decomposition 3/8, each direct arm 3/8. All 32 completed. This motivates removing an artificial coupling between concise final output and computation mode, not a claim that this routing change improves general accuracy. No prompt, sampling, default setting or token cap change.

When automatic reasoning is explicitly enabled by the owner, already recognized complex requests no longer force direct mode just because the owner asks for a brief or answer-only response. Explicit fast/no-thinking wording, ordinary simple questions, existing source isolation, language/quotation tasks and diagnostic isolation retain their safeguards. Explicit careful mode retains its existing precedence. Existing output-format instructions remain in the actual model prompt. The existing thinking-mode budget includes internal computation; this does not guarantee brevity, correctness or faster responses. No new complexity patterns were tailored to the evaluation items.

Android regression coverage updated to distinguish final-answer preferences from explicit speed preferences, test both opt-in settings, and preserve diagnostics. ACIE-028/029/099 remain open until actual handset paired reasoning, latency and instruction-adherence evidence. Existing full ledger is in #420; this separate PR avoids competing writes to that file.

## Regression follow-up

Android run 45726062537 failed the concise-output test because the anchored deductive pattern did not permit a leading brevity modifier. Align task-opening, deduction and language guards on bounded leading `briefly`/`concisely` modifiers; permit the same leading modifier on tradeoff questions. Preserve all existing source, fast-mode and diagnostic restrictions. Added short-form and translation counterexamples. Fresh Android verification required; this is not an actual-model accuracy result.
