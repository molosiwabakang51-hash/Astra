"""Measure local CPU model throughput using an owner-provided llama-bench binary.

Only synthetic benchmark tokens are used. No chats or documents are accepted.
No downloads, network requests, model training, or general intelligence scores.
"""
import argparse
import json
import math
from pathlib import Path
import subprocess
import sys
import time

from astra_resource_plan import model_file, plan, probe

def parse_report(raw, threads):
    rows = json.loads(raw)
    if not isinstance(rows, list) or len(rows) != 2:
        raise ValueError("Expected two synthetic benchmark cases.")
    cases = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("Invalid benchmark record.")
        key = (row.get("n_prompt"), row.get("n_gen"))
        if key not in ((128, 0), (0, 32)) or key in cases:
            raise ValueError("Unexpected or duplicate benchmark case.")
        if row.get("n_threads") != threads or row.get("n_gpu_layers") != 0:
            raise ValueError("Benchmark settings did not match CPU-only request.")
        speed = row.get("avg_ts")
        if isinstance(speed, bool) or not isinstance(speed, (int, float)) or not math.isfinite(speed) or speed <= 0:
            raise ValueError("Invalid throughput measurement.")
        cases[key] = round(speed, 3)
    return {"prompt_tokens_per_second": cases[(128, 0)],
            "generation_tokens_per_second": cases[(0, 32)]}

def benchmark(binary, model, timeout=300):
    binary = Path(binary).resolve()
    model = Path(model).resolve()
    if not binary.is_file() or binary.name.lower() not in ("llama-bench", "llama-bench.exe"):
        raise ValueError("Select a trusted local llama-bench executable.")
    if not 30 <= timeout <= 900:
        raise ValueError("Timeout must be 30–900 seconds.")
    name, size = model_file(model)
    admission = plan(probe(model.parent), [(name, size)])
    if not admission["all_admitted_individually"]:
        raise RuntimeError("Insufficient available RAM; model not launched.")
    threads = admission["threads"]
    command = [str(binary), "-m", str(model), "-p", "128", "-n", "32",
               "-t", str(threads), "-ngl", "0", "-r", "2", "-o", "json"]
    start = time.monotonic()
    try:
        completed = subprocess.run(command, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                   timeout=timeout, check=False, shell=False)
    except subprocess.TimeoutExpired:
        raise RuntimeError("Synthetic benchmark timed out; no score.") from None
    if completed.returncode != 0 or len(completed.stdout) > 1_000_000:
        raise RuntimeError("Synthetic benchmark failed; no score.")
    result = parse_report(completed.stdout, threads)
    return {"model": name, "threads": threads, **result,
            "process_wall_seconds": round(time.monotonic() - start, 3),
            "available_ram_before_mib": admission["available_ram_mib"],
            "note": "Synthetic throughput only; no accuracy or peak RAM measurement."}

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary")
    parser.add_argument("models", nargs="+")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args(argv)
    results = []
    failures = 0
    for model in args.models:
        try:
            results.append(benchmark(args.binary, model, args.timeout))
        except (OSError, ValueError, RuntimeError):
            # Do not export private paths from subprocess/provider exceptions.
            results.append({"model": Path(model).name, "status": "unverified"})
            failures += 1
    print(json.dumps(results, indent=2))
    return 1 if failures else 0

if __name__ == "__main__":
    sys.exit(main())
