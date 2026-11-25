# Verified finance pilot

From the repository root (after the dependent branches are integrated):

```powershell
python desktop/astra_local_finance.py desktop/examples/local_finance.json
```

No model or native executable is needed for the deterministic calculation. The example discounts 121 at 10% over two years; the exact result is 100.

Optional independent model proposals on a machine with trusted local llama.cpp and two distinct, licensed GGUF files:

```powershell
python desktop/astra_local_finance.py desktop/examples/local_finance.json --allow-model --binary "C:\llama.cpp\llama-cli.exe" --primary "C:\Models\primary.gguf" --critic "C:\Models\critic.gguf" --timeout 180
```

The runner rechecks available RAM before each launch. Models run sequentially with CPU-only settings, a 2048-token context, a 48-token generation limit and a per-call timeout. It requests offline mode and strips model-specific environment overrides. Use only a trusted current binary: https://github.com/ggml-org/llama.cpp/blob/master/tools/cli/README.md. This is not an OS sandbox. Incompatibility, malformed output or insufficient RAM is reported as an unavailable proposal, while exact arithmetic remains authoritative.

Prompts are passed through temporary files rather than command-line text. They are removed on ordinary completion, including handled timeouts; crash remnants, swap or SSD copies cannot be ruled out. Do not use confidential material in this experimental runner. No automatic persistence, uploads, downloads or model training is included.

The proposal grammar constrains numeric responses, and output is validated after the subprocess returns. This is not a hard OS-level memory cap on subprocess output. Physical-device RAM, latency, model compatibility and actual answer quality are unverified. No commercial deployment is approved by these tests.
