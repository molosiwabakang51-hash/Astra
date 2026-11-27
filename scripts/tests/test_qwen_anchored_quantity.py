"""Synthetic contracts for source-anchored reasoning; not model scores."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_anchored_quantity as probe


class AnchoredQuantityTests(unittest.TestCase):
    def test_all_eligible_fixture_operands_are_from_source(self):
        self.assertEqual(len({c[0] for c in probe.CASES}),12)
        self.assertEqual(sum(c[2] is None for c in probe.CASES),4)
        for name,clauses,plan,result in probe.CASES:
            if plan is None:
                continue
            labels=";".join(p[0] for p in plan.split(";"))
            self.assertEqual(probe.anchored_plan(labels,clauses),plan,name)

    def test_ambiguous_and_invented_operands_fail_closed(self):
        for labels,clauses in (
            ("I",("There are 3 and 5 coins.",)),
            ("I",("There are 3 coins and 5 euros.",)),
            ("I",("There are coins.",)),
            ("I",("There are 1,000 coins.",)),
            ("I",("There are 1.12345 coins.",)),
            ("I",("There are 1000000000 coins.",)),
            ("I",("There are 4 mystery unitsX.",)),
            ("I",("There are 20 basis points.",)),
            ("I;A",("There are 5 coins.",)),
            ("I;U",("There are 5 coins.","The new figure is 7 coins.")),
            ("I;A",("There are 5 coins.","The difference is -2 coins.")),
            ("I;A:9:coin",("There are 5 coins.","I receive 2 coins.")),
            ("NONE",("There are 5 coins.",)),
        ):
            self.assertIsNone(probe.anchored_plan(labels,clauses),(labels,clauses))
        self.assertEqual(probe.anchored_plan("I;D",("There are 5 coins.","I receive 2 coins.")),
                         "I:5:coin;D:2:coin")
        # Source membership deliberately does not certify operation semantics.

    def test_pairing_and_semantic_scoring_are_independent_of_source_membership(self):
        cases=(("relative_0",("There are 5 coins.","I receive 2 coins."),
                "I:5:coin;A:2:coin","7"),
               ("unsupported_0",("Two people own 3 and 5 coins.",),None,None))
        calls=[]
        def verify(q):
            return ("5" if "decrease" in q else "7","coin",2)
        def infer(payload):
            calls.append(payload)
            if len(calls)>2:
                content="NONE"
            elif payload["grammar"]==probe.LABEL_GRAMMAR:
                content="I;D"
            else:
                content="I:5:coin;A:2:coin"
            return dict(generation_settings=payload,stop_type="eos",truncated=False,
                        tokens_predicted=12,content=content)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(infer,verify,cases)
        self.assertEqual(len(calls),4)
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k not in ("prompt","grammar")},
                             {k:v for k,v in b.items() if k not in ("prompt","grammar")})
            self.assertEqual(a["prompt"].split("SOURCE EVENTS:",1)[1],
                             b["prompt"].split("SOURCE EVENTS:",1)[1])
        self.assertEqual(report["totals"]["compact"]["correct"],2)
        self.assertEqual(report["totals"]["anchored"]["correct"],1)
        self.assertEqual(report["totals"]["anchored"]["wrong_accepted"],1)
        self.assertEqual(report["matched"]["compact_only"],1)
        self.assertIsNone(report["official_ai_rating"])

    def test_truncated_hidden_or_unverified_output_is_not_accepted(self):
        cases=(("unsupported_0",("Ambiguous event.",),None,None),)
        def verify(_):
            self.fail("Incomplete output reached verifier")
        for stop,content in (("limit","I:1:coin"),("eos","<think>guess</think>I:1:coin")):
            def infer(payload):
                return dict(generation_settings=payload,stop_type=stop,truncated=False,
                            tokens_predicted=8,content=content)
            with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
                result=probe.evaluate(infer,verify,cases)
            self.assertEqual(result["matched"]["neither"],1)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {},verify,cases)

    def test_event_bounds_and_unambiguous_source_serialization(self):
        self.assertEqual(probe.source_text(("A \"quote\".",)), '1. "A \\"quote\\"."')
        for invalid in ((),("x"*181,),("x\ny",),("x",)*9):
            with self.assertRaises(ValueError):
                probe.source_text(invalid)


if __name__=="__main__":
    unittest.main()
