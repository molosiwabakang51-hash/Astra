"""Regression checks for equal-call scoped fact extraction."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_scoped_factorization as probe


class ScopedFactorizationTests(unittest.TestCase):
    @staticmethod
    def builder(suite, shared_only=False):
        data = {}
        for item in suite:
            data[item["question"]] = item["required"]
            for j, q in enumerate(item["subquestions"]):
                data[q] = item["required"] if shared_only else (item["required"][j],)
        return lambda q, h: "".join('Previous user: "' + f + '"\n' for f in data[q]) + probe.BOUNDARY + q

    def test_seeded_case_balance_and_context_selection(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({item["case"] for item in suite}), 10)
            dual = [item for item in suite if item["case"].startswith("dual_")]
            self.assertEqual(len(dual), 6)
            self.assertEqual(sum(item["operation"]=="sum" for item in dual), 3)
            for item in suite:
                pair = probe.prepare(self.builder(suite), item)
                self.assertEqual(len(pair["shared"]), len(pair["scoped"]))
                for j, q in enumerate(item["subquestions"]):
                    self.assertTrue(pair["scoped"][j].endswith(q))
                    self.assertIn(item["required"][j], pair["scoped"][j])
                    if len(item["required"]) == 2:
                        self.assertNotIn(item["required"][1-j], pair["scoped"][j])
                        self.assertIn(item["required"][1-j], pair["shared"][j])
                if item["operation"] == "identity":
                    self.assertEqual(pair["shared"], pair["scoped"])

    def test_preflight_rejects_uninformative_and_incomplete_fixtures(self):
        suite = probe.cases(19)
        good = self.builder(suite)
        for bad in (lambda q,h: probe.BOUNDARY+q,
                    lambda q,h: "Actually, use spicy rice.\n"+good(q,h),
                    lambda q,h: "[Some linked corrections are omitted.]\n"+good(q,h)):
            with self.assertRaises(RuntimeError):
                probe.prepare(bad, suite[0])
        with self.assertRaises(RuntimeError):
            probe.evaluate(19, lambda _: self.fail("No inference expected"),
                           self.builder(suite, shared_only=True), suite)

    def test_equal_call_counts_paired_seeds_and_no_incomplete_credit(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="0", stop_type="limit", truncated=True,
                        tokens_predicted=probe.CAP, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builder(suite), suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {}, self.builder(suite), suite)
        self.assertEqual(len(calls),32)
        offset = 0
        for item in suite:
            n = len(item["operands"])
            for j in range(n):
                self.assertEqual(calls[offset+j]["seed"],calls[offset+n+j]["seed"])
                self.assertEqual(calls[offset+j]["n_predict"],calls[offset+n+j]["n_predict"])
            offset += 2*n
        for arm in ("shared","scoped"):
            self.assertEqual(result["totals"][arm]["calls"],16)
            self.assertEqual(result["totals"][arm]["correct"],0)
            self.assertEqual(result["extraction"][arm]["correct_operands"],0)
        self.assertEqual(result["extraction"]["shared"]["other_correction_retained"],12)
        self.assertEqual(result["extraction"]["scoped"]["other_correction_retained"],0)
        self.assertIsNone(result["official_ai_rating"])

    def test_wrong_operands_with_correct_sum_are_reported_separately(self):
        item = probe.cases(19)[0]
        a,b = item["operands"]
        self.assertEqual(item["operation"],"sum")
        replies = iter([str(a+1),str(b-1),str(a),str(b)])
        def infer(payload):
            return dict(content=next(replies), stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builder([item]), [item])
        self.assertEqual(result["extraction"]["shared"]["correct_result_with_wrong_operands"],1)
        self.assertEqual(result["extraction"]["scoped"]["both_correct"],1)
        self.assertNotIn(item["required"][0],str(result))
