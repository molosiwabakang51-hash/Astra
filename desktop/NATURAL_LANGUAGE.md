# Natural-language desktop pilot

From the repository root, with the dependent orchestrator integrated:

```powershell
"Please calculate the percentage change from 80 to 100." | python desktop/astra_text.py
"What is the present value of 121 at 10% over 2 years?" | python desktop/astra_text.py
"weighted portfolio return: 30% at 10% and 70% at 5%" | python desktop/astra_text.py
"Find my notes about bond duration" | python desktop/astra_text.py --ledger evidence.sqlite
```

Numerical results use decimal ratios for percentage change and weighted returns. The first and third examples return 0.25 and 0.065 respectively (25% and 6.5%). Finance requests bypass neural inference even with an approved model configuration.

For an evidence-grounded draft, use a UTF-8 input file to avoid exposing your question as a process argument:

```powershell
python desktop/astra_text.py --input-file question.txt --ledger evidence.sqlite --approve-models --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf"
```

Add `--review --critic "C:\Models\critic.gguf"` to request an independent advisory critique. Use trusted local executables and model files. This does not constitute factual certification. Without model approval, non-calculation questions return relevant reviewed records instead of generated answers.

Supported calculation grammar is deliberately bounded English: percentage change, annual present/future value, compound growth, and weighted returns with explicit percentage pairs. Ambiguous calculation-like requests ask for clarification; no guessed numeric inputs or model-driven tool execution. Other text routes to reviewed evidence, not unrestricted conversation. Multilingual routing, follow-up resolution, GUI and actual native runtime validation remain outstanding.

## Interactive financial scenarios

Start an in-memory session:

```powershell
python desktop/astra_session.py
```

Enter one question per line:

```text
Calculate the present value of P10,000 at 8% over five years.
What happens if interest rates increase to 10%?
Compare both results and explain the implications.
What about six years?
Compare with original
reset scenario
exit
```

An explicit present/future value calculation establishes the current amount, annual rate, term and, when supplied, BWP currency. Follow-ups change one field only. The comparison is exact rational arithmetic with a decimal approximation; no language model is needed. A new calculation replaces the previous anchor; changing topic or resetting clears it. Invalid changes leave the original state intact. No currency conversion or assumed currency label is performed. Other requests delegate to the existing evidence interface if configured with a local ledger. Session state is not written to disk, though terminal software may retain its own history.

This is bounded financial contextual reasoning, not general multi-turn language understanding. Unsupported follow-up wording may require restating the calculation.
