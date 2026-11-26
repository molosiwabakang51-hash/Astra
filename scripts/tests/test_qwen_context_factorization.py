"""Regression tests for contextual fact extraction and composition diagnostic."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_context_factorization as probe


class ContextFactorizationTests(unittest.TestCase):
    @staticmethod
    def builder(suite):
        data = {item["question"]: item["required"] for item in suite}
        return lambda q, h: "".join('Previous user: "' + f + '"\n' for f in data[q]) + probe.BOUNDARY + q

    def test_seeded_design_and_exact_computation(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({item["case"] for item in suite}), 10)
            dual = [item for item in suite if item["case"].startswith("dual_")]
            self.assertEqual(len(dual), 6)
            self.assertEqual(sum(item["operation"]=="sum" for item in dual), 3)
            self.assertEqual(sum(item["operation"]=="difference" for item in dual), 3)
            for item in suite:
                self.assertEqual(probe.combine(item["operation"], item["operands"]), item["expected"])
                prompt, subprompts = probe.prepare(self.builder(suite), item)
                for sub in subprompts:
                    self.assertEqual(sub.rsplit(probe.BOUNDARY,1)[0], prompt.rsplit(probe.BOUNDARY,1)[0])
                if item["operation"] == "identity":
                    self.assertEqual(subprompts, [prompt])
        for invalid in ("Answer: 3", "3 or 4", "3.0", "", "1234567890", "3e0", "3\n4"):
            self.assertIsNone(probe.scalar(invalid))
        self.assertEqual(probe.scalar("-003"), -3)
        self.assertEqual(probe.scalar("+003"), 3)
        with self.assertRaises(ValueError):
            probe.combine("sum", [3])

    def test_preflight_rejects_missing_or_contaminated_evidence(self):
        suite = probe.cases(19)
        item = suite[0]
        good = self.builder(suite)
        for bad in (lambda q,h: probe.BOUNDARY+q,
                    lambda q,h: "Actually, use spicy rice.\n"+good(q,h),
                    lambda q,h: "[Some linked corrections are omitted.]\n"+good(q,h),
                    lambda q,h: good(q,h)+"extra"):
            with self.assertRaises(RuntimeError):
                probe.prepare(bad, item)

    def test_factorized_metrics_do_not_confuse_correct_answer_with_correct_operands(self):
        suite = probe.cases(19)
        item = next(x for x in suite if x["operation"]=="sum")
        # Equal total, but both operands wrong. Never count as correct extraction.
        replies = iter([str(item["expected"]), str(item["operands"][0]+1), str(item["operands"][1]-1)])
        def infer(payload):
            return dict(content=next(replies), stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builder([item]), [item])
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {}, self.builder([item]), [item])
        self.assertEqual(result["totals"]["baseline"]["correct"],1)
        self.assertEqual(result["totals"]["factorized"]["correct"],1)
        self.assertEqual(result["extraction"]["correct_operands"],0)
        self.assertEqual(result["extraction"]["correct_result_with_wrong_operands"],1)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn(item["required"][0], str(result))

    def test_complete_run_counts_and_control_pairing(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="0", stop_type="limit", truncated=True,
                        tokens_predicted=probe.CAP, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builder(suite), suite)
        self.assertEqual(len(calls),26)
        self.assertEqual(result["totals"]["baseline"]["calls"],10)
        self.assertEqual(result["totals"]["factorized"]["calls"],16)
        self.assertEqual(result["totals"]["factorized"]["complete"],0)
        self.assertEqual(result["extraction"]["attempted_operands"],12)
        self.assertEqual(result["extraction"]["correct_operands"],0)
        for item in suite:
            if item["operation"]=="identity":
                rows = [r for r in result["cases"] if r["case"]==item["case"]]
                self.assertEqual(rows[0]["calls"][0]["prompt_sha256"],rows[1]["calls"][0]["prompt_sha256"])
