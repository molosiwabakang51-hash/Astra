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
