# Astra · reproducible 8.5/10 AI quality evaluation

**Status:** measurement protocol, NOT an earned score.  
**Campaign:** 15–30 November 2026.  
**Purpose:** measure useful, reliable **on-phone** assistant capability rather than code size,
number of commands, passing compiler checks, or the theoretical ability of a GGUF model.

## Test conditions and evidence

1. Use the same ARM64 Android handset and a recorded Astra commit/version, model
   label and checksum across repeated runs. Record Android version, available
   memory, airplane-mode status, model used, battery, wall-clock times, crashes,
   and outputs. Test only verified Qwen3 1.7B Q4_K_M. A legacy 0.6B
   file is NOT acceptable test evidence or a supported conversation fallback.
2. Start with the owner-approved test dataset below, not personal diary entries,
   credentials or proprietary course materials. Do not upload chat archives.
3. Enable airplane mode and disable Wi-Fi before the offline test. Disable any
   optional network research. Screenshots and local diagnostic exports should
   contain no private data. Run model tests in **open-ended Qwen chat**, not only
   deterministic calculators, built-in dictionaries or hardcoded responses.
4. Repeat the scorecard on a later app start or after native-model reload.
   Record both scores and regressions; no cherry-picking the better result.
5. Each test is **pass = full assigned points, fail = zero, unverified = no score**.
   A test without recorded on-phone evidence is *unverified*, not a pass. The
   8.5/10 label is **withheld** if mandatory gates are unverified regardless
   of the raw subtotal.

## The 100-point scorecard

| Domain | Cases | Points per case | Total |
| --- | ---: | ---: | ---: |
| A. Open-ended conversation quality | 5 | 5 | 25 |
| B. Finance, math and deduction | 5 | 4 | 20 |
| C. Multi-turn context and corrections | 5 | 3 | 15 |
| D. Multilingual open-ended communication | 5 | 3 | 15 |
| E. Confirmed local tool operations | 5 | 3 | 15 |
| F. Actual offline/device release gates | 5 | 2 | 10 |
| **Total** | **30** | | **100** |

### A. Open-ended conversation quality — 25 points

Each response must address the **actual request**, avoid hallucinated live facts,
and deliver the requested level of detail. A command-list dump, stock greeting,
vague template, server/API suggestion or irrelevant fallback **fails**.

- **A1 (5):** "Explain bond duration in plain English and give an example of
  what happens when interest rates rise." Pass: correct inverse rate/price
  relationship; meaningful duration/sensitivity example.
- **A2 (5):** "Compare a fixed-rate government bond with an inflation-linked
  bond, including two tradeoffs." Pass: correctly discusses inflation protection,
  nominal/real yield and rate/liquidity risk without live-price invention.
- **A3 (5):** "Teach me compound interest step by step using P1,000 and 10% a
  year for three years." Pass: explains compounding and obtains P1,331.
- **A4 (5):** "Help me build a realistic five-step plan to learn basic Python.
  I have never programmed." Pass: coherent ordered steps and one first exercise.
- **A5 (5):** "Describe a situation where a simple answer would mislead me,
  then explain the tradeoff." Pass: specific example, competing considerations,
  clear limits and no invented sources.

### B. Finance, math and deduction — 20 points

- **B1 (4):** "What is 19 plus 23? Reply only with the number." Exact **42**.
- **B2 (4):** "What is 17 multiplied by 19? Reply only with the number." Exact **323**.
- **B3 (4):** "P1,000 earns 10% annually, compounded for three complete years.
  No deposits or withdrawals. Final balance?" **P1,331** (reasonable formatting).
- **B4 (4):** "All larks are birds, and no birds are robots. Can any lark be a
  robot under these premises? Answer yes or no." Exact **No**.
- **B5 (4):** "If a five-year modified-duration bond experiences a one percentage
  point increase in yield, approximately how much does its price change,
  ignoring convexity?" **Approximately −5%**, with sign and approximation clear.

### C. Multi-turn context and correction — 15 points

Test each case with an actual new conversation, including the specified
follow-up. Record whether model KV-cache was reloaded or the app restarted.

- **C1 (3):** "Remember fictional codeword NIMBORA for the next question."
  Then "What was the codeword?" Must return **NIMBORA** without mixing contexts.
- **C2 (3):** "Assume the hypothetical bond has a 6% coupon."
  Then "What coupon did I give?" Must answer **6%**.
- **C3 (3):** "For our fictional example, use a seven-year investment horizon."
  Then "Correction: make that three years." Then "What is the horizon now?"
  Must answer **three years**, not seven.
- **C4 (3):** "We're discussing government bonds, not equities."
  After several unrelated messages, "Return to our earlier asset topic."
  Must identify **government bonds** using same-chat history, without inventing
  private history.
- **C5 (3):** Open another conversation with a different fictional codeword,
  return to the original, and ask for its codeword. Must return the right
  codeword **only within its authorized conversation**, not cross-chat leakage.

### D. Multilingual generative communication — 15 points

Verify **actual local Qwen inference** when testing general language ability.
Built-in lesson/dictionary correctness is a useful separate feature but does
not independently establish generative understanding.

- **D1 (3):** Russian: translate **спасибо** into English → *thank you/thanks*.
- **D2 (3):** Setswana: translate **Dumela** into English → *hello/hi*.
- **D3 (3):** French: translate **bonjour** into English → *hello/good morning*.
- **D4 (3):** German: translate **Danke** into English → *thank you/thanks*.
- **D5 (3):** Spanish: translate **gracias** into English → *thank you/thanks*.

### E. User-confirmed local actions — 15 points

Use fictional tasks, notes and shopping items. Verify persisted state, not just
the assistant's spoken or printed claim. The language model must never execute
arbitrary untrusted text.

- **E1 (3):** `workflow: add task Practice duration; add note Test example`.
  Both items must appear in a **preview** and **not** save before confirmation.
- **E2 (3):** After E1, type `confirm workflow`; both save atomically, survive
  an app restart and appear in the expected local collections.
- **E3 (3):** Stage a plan containing a valid action then `complete task 9999`.
  Reject the **entire** plan, leaving all collections unchanged.
- **E4 (3):** Stage a plan, switch/clear the conversation, then type
  `confirm workflow`. Nothing should apply after the transition.
- **E5 (3):** Stage a workflow, change the underlying task list, then confirm.
  Stale confirmation must be rejected without partially applying actions.

### F. Real Android offline/reliability gates — 10 points

- **F1 (2; mandatory):** With airplane mode and Wi-Fi disabled, **Test on-device
  AI** returns exactly `ASTRA OFFLINE` from the native model and independently
  generated open-ended text. No gateway, API or second computer.
- **F2 (2):** Long streaming answer is cancellable; follow-up generation works,
  and no stale partial output crosses into another conversation.
- **F3 (2):** Record time-to-first-token, full response time, RAM/thermal behavior
  and 5-minute battery impact; no crash/ANR or storage exhaustion.
- **F4 (2; mandatory):** Two successive **same owner-key-signed** LITE APK updates
  retain chat, tasks, notes and privately imported GGUF. Ephemeral CI signing
  keys are not evidence of this.
- **F5 (2; mandatory):** Optional storage grants can be declined/revoked;
  no app-private data from other apps is exposed; shared diagnostics contain no
  chat text, passwords or tokens; no unintended network transmissions.

## Score conversion and release gates

- **Reported score:** points obtained / 10, but only when all 30 cases have
  been evaluated on the real phone with recorded evidence.
- **Campaign baseline:** at least **85/100 = 8.5/10** on **two complete runs** using the
  same disclosed configuration, with mandatory F1/F4/F5 passing both times.
- **No automatic score inflation:** unit-test counts, merged PR counts,
  bundle size, synthetic JVM outputs and static code inspection cannot be
  counted as real-phone AI quality points.
- **Non-negotiable stable release:** all criteria in
  [stable-release-acceptance.md](stable-release-acceptance.md) remain binding.
  A quality score alone never implies Android release/signing readiness.
- **Partial progress:** report CI passes and unverified handset categories
  separately. Never manufacture scores for tests that were not run.

## Record template for each two-day review

| Field | Required entry |
| --- | --- |
| Date / Astra commit / device / Android | Explicit values |
| Actual Qwen model and checksum | Verified identifier or UNVERIFIED |
| Airplane mode and network status | Evidence or UNVERIFIED |
| A, B, C, D, E, F points | Per-case pass/fail or UNVERIFIED |
| Run 1 and Run 2 totals | Compute only after all 30 per run |
| First-token / response time / battery / RAM | Measured values or UNVERIFIED |
| GitHub Actions run and failure summary | Link and job result |
| User/private-data/signing acceptance | PASS / FAIL / UNVERIFIED |
| Largest unresolved capability defect | One reproducible example |
| Next code/test action | Specific issue, PR and acceptance test |

## Coverage traceability and score boundaries

The 30 scored cases are deliberately weighted toward **real generative behavior**.
The following is a **coverage map**, not another 100 points and not a claim
that every named capability has been adequately tested. Cases may appear in
multiple rows, but each case earns its original points **only once**.

| Required dimension | Scored cases | Additional release evidence required |
| --- | --- | --- |
| Natural conversation | A1–A5 | Human-rated clarity, concision and hallucination review |
| Factual accuracy | A1–A3, B1–B5, D1–D5 | Check answers against independent answer keys; no live-data invention |
| Reasoning | A2, A5, B3–B5 | Multi-step explanations on both model sizes |
| Multi-turn context | C1–C5 | Restart and conversation isolation |
| Language | D1–D5 | Free-form Russian, Setswana, French, German and Spanish follow-ups |
| Tool correctness | E1–E5 | Persistence, atomicity, rejection and rollback |
| Startup and latency | F1–F3 | Cold/warm first-token timing, 5-minute RAM/thermal/battery observations |
| Safety and privacy | E1–E5, F4–F5 | Consent, network capture, credential hygiene, update preservation |
| Handset usability | F2–F5 | TalkBack, font scaling, keyboard, navigation and crash/ANR review |

**Blind spots:** D1–D5 are short translations and do not alone establish
multilingual conversation. F3 records performance but sets no minimum
latency threshold; reviewers must report actual values and judge usability
rather than treating the existence of a measurement as proof of speed.
The release acceptance checklist remains binding even if 85 points are
recorded. Do not convert the 4.5/10 provisional development estimate into a
measured score without a complete phone run.

### Machine-readable case manifest

The repository maintains `scripts/quality/scorecard_cases.json` with
the 30 unique IDs, their exact assigned points, and whether they require
physical-device evidence. Run
`python3 -m unittest discover -s scripts/tests -p 'test_quality_scorecard.py'`
to catch missing/duplicated cases, altered weights and unsupported
automatic scoring. This is **schema validation**, not an AI benchmark.
