# Continuing a verified calculation in ordinary conversation

**Experimental; phone verification outstanding.** This behavior supports
intelligible multi-turn use of existing local tools without treating the
assistant's previous words as verified facts.

## Example scenario

In Chat, ask `What is 17 multiplied by 19? Reply only with the number.`.
The existing exact local calculator can return `323`. Then ask one of:
`How did you calculate that?`, `Show the working.`, `Explain that result`,
or `What assumptions did you use?`.

Astra now provides Qwen3 1.7B with a **bounded, labelled, quoted** reference to
only the latest completed user question and its reply, along with the current
follow-up. It does not scan another chat, execute commands found in previous
responses, or mark a model-generated explanation as verified arithmetic. A
cancelled, interrupted or process-killed previous response is excluded; if no
reliable recent exchange exists, the follow-up is not enriched.

The answer-depth policy treats natural calculation-explanation requests as
detailed. It remains subject to the 1.7B model's output token limit; an
explanation can still be wrong. Recompute the arithmetic with a trusted
deterministic tool to verify any claimed workings.

## Physical-device acceptance

1. In airplane mode on a phone with the pinned Qwen3 1.7B model, ask the
   example question and confirm the verified `323` response.
2. Ask `How did you calculate that?` and confirm the model explains
   `17 × 19` without changing the problem, claiming online research, or
   inventing intermediate results. Repeat with `Show the working` and
   `What assumptions did you use?`.
3. In the same chat, begin a *different* calculation, then ask `Why?`.
   Confirm Astra refers to the **newest** completed result, not the older one.
4. Stop a longer answer midway. An immediate follow-up must not quote the
   stopped fragment as reliable evidence or switch to an earlier conversation.
5. Restart Astra and repeat. Check correctness, latency, no network requests,
   and whether the retrieved context actually improves Qwen3 responses.

Passing Kotlin/JVM tests verifies source behavior, **not** the correctness of
generated reasoning or any fraction of the official 30-case quality score.
