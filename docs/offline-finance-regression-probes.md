# Offline finance and crash-recovery regression scenarios

These are **additional unscored acceptance probes** for issue #142 and issue #111.
They do not award points in the 100-point handset scorecard. Run in airplane
mode with Wi-Fi disabled; record model/version, output and crash status. Use
fictional examples only. Evaluate 0.6B and imported 1.7B separately.

| ID | Exact user prompt | Required behavior |
| --- | --- | --- |
| FIN-01 | I live in Botswana. If I buy a US dollar stock, does investing in USD eliminate exchange-rate risk for me? | **No.** Distinguish USD-denominated price movements from BWP/USD translation, conversion fees and the investor's spending currency. No guaranteed hedge claim. |
| FIN-02 | Can every person in Botswana open an account with any US stockbroker? | **No.** Broker residency eligibility, identity checks, tax documentation and product access vary. Do not invent a specific broker's current policy offline. |
| FIN-03 | If a bond's yield rises one percentage point and its modified duration is five, what is the approximate price move? | Approximately **-5%**, ignoring convexity; no claim of an exact change. |
| FIN-04 | I borrow P10,000 at 15% to invest in something promising 10%. Is the spread a guaranteed profit? | **No.** Negative nominal carry before costs, investment risk, financing and loss amplification; no invented guaranteed return. |
| FIN-05 | What is the latest USD/BWP exchange rate right now? | Explicitly disclose inability to verify live rates offline. Do not fabricate a current quote or timestamp. |
| FIN-06 | Teach me finance. | Generate a relevant introductory answer or a recoverable error **without process termination**. Repeat five times, then retry after Stop and after app restart. |
| FIN-07 | Explain currency risk using P1,000, then go into more detail for your fourth point. | Maintain the correct numbered referent in the same chat, without leaking another chat's history. |

## Recording protocol

For every probe record **PASS / FAIL / UNVERIFIED**, verbatim output, model
identifier, device/Android version, elapsed time and whether any process crash
or ANR occurred. Do not treat a deterministic built-in response as proof of
general Qwen reasoning. A PASS requires an actual locally generated response
where the test concerns generative ability.

For FIN-06, collect the Android **crash buffer** if the process exits:
`adb logcat -d -b crash -v time`. Review and redact diagnostics locally
before sharing. Never attach chat histories, imported books, tokens, signing
material or private GGUF files to public GitHub issues.

**Release decision:** These probes supplement, never replace,
`docs/ai-quality-evaluation.md` and
`docs/stable-release-acceptance.md`. CI may verify test fixtures but cannot
claim phone runtime or a higher quality score.
