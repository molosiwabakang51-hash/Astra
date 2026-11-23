# Qwen3 real-model multi-turn reasoning test

**Status: experimental, owner-phone results outstanding.** This feature is a
test harness that uses the actual pinned Qwen3 1.7B native model. It is not an
AI rating, not neural training, and is **not** part of the 30-case, 100-point
official phone quality scorecard.

## Owner action

In Settings, select **Test multi-turn Qwen reasoning**. Astra executes four
synthetic three-turn conversations in complete isolation from real chats:

1. A fictional bond maturity is corrected from seven years to three years.
   The final model answer must reflect the latest correction.
2. Two fictional companies appear in successive topics. The model must answer
   for the company associated with the more recent topic.
3. A hypothetical project discount rate is corrected from 8% to 10%. The
   final question requires using the corrected rate to calculate present
   value on a one-year P110 payment.
4. An invented location, Maun, is introduced in English and recalled in a
   French-language follow-up.

Each scenario is a **new isolated native generation**. Within each scenario,
the three requests share the same generation identifier and the same
model-session context, so this tests actual Qwen3 conversation continuity
rather than the deterministic note/task or calculator handlers. Each turn
has a 128-token output limit. Both final-answer accuracy and **earlier-turn instruction adherence** are
reported separately, together with total elapsed milliseconds per scenario.
The earlier two replies must match their explicitly requested concise ACK/OK
or location answer. This prevents a correct final answer from concealing
previous failures to follow user instructions; it does not imply a score for
unmeasured real-world dialogue.  the user's chats, notes,
books, private facts and passwords do not enter the prompts. The report
does not export model responses or private user data.

## Limitations and handset acceptance

These are four narrow controlled tests; passing them does not establish
open-ended reasoning, explanation quality, multilingual fluency or an 8.5/10
intelligence level. A small model can exhaust its token budget, and cold-start
loading may dominate timing. A scenario fails if its final output is not a
correct, concise answer; an interrupted or incomplete run has no score.

To collect genuine evidence, on a physical ARM64 phone with the official
Qwen3 1.7B weights imported: enable airplane mode, launch Settings, run the
four-case sequence, and copy the local report. Repeat after closing and
reopening the app. Record failures and elapsed time for **all** cases.
Separately perform the canonical 30-case/100-point offline AI quality
evaluation in `docs/ai-quality-evaluation.md`, including broader
multi-turn and multilingual behavior. No device assessment or score is
claimed until these observations actually exist.
