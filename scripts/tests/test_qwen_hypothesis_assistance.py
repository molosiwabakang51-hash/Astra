"""Public synthetic contract tests, not native intelligence measurements."""
import itertools
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_hypothesis_assistance as probe


class HypothesisAssistanceTests(unittest.TestCase):
    def test_exhaustive_partial_observations_match_independent_oracle(self):
        count=0
        for partial in itertools.product((-1,0,1),repeat=8):
            observed=tuple((i,v) for i,v in enumerate(partial) if v!=-1)
            for target in range(8):
                if partial[target]!=-1:
                    continue
                self.assertEqual(probe.reference(observed,target),
                                 probe.independent_reference(observed,target))
                count+=1
        self.assertEqual(count,8*3**7)

    def test_fixture_generation_balanced_and_reproducible(self):
        suite=probe.cases()
        self.assertEqual(suite,probe.cases())
        self.assertNotEqual(suite,probe.cases(probe.SEED+1))
        self.assertEqual(len(suite),16)
        self.assertEqual(len({(c[2],c[3]) for c in suite}),16)
        for group in probe.GROUPS:
            self.assertEqual(sum(c[1]==group for c in suite),4 if group=="ambiguous" else 3)
        for _,group,obs,target,expected in suite:
            names,answer=probe.independent_reference(obs,target)
            self.assertEqual(answer,expected)
            self.assertNotIn(target,dict(obs))
            self.assertTrue(names)
            if group.startswith("unique"):
                self.assertEqual(len(names),1)
            else:
                self.assertGreater(len(names),1)

    def test_filter_only_supplies_compatible_rule_names_not_query_predictions(self):
        for _,_,obs,target,_ in probe.cases():
            base=probe.prompt(obs,target,False)
            candidate=probe.prompt(obs,target,True)
            names,_=probe.independent_reference(obs,target)
            addition=("An exact filter checked the observed examples against every allowed rule. "
                      "The complete set of compatible rules is: "+", ".join(names)+".\n")
            self.assertEqual(candidate.replace(addition,""),base)
            self.assertNotIn(probe.bits(target)+" => output=",candidate)

    def test_invalid_and_inconsistent_evidence(self):
        for observed,target in (
            (((0,1),(0,0)),1),
            (((8,1),),1),
            (((0,2),),1),
            (((0,1),),0),
            ((),8),
        ):
            with self.assertRaises(ValueError):
                probe.reference(observed,target)
        # All permitted rules except NOT A output 0 at 000.
        # NOT A also outputs 1 at 001, so these two examples contradict every rule.
        self.assertEqual(probe.reference(((0,1),(1,0)),2),((),"INCONSISTENT"))

    def test_paired_inference_and_fixed_uncertainty_denominator(self):
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
        for arm in ("baseline","assisted"):
            self.assertEqual(report["totals"][arm]["attempted"],16)
            self.assertEqual(report["totals"][arm]["correct"],4)
            self.assertEqual(report["totals"][arm]["false_abstention"],12)
            self.assertEqual(report["totals"][arm]["false_certainty"],0)
        self.assertEqual(report["matched"]["both"],4)
        self.assertIsNone(report["official_ai_rating"])

    def test_preflight_and_incomplete_answers_fail_closed(self):
        case=probe.cases()[0]
        wrong=case[:-1]+("NO" if case[-1]=="YES" else "YES",)
        with self.assertRaises(RuntimeError):
            probe.evaluate(lambda _:self.fail("Inference before fixture check"),(wrong,))
        for stop,content in (("limit",case[-1]),("eos",case[-1]+" because..."),("eos","<think>"+case[-1])):
            def infer(payload):
                return dict(content=content,stop_type=stop,truncated=False,
                            tokens_predicted=10,generation_settings=payload)
            with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
                report=probe.evaluate(infer,(case,))
            self.assertEqual(report["matched"]["neither"],1)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {},(case,))


if __name__=="__main__":
    unittest.main()
