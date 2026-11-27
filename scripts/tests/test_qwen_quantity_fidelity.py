"""Synthetic evaluation-contract tests; no neural quality claims."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_quantity_fidelity as probe


class QuantityFidelityTests(unittest.TestCase):
    def test_fixture_coverage_and_signature(self):
        self.assertEqual(len({c[0] for c in probe.CASES}), 12)
        self.assertEqual(sum(c[2] is None for c in probe.CASES), 4)
        for name, scenario, body, result in probe.CASES:
            self.assertTrue(probe.prompt(scenario, True).endswith(probe.json.dumps(scenario, ensure_ascii=False)))
            self.assertEqual(probe.prompt(scenario, True).replace(probe.CONTRAST, ""), probe.prompt(scenario, False))
            if body is not None:
                sig = probe.signature(probe.trace(body))
                self.assertIsNotNone(sig, name)
                self.assertEqual(sig[0][0], "initial")
                self.assertEqual(len(sig), len(body.split(";")))
        first = probe.trace("initial amount is 10 coins; add 5 coins to it")
        same = probe.trace("initial amount is 10 coin; raise it by 5.00 coins")
        different = probe.trace("initial amount is 20 coins; subtract 5 coins from it")
        self.assertEqual(probe.signature(first), probe.signature(same))
        self.assertNotEqual(probe.signature(first), probe.signature(different))
        self.assertIsNone(probe.signature(first + "\nNONE"))

    def test_paired_accounting_separates_same_result_and_faithful_trace(self):
        body = "initial amount is 10 coins; add 5 coins to it"
        wrong = probe.trace("initial amount is 15 coins")
        cases = (("relative_0", "First scenario", body, "15"),
                 ("unsupported_0", "Ambiguous scenario", None, None))
        calls = []
        def verify(text):
            return ("15", "coin", 2 if text == probe.trace(body) else 1)
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos", truncated=False,
                        tokens_predicted=12, content=wrong if len(calls) <= 2 else "NONE")
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            report = probe.evaluate(infer, verify, cases)
        self.assertEqual(len(calls), 4)
        for a,b in zip(calls[::2], calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="prompt"},
                             {k:v for k,v in b.items() if k!="prompt"})
        for arm in ("baseline","contrast"):
            stats = report["totals"][arm]
            self.assertEqual(stats["correct"], 1)
            self.assertEqual(stats["faithful"], 0)
            self.assertEqual(stats["result_correct"], 1)
            self.assertEqual(stats["same_result_wrong_trace"], 1)
            self.assertEqual(stats["wrong_accepted"], 1)
        self.assertEqual(report["matched"]["both"], 1)
        self.assertEqual(report["matched"]["neither"], 1)
        self.assertIsNone(report["official_ai_rating"])

    def test_truncation_and_unsupported_outputs_never_reach_verifier(self):
        cases = (("unsupported_0", "Ambiguous", None, None),)
        def verify(_):
            self.fail("Truncated output was sent to verifier")
        def infer(payload):
            return dict(generation_settings=payload, stop_type="limit", truncated=False,
                        tokens_predicted=probe.CAP,
                        content=probe.trace("initial amount is 10 coins"))
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            report = probe.evaluate(infer, verify, cases)
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {}, verify, cases)
        self.assertEqual(report["matched"]["neither"], 1)
        self.assertTrue(all(not r["accepted"] for r in report["cases"]))

    def test_fixture_preflight_and_duplicates_fail_closed(self):
        cases = (("relative_0", "Scenario", "initial amount is 10 coins", "10"),)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: self.fail("Unexpected inference"), lambda _: None, cases)
            with self.assertRaises(ValueError):
                probe.evaluate(None, None, cases * 2)


if __name__ == "__main__":
    unittest.main()
