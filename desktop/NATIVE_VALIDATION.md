# Native benchmark validation

Run from the repository root using trusted, compatible llama.cpp binaries and distinct locally supplied GGUF weights:

```powershell
python desktop/astra_native_benchmark.py "C:\llama.cpp\llama-cli.exe" "C:\Models\primary.gguf" "C:\Models\critic.gguf" --approve-models
```

The default transfer suite creates twelve new questions from four public templates (simple interest, weighted return, conditional probability, present value). The local random seed is selected after the configuration is fingerprinted; the seed is reported after evaluation for reproduction. Option order is randomized and exact answer keys are computed using rational arithmetic. Use a private seed selected after configuration freeze, and do not tune the models or prompts against the evaluation seed. Supply --seed-file with a 32-character lowercase hexadecimal seed to reproduce a run. The templates are public, so this is a synthetic transfer check rather than an independent held-out population.

Both arms receive three sequential calls per question. Total 72 calls; at a 180-second timeout native calls alone may consume up to 216 minutes. The report provides paired accuracy and family-level results, not a general intelligence rating. It does not authorize deployment.

The executable, weights, relevant Python source and runtime configuration are fingerprinted before and after the evaluation. This is not cryptographic attestation of the live process or the dynamic libraries, and resource availability can vary.

Once the separate equal-call benchmark and benchmark-history modules are integrated, direct recording is available using:

```powershell
python desktop/astra_native_benchmark.py "C:\llama.cpp\llama-cli.exe" "C:\Models\primary.gguf" "C:\Models\critic.gguf" --suite development --database benchmark-history.sqlite --approve-models --approve-storage
```

The history database is opened only after a completed evaluation and matching fingerprints. The generated transfer suite is deliberately excluded from the fixed-suite history to avoid mixing incompatible cases. Retain its report separately. No partial scores, private questions, model downloads, or automatic deployment. Real native inference remains to be tested on the target hardware.
