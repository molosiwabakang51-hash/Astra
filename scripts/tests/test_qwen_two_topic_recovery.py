"""Synthetic checks for two-topic compiled recovery diagnostic."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_two_topic_recovery as probe


class TwoTopicRecoveryTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        data = {q: (case, required) for case, q, _, _, required in suite}
        def build(q, h, candidate):
            case, required = data[q]
            included = required if candidate or case.startswith("single_") else required[:1]
            return "\n".join(included) + "\n" + q
        return {"baseline": lambda q, h: build(q, h, False),
                "recovery": lambda q, h: build(q, h, True)}

    def test_case_balance_and_reproducibility(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}), 8)
            for domain in ("dual_", "single_"):
                indices = [i for i, r in enumerate(suite) if r[0].startswith(domain)]
                self.assertEqual(len(indices), 4)
                self.assertEqual(sum(i % 2 == 0 for i in indices), 2)
            for _, q, _, history, required in suite:
                self.assertTrue(q.endswith("Reply with only the integer."))
                for fragment in required:
                    self.assertIn(("user", fragment), history)

    def test_preflight_rejects_missing_corrections_and_changed_controls(self):
        suite = probe.cases(19)
        good = self.builders(suite)
        self.assertEqual(len(probe.prepare(19, good, suite)), 8)
        for bad in (
            {"baseline": good["baseline"], "recovery": good["baseline"]},
            {"baseline": good["recovery"], "recovery": good["recovery"]},
            {"baseline": good["baseline"],
             "recovery": lambda q, h: "Actually, use spicy rice.\n" + good["recovery"](q,h)},
            {"baseline": good["baseline"],
             "recovery": lambda q, h: "Changed control.\n" + good["recovery"](q,h)},
        ):
            with self.assertRaises(RuntimeError):
                probe.prepare(19, bad, suite)

    def test_matched_native_metrics_and_no_raw_history_export(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="0", stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builders(suite), suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {}, self.builders(suite), suite)
        self.assertEqual(len(calls), 16)
        for i in range(0, len(calls), 2):
            self.assertEqual(calls[i]["seed"], calls[i+1]["seed"])
            self.assertEqual(calls[i]["n_predict"], calls[i+1]["n_predict"])
        self.assertEqual(result["matched"]["neither"], 8)
        self.assertEqual(result["totals"]["recovery"]["complete"], 8)
        self.assertTrue(all(row["required_retained"] == row["required_count"]
                            for row in result["cases"] if row["arm"] == "recovery"))
        self.assertNotIn("Actually, make it", str(result))
        self.assertIsNone(result["official_ai_rating"])
