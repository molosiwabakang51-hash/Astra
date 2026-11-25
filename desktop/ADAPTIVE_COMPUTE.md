# Adaptive compute pilot

Use a reviewed local evidence database and trusted compatible local llama.cpp executable with a primary GGUF. From the repository root:

```powershell
python desktop/astra_adaptive_answer.py --ledger evidence.sqlite --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf" --approve-models --mode fast --budget 90
```

Type one question per line; enter exit to stop. Fast mode allows at most one model call. Balanced mode allows one additional advisory critic, with a distinct local model supplied through --critic. The critic is skipped when the remaining budget cannot accommodate its estimated cost.

The general budget wrapper also supports a four-call deep policy for future integration with recursive revision. This CLI deliberately exposes only fast and balanced. A per-request budget is 1–900 seconds; native calls never exceed the configured per-call timeout. RAM admission remains enforced. The latency profile retains only the latest 12 observations per role in memory, estimating the 90th percentile plus a 25% margin. Until measured, it assumes 30 seconds. These thresholds are engineering heuristics, not measured target-hardware performance or proof of answer quality.

The time budget is not a hard operating-system deadline. Process startup, cleanup and scheduling can exceed it, but late model results are discarded. No prompts, generated answers or latency samples are saved by the budget wrapper. The terminal and native temporary files may retain information. No autonomous model replacement, training, or automatic knowledge writes are performed.

Actual native runtime compatibility and inference quality must be tested on the target computer. The regression suite uses synthetic timings and mocked subprocess calls.
