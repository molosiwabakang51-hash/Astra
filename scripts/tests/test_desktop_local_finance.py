"""Test local finance integration without real weights or native binaries."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_quantitative_verify",
             "astra_verified_finance", "astra_local_finance"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
L = sys.modules["astra_local_finance"]
P = sys.modules["astra_resource_plan"]


def request():
    return {"operation": "weighted_return",
            "inputs": {"weights": ["0.3", "0.7"], "returns": ["0.10", "0.05"]}}


class LocalFinanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.binary = root / "llama-cli.exe"
        self.binary.touch()
        self.primary = root / "primary.gguf"
        self.critic = root / "critic.gguf"
        self.primary.write_bytes(b"GGUF" + b"x" * 1024)
        self.critic.write_bytes(b"GGUF" + b"y" * 1024)
        self.paths = {"binary": self.binary, "primary": self.primary, "critic": self.critic}

    def test_deterministic_default_never_starts_native_runner(self):
        with patch.object(L,"run_bounded") as run:
            result = L.run_request(request())
            self.assertEqual("0.065", result["result"]["decimal_approximation"])
            self.assertEqual("no_model_proposals", result["proposal_agreement"])
            with self.assertRaises(ValueError):
                L.run_request(request(), **self.paths)
            run.assert_not_called()

    def test_distinct_weights_required(self):
        self.critic.write_bytes(self.primary.read_bytes())
        with self.assertRaises(ValueError):
            L.LocalProposer(**self.paths)
        with patch.object(L,"run_bounded") as run:
            result = L.run_request(request(), allow_model=True, **self.paths)
            self.assertEqual({"primary": "unavailable", "critic": "unavailable"},
                             result["proposal_assessment"])
            run.assert_not_called()

    def test_output_is_strict_and_bounded(self):
        self.assertEqual("-0.065", L.parse_proposal(b"banner\nFINAL: -0.065\n"))
        for raw in (b"0.065", b"FINAL: 6.5%", b"FINAL: 1e3",
                    b"FINAL: 1\nFINAL: 2", b"FINAL: 1\nFINAL: invalid",
                    b"<think>\nFINAL: 1\n</think>", b"FINAL: NaN",
                    b"x" * (L.MAX_OUTPUT_BYTES + 1), b"\xff"):
            with self.assertRaises(ValueError):
                L.parse_proposal(raw)

    def test_low_ram_refuses_each_native_call(self):
        with patch.object(L, "probe", return_value=P.Resources(P.GIB, 4, P.GIB)), \
             patch.object(L,"run_bounded") as run:
            result = L.run_request(request(), allow_model=True, **self.paths)
            run.assert_not_called()
            self.assertEqual("0.065", result["result"]["decimal_approximation"])
            self.assertEqual({"primary": "unavailable", "critic": "unavailable"},
                             result["proposal_assessment"])

    def test_sequential_native_calls_hide_answer_and_remove_prompt(self):
        prompts, files, roles = [], [], []

        def execute(args, **kwargs):
            model = Path(args[args.index("-m") + 1])
            prompt = Path(args[args.index("-f") + 1])
            roles.append(model.name)
            files.append(prompt)
            prompts.append(prompt.read_text(encoding="utf-8"))
            self.assertNotIn("0.065", prompts[-1])
            self.assertNotIn("weighted_return", " ".join(args))
            self.assertEqual("0", args[args.index("-ngl") + 1])
            self.assertEqual("0", args[args.index("--cache-ram") + 1])
            self.assertEqual("2", args[args.index("-tb") + 1])
            self.assertIn("--offline", args)
            self.assertIn("--grammar", args)
            self.assertEqual(L.MAX_OUTPUT_BYTES,kwargs["max_stdout_bytes"])
            self.assertEqual(60, kwargs["timeout"])
            self.assertNotIn("LLAMA_ARG_HF_REPO", kwargs["env"])
            self.assertNotIn("HF_TOKEN", kwargs["env"])
            if len(files) == 2:
                self.assertFalse(files[0].exists())
            output = b"FINAL: 0.07\n" if model == self.primary else b"FINAL: 0.065\n"
            return subprocess.CompletedProcess(args, 0, output)

        with patch.object(L, "probe", return_value=P.Resources(5 * P.GIB, 4, P.GIB)) as probe, \
             patch.object(L,"run_bounded", side_effect=execute), \
             patch.dict(os.environ, {"LLAMA_ARG_HF_REPO": "remote", "HF_TOKEN": "test"}):
            result = L.run_request(request(), allow_model=True, timeout=60, **self.paths)
        self.assertEqual(["primary.gguf", "critic.gguf"], roles)
        self.assertEqual(prompts[0], prompts[1])
        self.assertEqual(2, probe.call_count)
        self.assertTrue(all(not x.exists() for x in files))
        self.assertEqual({"primary": "mismatch", "critic": "matches"},
                         result["proposal_assessment"])

    def test_timeout_and_nonzero_exit_preserve_exact_result(self):
        files = []

        def execute(args, **kwargs):
            files.append(Path(args[args.index("-f") + 1]))
            if len(files) == 1:
                raise subprocess.TimeoutExpired(args, kwargs["timeout"])
            return subprocess.CompletedProcess(args, 1, b"FINAL: 0.065\n")

        with patch.object(L, "probe", return_value=P.Resources(5 * P.GIB, 4, P.GIB)), \
             patch.object(L,"run_bounded", side_effect=execute):
            result = L.run_request(request(), allow_model=True, **self.paths)
        self.assertTrue(all(not x.exists() for x in files))
        self.assertEqual("0.065", result["result"]["decimal_approximation"])
        self.assertEqual({"primary": "unavailable", "critic": "unavailable"},
                         result["proposal_assessment"])

    def test_malformed_or_oversized_request_never_executes(self):
        with patch.object(L,"run_bounded") as run:
            with self.assertRaises(ValueError):
                L.run_request({"operation": "eval", "inputs": {}}, allow_model=True, **self.paths)
            with self.assertRaises(ValueError):
                L.run_request(dict(request(), candidates={"primary": "0.065"}), allow_model=True, **self.paths)
            runner = L.LocalProposer(**self.paths)
            with self.assertRaises(ValueError):
                runner("primary", "x" * L.MAX_PROMPT_BYTES)
            run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
