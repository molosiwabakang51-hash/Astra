import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_program_state as probe


class ProgramStateTests(unittest.TestCase):
    def test_cases_deterministic_unique_and_balanced(self):
        for seed in range(50):
            rows = probe.cases(seed)
            self.assertEqual(rows, probe.cases(seed))
            self.assertEqual(len({row[0] for row in rows}), 12)
            self.assertEqual(sum(row[0].startswith("trace_") for row in rows), 8)
            answers = dict((case, expected) for case, _, expected in rows)
            self.assertEqual(answers["trace_queue"], answers["control_arithmetic"])
            self.assertNotEqual(answers["trace_sequential"], answers["trace_simultaneous"])

    def test_matched_seeds_controls_and_strict_scoring(self):
        suite = [("trace_example", "Start at 2 and add 3.", "5"),
                 ("control_example", "Return YES.", "YES")]
        calls = []
        def fake(payload):
            calls.append(payload)
            answer = "5" if "Start at 2" in payload["prompt"] else "YES"
            if probe.HINT in payload["prompt"]:
                answer = "5."
            return dict(content="<think>private</think>" + answer, stop_type="eos",
                        truncated=False, tokens_predicted=8, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Test system"):
            report = probe.evaluate(10, fake, suite)
        self.assertEqual(len(calls), 4)
        self.assertEqual(calls[0]["seed"], calls[1]["seed"])
        self.assertEqual(calls[2], calls[3])
        self.assertIn(probe.HINT, calls[1]["prompt"])
        self.assertNotIn(probe.HINT, calls[0]["prompt"])
        self.assertEqual(report["matched"]["baseline_only"], 1)
        self.assertEqual(report["matched"]["both_correct"], 1)
        self.assertIsNone(report["official_ai_rating"])
        self.assertNotIn("private", str(report))

    def test_fail_closed_native_settings_and_duplicate_cases(self):
        suite = [("trace_example", "Question", "5")]
        with patch.object(probe.native, "system_prompt", return_value="Test system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(10, lambda _: {}, suite)
            with self.assertRaises(ValueError):
                probe.evaluate(10, lambda _: {}, suite * 2)

    def test_incomplete_answer_not_counted(self):
        def fake(payload):
            return dict(content="<think>private</think>5", stop_type="limit",
                        truncated=False, tokens_predicted=probe.CAP,
                        generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Test system"):
            report = probe.evaluate(10, fake, [("trace_example", "Question", "5")])
        self.assertEqual(report["matched"]["neither"], 1)
        self.assertEqual(report["totals"]["trace"]["complete"], 0)


if __name__ == "__main__":
    unittest.main()
