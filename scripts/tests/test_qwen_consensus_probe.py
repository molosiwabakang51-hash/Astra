import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_consensus_probe as probe


class ConsensusProbeTests(unittest.TestCase):
    def test_suite_has_four_balanced_domains_and_reproducible_seed(self):
        suite = probe.cases(42)
        self.assertEqual(suite, probe.cases(42))
        self.assertNotEqual(suite, probe.cases(43))
        self.assertEqual(len({name for name, _, _ in suite}), 12)
        for domain in ("logic", "quantity", "context", "strategy"):
            self.assertEqual(sum(n.startswith(domain + "_") for n, _, _ in suite), 3)

    def test_comparison_measures_gains_and_losses_without_answer_key(self):
        suite = [("logic_gain", "Question A", "YES"),
                 ("logic_loss", "Question B", "NO")]
        responses = iter(["NO", "YES", "YES", "NO", "YES", "YES"])
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos", truncated=False,
                        tokens_predicted=5, content=next(responses))
        def select(question, attempts):
            self.assertIn(question, ("Question A", "Question B"))
            self.assertEqual(len(attempts), 3)
            self.assertTrue(all(set(a) == {"visible", "complete"} for a in attempts))
            return 1
        result = probe.evaluate(42, infer, select, suite)
        self.assertEqual(len(calls), 6)
        self.assertEqual(len({c["seed"] for c in calls}), 6)
        for start in (0, 3):
            pair = calls[start:start+3]
            self.assertTrue(all({k:v for k,v in pair[0].items() if k != "seed"} ==
                                {k:v for k,v in p.items() if k != "seed"} for p in pair))
        self.assertEqual(result["totals"]["baseline_correct"], 1)
        self.assertEqual(result["totals"]["candidate_correct"], 1)
        self.assertEqual(result["totals"]["candidate_only"], 1)
        self.assertEqual(result["totals"]["baseline_only"], 1)
        self.assertEqual(result["totals"]["candidate_tokens"], 30)
        self.assertEqual(result["totals"]["baseline_tokens"], 10)
        self.assertIsNone(result["official_ai_rating"])

    def test_incomplete_response_does_not_earn_credit(self):
        def infer(payload):
            return dict(generation_settings=payload, stop_type="limit", truncated=True,
                        tokens_predicted=768, content="YES")
        def select(question, attempts):
            self.assertTrue(all(not a["complete"] for a in attempts))
            return 0
        result = probe.evaluate(42, infer, select, [("logic_incomplete", "Question", "YES")])
        self.assertEqual(result["totals"]["baseline_correct"], 0)
        self.assertEqual(result["totals"]["candidate_correct"], 0)
        self.assertEqual(result["totals"]["complete_calls"], 0)


if __name__ == "__main__":
    unittest.main()
