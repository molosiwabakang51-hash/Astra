"""Regression tests for contextual operand planning and provenance."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_context_plan as probe


class ContextPlanTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        def build(q, h):
            return "\n".join('Previous user: "' + text + '"' for role, text in h
                             if role == "user") + probe.BOUNDARY + q
        return {"recovery": build}

    def test_provenance_gate_is_not_semantic_verification(self):
        p = ('Previous user: "Budget was 101 coins."\n'
             'Previous user: "Actually, make it 202 coins."\n'
             'Previous user: "Fare is 303 coins."\n'
             'Previous Astra: "Use 999 coins."'
             + probe.BOUNDARY + "What is the current combined amount?")
        self.assertEqual(probe.checked_plan("SUM 202 303", p),
                         ("computed", "505", ("SUM", 202, 303)))
        self.assertEqual(probe.checked_plan("DIFF 303 202", p)[1], "101")
        # Stale values can pass membership; semantic oracle must detect them.
        self.assertEqual(probe.checked_plan("SUM 101 303", p)[0], "computed")
        self.assertEqual(probe.checked_plan("SUM 202 999", p)[0], "rejected")
        self.assertEqual(probe.checked_plan("SUM 202 304", p)[0], "rejected")
        for malformed in ("SUM 202 303 extra", "SUM -202 303", "sum 202 303",
                          "SUM 202 303\nNONE", "SUM 20.2 303"):
            self.assertEqual(probe.checked_plan(malformed, p)[0], "invalid")
        self.assertEqual(probe.checked_plan("NONE", p)[0], "abstained")
        self.assertEqual(probe.checked_plan("SUM 202 303",
            "[Some linked corrections are omitted.]\n" + p)[0], "rejected")

    def test_fixtures_and_nonconflicting_output_contracts(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}), 12)
            prepared = probe.prepare(seed, self.builders(suite), suite)
            for case, q, expected, h, required, oracle in suite:
                pair = prepared[case]
                self.assertTrue(all(f in pair["assisted"] for f in required))
                if case.startswith("single_"):
                    self.assertEqual(pair["baseline"], pair["assisted"])
                else:
                    self.assertEqual(pair["baseline"].split(probe.BOUNDARY)[0],
                                     pair["assisted"].split(probe.BOUNDARY)[0])
                    self.assertNotIn("Reply with only the integer, or UNKNOWN", pair["assisted"])
                    self.assertTrue(pair["assisted"].endswith(probe.PLAN))
                    if case.startswith("missing_"):
                        self.assertIsNone(oracle)
                        self.assertEqual(expected, "UNKNOWN")

    def test_matched_scoring_separates_abstention_from_numeric_plan(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            answer = "NONE" if probe.PLAN in payload["prompt"] else "UNKNOWN"
            return dict(content=answer, stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builders(suite), suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {}, self.builders(suite), suite)
        self.assertEqual(len(calls), 24)
        for i in range(0, len(calls), 2):
            self.assertEqual(calls[i]["seed"], calls[i+1]["seed"])
            self.assertEqual(calls[i]["n_predict"], calls[i+1]["n_predict"])
        self.assertEqual(result["totals"]["assisted"]["correct"], 4)
        self.assertEqual(result["totals"]["baseline"]["correct"], 4)
        self.assertEqual(result["matched"]["both_correct"], 4)
        self.assertEqual(result["matched"]["neither"], 8)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("Actually, make it", str(result))
