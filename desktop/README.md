# Astra Desktop pilot

The first stage is a conservative CPU-only resource planner and synthetic performance benchmark. It does not yet implement collaborative model inference or Android synchronisation.

## Requirements

- Python 3.10 or later.
- Local GGUF files obtained from trusted publishers, with verified integrity and appropriate licenses.
- For the benchmark only, a trusted local llama.cpp CPU-compatible `llama-bench.exe` (Windows) or `llama-bench` (Linux). No executable or weights are downloaded automatically.

From the repository root on Windows PowerShell:

```powershell
python desktop/astra_resource_plan.py "C:\Models\primary.gguf" "C:\Models\critic.gguf"
python desktop/astra_cpu_benchmark.py "C:\llama.cpp\llama-bench.exe" "C:\Models\primary.gguf" "C:\Models\critic.gguf"
```

Replace paths with files already installed. The planner checks GGUF magic bytes, available physical memory, a 1.5 GiB reserve, and an estimated allocation. Header checks are not cryptographic verification. Run benchmarks only with trusted weights and binaries. The two CPU threads are a conservative starting point, not a proven optimum. The pilot does not execute two models simultaneously. Close other heavy applications; stop if the device becomes unstable.

The benchmark rechecks admission per model and reports synthetic prompt-processing and generation throughput. It does not evaluate reasoning, measure peak RAM, predict conversational latency, or certify hardware suitability. A failed or timed-out trial is not a score. Benchmark flags and JSON schema follow https://github.com/ggml-org/llama.cpp/blob/master/tools/llama-bench/README.md.

## Next gates

1. Collect actual Windows measurements from the existing i5-6300U and 8 GB machine.
2. Add bounded local model orchestration and a held-out reasoning evaluation; compare against the best single-model baseline.
3. Add independent evidence retrieval and deterministic verification.
4. Adopt specialist models only where measured accuracy gains justify latency and memory costs.
5. Validate commercial use cases and applicable model licenses before paid deployment.

No paid dependencies, automatic network transfers, or hardware upgrades are required for this pilot.
