# Offline Qwen source-citation provenance

**Experimental until owner-phone verification.** This is a source-integrity
mitigation, not a neural-model reasoning score.

## Why

A local language model can produce text that **looks like** a book citation
(`[B3]`) or a complete source-attribution footer even when it never received
that passage. Earlier versions appended genuine, app-created citations for
passages actually included in the Qwen prompt, but did not validate
citation-like identifiers appearing **inside the generated answer**.

That can mislead the owner about the evidence underlying an explanation.
It also matters for follow-up conversations, because book-grounded follow-up
routing checks for the app's verified private-book citation footer.

## Boundaries and implementation

The Kotlin native output bridge now applies a deterministic citation guard
before appending attribution footers that it creates itself.

- If exactly one private source block survived into the complete model prompt,
  `[B1]` may remain in the answer; `[B2]`, `[B3]`, `[K1]` and other
  unsupported citation identifiers are marked unverified rather than being
  certified.
- If two private passages were supplied, `[B1]` and `[B2]` may remain.
  Open-course `[K#]` passage identifiers are checked separately against
  the actual attached open-course passages.
- A Qwen-generated line impersonating the app-only headings
  `Retrieved local book passages (verify originals):` or
  `Open-course references:` is stripped and marked unverified.
- The app-owned footer is appended **after sanitization** using only source
  blocks verified against the final native prompt, not arbitrary model text.
- The owner still needs to read the primary passage: valid source-index
  references do not guarantee that the model interpreted the evidence
  correctly. Citations are not proof of factual truth.

No new model weights, private-book imports, network connections, permission,
logging of source passages or task/note mutations are introduced.

## Repeatable offline device test

Use an imported, **synthetic**, non-sensitive book in airplane mode, then:

1. Ask an explicit book question that retrieves exactly one test passage.
   Verify the app-created footer contains only `[B1]`, citing that passage.
2. Ask about a fact not present in that passage. If Qwen invents `[B2]` or
   `[K1]`, confirm those markers do **not** appear as verified citations.
3. Check an open-course reference answer and confirm it cannot impersonate
   imported private-book evidence.
4. Ask `Continue using my book` after a **genuinely cited** completed answer
   and verify the follow-up reretrieves fresh passages. Repeat after an
   uncited answer or interrupted generation: the previous model-generated
   footer may not silently grant book access.
5. Repeat after chat restart. Inspect whether content is still readable and
   source metadata actually corresponds to the original locally imported
   document. No network requests or unexpected task actions are permitted.

Kotlin/JVM regression tests cover invented markers, overlarge IDs, forged
headings and impossible passage counts. Those checks do **not** demonstrate
real-world model grounding accuracy or earn the 100-point handset score.
