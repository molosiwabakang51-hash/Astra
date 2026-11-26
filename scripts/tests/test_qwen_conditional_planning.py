import sys
from pathlib import Path
import random
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_conditional_planning as probe

class ConditionalPlanningTests(unittest.TestCase):
    def test_reference_values_and_balanced_suite(self):
        for seed in range(128):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({n for n,_,_ in suite}),12)
            self.assertEqual(sum(v is not None for _,_,v in suite),8)
            rng=random.Random(seed)
            a,b,c,d=rng.randint(12,23),rng.randint(4,8),rng.randint(2,5),rng.randint(8,17)
            keys={n:v for n,_,v in suite}
            self.assertEqual(int(keys["eligible_boxes"]),a*b-c*b-d)
            self.assertEqual(int(keys["eligible_revenue"]),a*d-a*b-c)
            self.assertEqual(int(keys["eligible_unit"])*a+c,a*b+c)
            self.assertEqual(int(keys["eligible_weight"]),sum([b]*a)+d)
            self.assertEqual(int(keys["eligible_change"]),sum([d-c]*b))
            self.assertEqual(int(keys["eligible_fraction"]),a)
            self.assertEqual(int(keys["eligible_average"])*3,(d-c)+d+(d+c))
            self.assertEqual(int(keys["eligible_negative"]),c-a)

    def test_retry_gate_preserves_abstention_and_valid_results(self):
        self.assertTrue(probe.should_retry(True,"CALC: invalid",None))
        for complete,proposal,parsed in [(False,"CALC: invalid",None),(True,"NONE",None),
                (True," NONE ",None),(True,"",None),(True,"CALC: 2+2",("4","prompt"))]:
            self.assertFalse(probe.should_retry(complete,proposal,parsed))

    def test_candidate_measures_gains_and_new_unsuitable_acceptance(self):
        suite=[("eligible_a","Q1","7"),("unsuitable_a","Q2",None),("eligible_b","Q3","8"),
               ("unsuitable_b","Q4",None)]
        replies=iter(["bad","CALC: 7","bad","CALC: 7","CALC: 8","NONE"])
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload,stop_type="eos",truncated=False,
                        tokens_predicted=5,content=next(replies))
        def invoke(operation,question,proposal=None):
            self.assertIn(question,{"Q1","Q2","Q3","Q4"})
            if operation=="plan":
                self.assertIsNone(proposal)
                return question
            return (proposal[-1],"synthesis") if proposal.startswith("CALC:") else None
        report=probe.evaluate(infer,invoke,42,suite)
        self.assertEqual(report["baseline_correct"],1)
        self.assertEqual(report["candidate_correct"],2)
        self.assertEqual(report["candidate_only"],1)
        self.assertEqual(report["newly_unsuitable_accepted"],1)
        self.assertEqual(report["eligible_retries"],1)
        self.assertEqual(report["unsuitable_retries"],1)
        self.assertEqual(report["baseline_tokens"],20)
        self.assertEqual(report["candidate_tokens"],30)
        self.assertEqual([p["n_predict"] for p in calls],[128,768,128,768,128,128])
        self.assertIsNone(report["official_ai_rating"])
        self.assertTrue(all("proposal" not in row for row in report["cases"]))

    def test_selective_policy_uses_shared_retry_and_reports_missed_gains(self):
        suite=[("eligible_a","Q1","7"),("eligible_b","Q2","8"),("unsuitable_a","Q3",None)]
        replies=iter(["bad","CALC: 7","bad","CALC: 8","bad","CALC: 7"])
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload,stop_type="eos",truncated=False,
                        tokens_predicted=5,content=next(replies))
        def invoke(operation,question,proposal=None):
            if operation=="plan":
                return question
            if operation=="retry_gate":
                self.assertEqual(proposal,"bad")
                return question=="Q1"
            return (proposal[-1],"synthesis") if proposal.startswith("CALC:") else None
        result=probe.evaluate(infer,invoke,42,suite,selective=True)
        narrow=result["selective_policy"]
        self.assertEqual(len(calls),6)
        self.assertEqual(result["candidate_correct"],2)
        self.assertEqual(narrow["correct"],1)
        self.assertEqual(narrow["missed_broad_gains"],1)
        self.assertEqual(narrow["unsuitable_accepted"],0)
        self.assertEqual(result["candidate_unsuitable_accepted"],1)
        self.assertEqual(narrow["tokens"],20)

    def test_selective_suite_includes_numeric_only_unsuitable_controls(self):
        suite=probe.selective_cases(42)
        self.assertEqual(len(suite),16)
        self.assertEqual(sum(v is None for _,_,v in suite),8)
        self.assertEqual(sum(n.startswith("unsuitable_numeric_") and
                             q.endswith("Reply only with the number.") and v is None
                             for n,q,v in suite),4)

    def test_incomplete_plan_never_evaluated_or_retried(self):
        def infer(payload):
            return dict(generation_settings=payload,stop_type="limit",truncated=True,
                        tokens_predicted=128,content="CALC: 7")
        def invoke(operation,question,proposal=None):
            self.assertEqual(operation,"plan")
            return question
        report=probe.evaluate(infer,invoke,42,[("eligible_a","Q","7")])
        self.assertEqual(report["candidate_correct"],0)
        self.assertEqual(report["eligible_retries"],0)

if __name__=="__main__":
    unittest.main()
