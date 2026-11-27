"""Synthetic evaluator checks, not neural performance evidence."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_causal_worlds as probe


class CausalWorldTests(unittest.TestCase):
    def test_reference_outcomes_and_domain_coverage(self):
        self.assertEqual(len({c[0] for c in probe.CASES}),16)
        for domain in ("forward","abduction","intervention","counterfactual"):
            cases=[c for c in probe.CASES if c[0].startswith(domain+"_")]
            self.assertEqual(len(cases),4)
            self.assertEqual({c[-1] for c in cases},{"YES","NO","UNKNOWN"})
        for case in probe.CASES:
            self.assertEqual(probe.oracle(case),case[-1],case[0])

    def test_intervention_does_not_condition_on_new_value(self):
        case=("test","AND","AND",((1,1),),((2,1),),0,"UNKNOWN")
        self.assertEqual(probe.oracle(case),"UNKNOWN")
        # Old observations are applied to the original world, not the changed world.
        case=("test","XOR","AND",((2,1),(1,1)),((1,0),),2,"NO")
        self.assertEqual(probe.oracle(case),"NO")
        self.assertEqual(probe.world(0,1,"AND","AND",{2:1}),(0,1,1,1))

    def test_impossible_or_invalid_evidence_fails_closed(self):
        for case in (
            ("bad","AND","OR",((0,0),(2,1)),(),3,"UNKNOWN"),
            ("bad","AND","OR",((0,0),(0,1)),(),3,"UNKNOWN"),
            ("bad","AND","OR",(),((2,2),),3,"UNKNOWN"),
            ("bad","AND","OR",(),(),4,"UNKNOWN"),
        ):
            with self.assertRaises(ValueError):
                probe.oracle(case)
        with self.assertRaises(RuntimeError):
            probe.evaluate(lambda _:self.fail("Inference before fixture preflight"),
                           (probe.CASES[0][:-1]+("YES",),))

    def test_paired_settings_and_calibrated_uncertainty(self):
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="UNKNOWN",stop_type="eos",truncated=False,
                        tokens_predicted=10,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(infer)
        self.assertEqual(len(calls),32)
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="prompt"},
                             {k:v for k,v in b.items() if k!="prompt"})
            self.assertEqual(a["prompt"].replace(probe.GUIDANCE,""),
                             b["prompt"].replace(probe.GUIDANCE,""))
        for arm in ("baseline","causal"):
            self.assertEqual(report["totals"][arm]["correct"],5)
            self.assertEqual(report["totals"][arm]["false_abstention"],11)
            self.assertEqual(report["totals"][arm]["false_certainty"],0)
        self.assertIsNone(report["official_ai_rating"])

    def test_incomplete_and_wrong_format_stay_in_denominator(self):
        case=(probe.CASES[0],)
        for stop,content in (("limit","NO"),("eos","NO because of the rules."),("eos","<think>NO")):
            def infer(payload):
                return dict(content=content,stop_type=stop,truncated=False,
                            tokens_predicted=10,generation_settings=payload)
            with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
                result=probe.evaluate(infer,case)
            self.assertEqual(result["matched"]["neither"],1)
            self.assertEqual(result["totals"]["baseline"]["attempted"],1)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {},case)


if __name__=="__main__":
    unittest.main()
