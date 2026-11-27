"""Contract tests for eventwise quantity rejection; no synthetic intelligence scores."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_eventwise_quantity as probe

class EventwiseTests(unittest.TestCase):
    def test_fresh_sources_match_reference_operands(self):
        self.assertEqual(len(probe.FRESH),12)
        self.assertEqual(sum(c[2] is None for c in probe.FRESH),4)
        for name,clauses,expected,result in probe.FRESH:
            if expected is not None:
                labels=";".join(x[0] for x in expected.split(";"))
                self.assertEqual(probe.interpret("eventwise",labels,clauses),(expected,False,False),name)

    def test_exact_event_count_and_rejection(self):
        clauses=("There are 3 coins.","I add 4 coins.")
        self.assertEqual(probe.interpret("global","NONE",clauses),(None,True,False))
        self.assertEqual(probe.interpret("eventwise","I;X",clauses),(None,True,False))
        self.assertEqual(probe.interpret("eventwise","X;X",clauses),(None,True,True))
        for bad in ("NONE","X","I;X;X","I;I","X;I","I;X extra","I;",""):
            self.assertEqual(probe.interpret("eventwise",bad,clauses),(None,False,False),bad)
        for n in range(1,9):
            self.assertEqual(probe.event_grammar(n).splitlines()[0].count(' ";" event'),n-1)
        for bad in (0,9,True,1.0):
            with self.assertRaises(ValueError):
                probe.event_grammar(bad)

    def test_source_grounding_does_not_establish_operation_semantics(self):
        clauses=("There are 3 coins.","I add 4 coins.")
        self.assertEqual(probe.interpret("eventwise","I;D",clauses),("I:3:coin;D:4:coin",False,False))
        self.assertEqual(probe.interpret("eventwise","I;A",("There are 3 and 7 coins.","I add 4 coins.")),(None,False,False))

    def test_paired_scoring_distinguishes_false_reject_and_wrong_acceptance(self):
        cases=(("eligible",("There are 3 coins.","I add 4 coins."),"I:3:coin;A:4:coin","7"),
               ("unsupported",("Two people own 3 and 5 coins.",),None,None))
        calls=[]
        def infer(p):
            calls.append(p)
            content = ("NONE" if p["grammar"]==probe.anchored.LABEL_GRAMMAR
                       else ("I;D" if len(calls)<=2 else "X"))
            return dict(generation_settings=p,stop_type="eos",truncated=False,
                        tokens_predicted=3,content=content)
        def verify(q):
            return ("-1" if "decrease" in q else "7","coin",2)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(infer,verify,(("fixture",cases),))
        self.assertEqual(len(calls),4)
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k not in ("prompt","grammar")},
                             {k:v for k,v in b.items() if k not in ("prompt","grammar")})
            self.assertEqual(a["prompt"].split("SOURCE EVENTS:",1)[1],b["prompt"].split("SOURCE EVENTS:",1)[1])
        self.assertEqual(report["totals"]["global"]["false_reject"],1)
        self.assertEqual(report["totals"]["eventwise"]["wrong_accepted"],1)
        self.assertEqual(report["totals"]["eventwise"]["eligible_correct"],0)
        self.assertEqual(report["totals"]["eventwise"]["unsupported_correct"],1)
        self.assertEqual(report["totals"]["eventwise"]["all_x"],1)
        self.assertEqual(report["matched"],dict(both=1,global_only=0,eventwise_only=0,neither=1))
        self.assertIsNone(report["official_ai_rating"])

    def test_incomplete_outputs_never_count_as_rejections(self):
        cases=(("unsupported",("Unclear event.",),None,None),)
        for stop,content in (("limit","X"),("eos","<think>guess</think>X")):
            def infer(p):
                return dict(generation_settings=p,stop_type=stop,truncated=False,
                            tokens_predicted=3,content=content)
            with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
                report=probe.evaluate(infer,lambda _:self.fail("Unexpected verification"),(("fixture",cases),))
            self.assertEqual(report["matched"]["neither"],1)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {},lambda _:None,(("fixture",cases),))

if __name__=="__main__":
    unittest.main()
