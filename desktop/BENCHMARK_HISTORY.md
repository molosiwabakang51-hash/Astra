# Benchmark history

Use a separate local SQLite database for benchmark history. Do not reuse the knowledge ledger or specialist-performance database. After running the equal-call benchmark, save its complete JSON report. The import accepts only the twelve m01–m12 paired cases with three calls per arm, real positive durations and explicit correctness booleans. Summaries are recomputed, not trusted.

From the repository root:

```powershell
python desktop/astra_benchmark_history_cli.py record --database benchmark-history.sqlite --report benchmark.json --primary-sha PRIMARY_SHA256 --critic-sha CRITIC_SHA256 --configuration-sha CONFIG_SHA256 --approve-storage
```

Substitute actual lowercase 64-character SHA-256 digests. On Windows, Get-FileHash -Algorithm SHA256 can hash the local GGUF files; normalize the result to lowercase. The configuration identity must bind the executable, settings and benchmark protocol consistently. The importer cannot authenticate supplied identities or prove that the report came from real inference.

Use the same three digests with inspect instead of record, omitting --report and --approve-storage. Duplicate normalized reports are idempotent. Up to 20 distinct reports per configuration and 200 overall may be retained; capacity exhaustion does not overwrite prior observations.

The descriptive recommendation requires at least three distinct retained reports, each with at least two net additional correct cases, at most one regression, and cooperation latency at most 1.5 times the baseline. It never authorizes deployment. Repeating the same fixed questions is not an independent validation population, and modifying timings can create different report identities without providing new quality evidence. Do not treat the descriptive threshold as statistical significance or a general intelligence measure.

To clear the entire benchmark history:

```powershell
python desktop/astra_benchmark_history_cli.py clear --database benchmark-history.sqlite --confirm-clear
```

The history retains model/configuration identities, per-case correctness and duration only. No prompt or answer text, no automatic neural training or model switching. Database clearing does not guarantee forensic erasure. Actual benchmark results are still required from the target computer. Direct native-run-to-history integration is a separate step.
