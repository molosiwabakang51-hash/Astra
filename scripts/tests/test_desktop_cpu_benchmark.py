"""Synthetic tests; never require model weights or a native executable."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_cpu_benchmark"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
B = sys.modules["astra_cpu_benchmark"]
P = sys.modules["astra_resource_plan"]

def rows():
    return [
        {"n_prompt": 128, "n_gen": 0, "n_threads": 2, "n_gpu_layers": 0, "avg_ts": 20.0},
        {"n_prompt": 0, "n_gen": 32, "n_threads": 2, "n_gpu_layers": 0, "avg_ts": 4.0},
    ]

class DesktopBenchmarkTests(unittest.TestCase):
    def test_accepts_only_requested_synthetic_cpu_cases(self):
        result = B.parse_report(json.dumps(rows()), 2)
        self.assertEqual(20.0, result["prompt_tokens_per_second"])
        self.assertEqual(4.0, result["generation_tokens_per_second"])
        for field, value in (("n_threads", 8), ("n_gpu_layers", 1), ("avg_ts", 0),
                             ("avg_ts", float("nan")), ("avg_ts", True)):
            bad = rows()
            bad[0][field] = value
            with self.assertRaises(ValueError):
                B.parse_report(json.dumps(bad), 2)
        with self.assertRaises(ValueError):
            B.parse_report(json.dumps([rows()[0], rows()[0]]), 2)

    def test_refused_admission_does_not_launch_process(self):
        with tempfile.TemporaryDirectory() as d:
            binary = Path(d) / "llama-bench.exe"
            binary.touch()
            model = Path(d) / "model.gguf"
            model.write_bytes(b"GGUF" + b"x" * 1024)
            with patch.object(B, "probe", return_value=P.Resources(P.GIB, 4, P.GIB)), patch.object(B.subprocess, "run") as run:
                with self.assertRaises(RuntimeError):
                    B.benchmark(binary, model)
                run.assert_not_called()

    def test_local_execution_is_bounded_and_cpu_only(self):
        with tempfile.TemporaryDirectory() as d:
            binary = Path(d) / "llama-bench.exe"
            binary.touch()
            model = Path(d) / "model.gguf"
            model.write_bytes(b"GGUF" + b"x" * 1024)
            with patch.object(B, "probe", return_value=P.Resources(5 * P.GIB, 4, P.GIB)), patch.object(B.subprocess, "run") as run:
                run.return_value = subprocess.CompletedProcess([], 0, json.dumps(rows()).encode())
                result = B.benchmark(binary, model, timeout=60)
                args, kwargs = run.call_args
                self.assertEqual("0", args[0][args[0].index("-ngl") + 1])
                self.assertEqual(60, kwargs["timeout"])
                self.assertFalse(kwargs["shell"])
                self.assertEqual("model.gguf", result["model"])
                self.assertNotIn(d, json.dumps(result))
                run.side_effect = subprocess.TimeoutExpired("synthetic", 60)
                with self.assertRaises(RuntimeError):
                    B.benchmark(binary, model, timeout=60)

if __name__ == "__main__":
    unittest.main()
