"""Tests of independent eligibility veto and actual cost accounting."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_eligibility_gate as probe


class EligibilityGateTests(unittest.TestCase):
    def test_seeded_semantic_controls(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}),16)
            for prefix,n in (("eligible_",8),("hypothetical_",4),("other_",4)):
                self.assertEqual(sum(r[0].startswith(prefix) for r in suite),n)
            self.assertEqual(sum(q.startswith("If ") and oracle is not None
                                 for _,q,oracle in suite),2)
            for _,q,oracle in suite:
                if oracle:
                    status,plan=probe.prior.parse_plan(q,"|".join(oracle))
                    self.assertEqual(status,"accepted")
                    self.assertTrue(probe.prior.semantic_match(plan,oracle))

    def test_gate_veto_can_prevent_error_but_not_create_correct_plan(self):
        suite=[("eligible_0","Add my current aquarium repair budget and coach ticket in coins.",
                ("SUM","aquarium repair budget","coach ticket")),
               ("hypothetical_0","If my aquarium repair budget increased, add it to the coach ticket.",None),
               ("eligible_1","Add the aquarium repair budget and coach ticket in coins.",
                ("SUM","aquarium repair budget","coach ticket")),
               ("other_0","Compare the aquarium repair budget and coach ticket by ratio.",None)]
        plan="SUM|aquarium repair budget|coach ticket"
        # even rows call planner then gate, odd rows gate then planner
        replies=iter([plan,"OTHER","OTHER",plan,"NONE","CURRENT","CURRENT",plan])
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content=next(replies),stop_type="eos",truncated=False,
                        tokens_predicted=3,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        self.assertEqual(len(calls),8)
        self.assertEqual(result["totals"]["baseline_correct"],1)
        self.assertEqual(result["totals"]["gated_correct"],1)
        self.assertEqual(result["totals"]["gains"],1)
        self.assertEqual(result["totals"]["losses"],1)
        self.assertEqual(result["totals"]["vetoed_correct_plans"],1)
        self.assertEqual(result["totals"]["baseline_wrong_accepted"],2)
        self.assertEqual(result["totals"]["gated_wrong_accepted"],1)
        self.assertEqual(result["totals"]["additional_gate_tokens"],12)
        for i in range(0,len(calls),2):
            self.assertEqual({k:v for k,v in calls[i].items() if k!="prompt"},
                             {k:v for k,v in calls[i+1].items() if k!="prompt"})
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("aquarium repair budget",str(result))

    def test_invalid_gate_and_incomplete_planner_fail_closed(self):
        suite=[("eligible_0","Add my aquarium repair budget and coach ticket in coins.",
                ("SUM","aquarium repair budget","coach ticket")),
               ("hypothetical_0","If my aquarium repair budget increased, add it to the coach ticket.",None)]
        plan="SUM|aquarium repair budget|coach ticket"
        replies=iter([(plan,False),("CURRENT because",False),("OTHER",False),(plan,True)])
        def infer(payload):
            answer,failed=next(replies)
            return dict(content=answer,stop_type="limit" if failed else "eos",
                        truncated=failed,tokens_predicted=3,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        self.assertEqual(result["totals"]["baseline_correct"],1)
        self.assertEqual(result["totals"]["gated_correct"],0)
        self.assertEqual(result["totals"]["gated_wrong_accepted"],0)
        self.assertEqual(result["cases"][0]["gate_status"],"invalid")
        self.assertEqual(result["totals"]["planner_complete"],1)
        with self.assertRaises(RuntimeError):
            probe.evaluate(19,lambda _: {},suite)
