# Adaptive compute pilot

Use a reviewed local evidence database and trusted compatible local llama.cpp executable with a primary GGUF. From the repository root:

```powershell
python desktop/astra_adaptive_answer.py --ledger evidence.sqlite --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf" --approve-models --mode fast --budget 90
```

Type one question per line; enter exit to stop. Fast mode allows at most one model call. Balanced mode allows one additional advisory critic, with a distinct local model supplied through --critic. The critic is skipped when the remaining budget cannot accommodate its estimated cost.

Deep mode (--mode deep) connects the four-call budget to recursive critique, revision and comparative evaluation. Supply --critic. Valid drafts are retained if an optional call exceeds the budget, provided supporting evidence remains active. A reviewer's preference is advisory. A per-request budget is 1–900 seconds; native calls never exceed the configured per-call timeout. RAM admission remains enforced. The latency profile retains only the latest 12 observations per role in memory, estimating the 90th percentile plus a 25% margin. Until measured, it assumes 30 seconds. These thresholds are engineering heuristics, not measured target-hardware performance or proof of answer quality.

The time budget is not a hard operating-system deadline. Process startup, cleanup and scheduling can exceed it, but late model results are discarded. No prompts, generated answers or latency samples are saved by the budget wrapper. The terminal and native temporary files may retain information. No autonomous model replacement, training, or automatic knowledge writes are performed.

Actual native runtime compatibility and inference quality must be tested on the target computer. The regression suite uses synthetic timings and mocked subprocess calls.

## Equal-call reasoning comparison

```powershell
python desktop/astra_equal_call_eval.py "C:\llama.cpp\llama-cli.exe" "C:\Models\primary.gguf" "C:\Models\critic.gguf" --approve-models
```

The benchmark compares three primary-model calls against two primary calls and one critic call on each of twelve fixed synthetic development questions. Both arms adjudicate two candidate letters. Ground-truth scoring occurs outside generation, and arm order alternates. It reports paired wins/losses, correctness and elapsed time. It requires 72 model calls; at the default 180-second per-call timeout, native calls alone could take up to 216 minutes. Equal calls do not ensure equal tokens, FLOPs or latency. The fixed questions are not a held-out sample and no benchmark has yet been run on actual weights. Do not infer general intelligence or statistical significance from these results.
