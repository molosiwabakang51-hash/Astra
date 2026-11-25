# Bounded recursive revision

Use a local reviewed evidence ledger, a UTF-8 question file and trusted compatible llama.cpp executable with two distinct local models.

```powershell
python desktop/astra_revision_cli.py --question-file question.txt --ledger evidence.sqlite --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf" --critic "C:\Models\critic.gguf" --approve-models
```

The pilot generates an original draft, asks an independent model for concrete concerns, revises at most once if concerns exist, then requests an advisory comparison. At most four sequential model calls are permitted; the runtime rechecks available RAM for each. It avoids revision if the critic reports no concrete concerns, is unavailable, or the prompt exceeds the budget. Both valid drafts remain available and the reviewer may prefer the original, tie, or express uncertainty.

All supplied evidence is checked before and after each model call, including uncited sources. A changed record invalidates all drafts. Citation identifiers are validated, not their factual entailment. The comparative reviewer has seen the original during critique, so hiding candidate labels is not a genuinely blinded evaluation. The preference is advisory and does not establish improvement.

No automatic persistence, training, evidence modification or arbitrary tool execution. Native runtime compatibility, response quality, and latency on the target computer remain unverified. Initial tests should use synthetic questions and evidence. At the default 180-second per-call timeout, four native calls can consume up to 720 seconds of subprocess execution plus startup and other overhead. Do not use confidential material in the experimental runtime.
