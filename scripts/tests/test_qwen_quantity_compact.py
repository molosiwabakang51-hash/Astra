"""Synthetic contract checks, not neural performance evidence."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_quantity_compact as probe


class CompactQuantityTests(unittest.TestCase):
    def test_fixture_signatures_and_strict_decode(self):
        self.assertEqual(len({c[0] for c in probe.CASES}), 12)
        self.assertEqual(sum(c[2] is None for c in probe.CASES), 4)
        for name, scenario, plan, answer in probe.CASES:
            if plan is not None:
                decoded = probe.decode(plan)
                self.assertIsNotNone(decoded, name)
                self.assertIsNotNone(probe.fidelity.signature(decoded), name)
        self.assertEqual(probe.decode("I:5:percent;A:30:bp;U"),
                         "Trace quantity: initial amount is 5 percent; increase it by 30 basis points; the current amount is unknown. What is the current amount?")
        for bad in ("NONE", "", "I:1:coin;I:2:coin", "A:2:coin", "I:1:bp",
                    "I:1:coin;A:-2:coin", "I:1:coin;S:2:pp", "I:1:coin;",
                    "I:1:coin; U", "I:1:coin\nNONE", "I:1:coin"+";U"*8,
                    "I:1:coin;A:1234567890:coin", "I:1.00001:coin", " I:1:coin"):
            self.assertIsNone(probe.decode(bad), bad)

    def test_grammar_only_pairing_and_equal_result_error(self):
        cases = (("relative_0","Scenario", "I:10:coin;A:5:coin", "15"),
                 ("unsupported_0","Ambiguous", None, None))
        calls = []
        gold = probe.decode(cases[0][2])
        def verify(q):
            return ("15", "coin", 2 if q == gold else 1)
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload, stop_type="eos", truncated=False,
                        tokens_predicted=8, content="I:15:coin" if len(calls)<=2 else "NONE")
        with patch.object(probe.native,"system_prompt",return_value="Test system"):
            report = probe.evaluate(infer, verify, cases)
        self.assertEqual(len(calls), 4)
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="grammar"},
                             {k:v for k,v in b.items() if k!="grammar"})
            self.assertNotEqual("grammar" in a, "grammar" in b)
        for arm in ("direct","grammar"):
            self.assertEqual(report["totals"][arm]["correct"],1)
            self.assertEqual(report["totals"][arm]["wrong_accepted"],1)
            self.assertEqual(report["totals"][arm]["faithful"],0)
            self.assertEqual(report["totals"][arm]["result_correct"],1)
        self.assertIsNone(report["official_ai_rating"])

    def test_truncated_and_hidden_outputs_not_admitted(self):
        cases=(("unsupported_0","Ambiguous",None,None),)
        def verify(_):
            self.fail("Incomplete or hidden response reached verifier")
        for stop,content in (("limit","I:1:coin"),("eos","<think>guess</think>I:1:coin")):
            def infer(payload):
                return dict(generation_settings=payload, stop_type=stop, truncated=False,
                            tokens_predicted=8, content=content)
            with patch.object(probe.native,"system_prompt",return_value="Test system"):
                report=probe.evaluate(infer,verify,cases)
            self.assertEqual(report["matched"]["neither"],1)

    def test_canary_fails_closed_and_metadata_is_required(self):
        with patch.object(probe.native,"system_prompt",return_value="Test system"):
            probe.canary(lambda _: dict(content="ASTRA_COMPACT_CANARY",stop_type="eos",truncated=False))
            with self.assertRaises(RuntimeError):
                probe.canary(lambda _: dict(content="NO",stop_type="eos",truncated=False))
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {}, lambda _: None, (("unsupported_0","Ambiguous",None,None),))

if __name__ == "__main__":
    unittest.main()
