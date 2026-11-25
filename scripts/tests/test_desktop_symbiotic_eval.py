"""Mocked cooperation trials; no model weights or network required."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import subprocess

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_symbiotic_eval"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
S = sys.modules["astra_symbiotic_eval"]
P = sys.modules["astra_resource_plan"]

class SymbioticEvaluationTests(unittest.TestCase):
    def test_unambiguous_marker_only(self):
        self.assertEqual("B", S.parse_answer("banner\nFINAL: B\n"))
        for raw in ("", "B", "FINAL: B\nFINAL: C", "FINAL: B or C",
                    "<think>\nFINAL: B\n</think>", "FINAL: B\nFINAL: invalid",
                    "x" * 65537):
            self.assertIsNone(S.parse_answer(raw))

    def test_agreement_does_not_spend_third_model_call(self):
        prompts = []
        def run(role, prompt):
            prompts.append((role, prompt))
            return "B"
        rows = S.evaluate(run, S.QUESTIONS[:1])
        self.assertEqual(2, len(prompts))
        self.assertEqual(prompts[0][1], prompts[1][1])
        self.assertTrue(rows[0]["agreement"])
        self.assertFalse(rows[0]["combined_correct"])

    def test_disagreement_can_correct_or_worsen_primary(self):
        for candidates, expected in ((["B", "A", "A"], True), (["A", "B", "B"], False)):
            calls = []
            def run(role, prompt):
                calls.append((role, prompt))
                return candidates[len(calls)-1]
            result = S.evaluate(run, S.QUESTIONS[:1])[0]
            self.assertEqual(["primary", "critic", "primary"], [c[0] for c in calls])
            self.assertEqual(calls[0][1], calls[1][1])
            self.assertIn("Two independent candidate options", calls[2][1])
            self.assertNotIn("correct answer", calls[2][1])
            self.assertEqual(expected, result["combined_correct"])
            self.assertEqual(3, result["model_calls"])

    def test_ground_truth_does_not_change_model_prompts(self):
        question = S.QUESTIONS[0]
        traces = []
        for expected in ("A", "D"):
            calls = []
            def run(role, prompt):
                calls.append((role, prompt))
                return ("B", "C", "D")[len(calls)-1]
            S.evaluate(run, [S.Question(question.id, question.text, expected)])
            traces.append(calls)
        self.assertEqual(traces[0], traces[1])

    def test_invalid_candidate_and_partial_suite_fail_closed(self):
        with self.assertRaises(RuntimeError):
            S.evaluate(lambda role, prompt: "invalid", S.QUESTIONS[:1])
        with self.assertRaises(ValueError):
            S.report(S.evaluate(lambda role, prompt: "A", S.QUESTIONS[:1]))
        rows = S.evaluate(lambda role, prompt: "A")
        report = S.report(rows)
        self.assertEqual(6, report["questions"])
        self.assertEqual(12, report["model_calls"])
        self.assertEqual(2, report["combined_correct"])

    def test_resource_gate_and_runtime_flags(self):
        with tempfile.TemporaryDirectory() as d:
            model = Path(d) / "model.gguf"
            model.write_bytes(b"GGUF" + b"x" * 1024)
            binary = Path(d) / "llama-cli.exe"
            with patch.object(S, "probe", return_value=P.Resources(P.GIB, 4, P.GIB)), patch.object(S.subprocess, "run") as run:
                with self.assertRaises(RuntimeError):
                    S.generate(binary, model, "synthetic", 30)
                run.assert_not_called()
            with patch.object(S, "probe", return_value=P.Resources(5 * P.GIB, 4, P.GIB)), patch.object(S.subprocess, "run") as run:
                run.return_value = subprocess.CompletedProcess([], 0, b"FINAL: A\n")
                with patch.dict(S.os.environ, {"LLAMA_ARG_MODEL": "unexpected", "LLAMA_ARG_N_GPU_LAYERS": "9"}):
                    self.assertEqual("A", S.generate(binary, model, "synthetic", 30))
                args, kwargs = run.call_args
                self.assertFalse(kwargs["shell"])
                self.assertEqual(30, kwargs["timeout"])
                self.assertNotIn("LLAMA_ARG_MODEL", kwargs["env"])
                self.assertEqual("0", args[0][args[0].index("-ngl")+1])
                self.assertEqual("0", args[0][args[0].index("--cache-ram")+1])
                run.return_value = subprocess.CompletedProcess([], 1, b"FINAL: A")
                with self.assertRaises(RuntimeError):
                    S.generate(binary, model, "synthetic", 30)

    def test_identical_file_contents_are_detected(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = Path(d) / "a.gguf", Path(d) / "b.gguf"
            a.write_bytes(b"GGUF" + b"x" * 1024)
            b.write_bytes(a.read_bytes())
            self.assertEqual(S.identity(a), S.identity(b))
            b.write_bytes(b"GGUF" + b"y" * 1024)
            self.assertNotEqual(S.identity(a), S.identity(b))

if __name__ == "__main__":
    unittest.main()
