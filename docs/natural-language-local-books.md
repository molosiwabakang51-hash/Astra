# Ask imported books in natural language — private, offline, explicit

**Status: experimental until verified on an owner phone.**

Astra's original private-library command is `ask my books <question>`.
This enhancement additionally recognizes explicit natural requests such as:

- `According to my books, how does bond duration work?`
- `Using my book, explain inflation risk.`
- `What do my books say about convexity?`
- `Can you check my books for option pricing?`
- `Please search my books for the difference between coupons and yields.`

These are **owner instructions to query personally imported books**, not ordinary
open-ended chat prompts. They go through the same local retrieval pipeline,
consent-aware excerpt injection and citation-fit safeguards as `ask my books`.
An unrelated question, an assistant-generated command or a vague reference to
books does not automatically authorize reading private files.

The generated response must not treat an earlier Astra claim as source
evidence. If books were not imported, no relevant passage was indexed or the
passage could not fit in Qwen's prompt, Astra must not fabricate citations.
There is no added internet service or upload of personal book content.

## Device acceptance protocol

Run in airplane mode with the imported Qwen3 1.7B model and a **non-sensitive**
sample source (for example a fictional lesson on bond duration). Verify:

1. `According to my books, explain bond duration.` returns locally indexed,
   relevant evidence with an attribution marker that actually corresponds to
   the prompt's supplied text.
2. `What do my books say about convexity?` searches only the imported
   source library and does not send excerpts over the network.
3. `Can you check my books for modified duration?` and
   `Using my books, explain interest-rate risk.` both reach the same retrieval
   path without first requiring the legacy `ask my books` prefix.
4. A normal `Teach me finance`, `Show my books`, `I have books` or
   `Search books online` must **not** trigger private source retrieval.
5. Pasted multiline instructions cannot bypass the single-message consent
   classifier, create a task/note, or execute imported text.
6. A new chat must not gain previous chat consent. Explicit book follow-ups
   are separately restricted to citation-backed, same-chat, completed turns.
7. Stop generation mid-reply, then restart. No interrupted answer may be used
   to claim a verified citation or an earned intelligence score.

Automated parser and source-contract tests are necessary but do **not** prove
real-world Qwen3 retrieval quality, speed or the 8.5/10 goal. Record errors
and pass/fail cases under the canonical 30-case, 100-point handset framework.
