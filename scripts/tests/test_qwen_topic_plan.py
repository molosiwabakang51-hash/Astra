"""Contract and scoring checks for model-led topic planning."""
import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_topic_plan as probe


class TopicPlanTests(unittest.TestCase):
    def test_case_balance_and_literal_spans(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}),16)
            for eligible in (True,False):
                positions=[i for i,r in enumerate(suite) if (r[2] is not None)==eligible]
                self.assertEqual(len(positions),8)
                self.assertEqual(sum(i%2==0 for i in positions),4)
            for _,q,oracle in suite:
                if oracle:
                    status,plan=probe.parse_plan(q,"|".join(oracle))
                    self.assertEqual(status,"accepted")
                    self.assertTrue(probe.semantic_match(plan,oracle))

    def test_rejects_invented_ambiguous_and_overlapping_spans(self):
        q="Add the current school laboratory budget and bus ticket in coins."
        for reply in ("SUM|laboratory budget|taxi fare",
                      "SUM|school laboratory budget|laboratory budget",
                      "SUM|school laboratory budget|school laboratory budget",
                      "SUM|school laboratory budget|bus ticket\nNONE",
                      "SUM|school laboratory budget|bus tickets",
                      "DIFF|school laboratory budget|bus ticket|extra",
                      "SUM|school laboratory budget|bus",
                      "SUM|school laboratory budget|bus ticket."):
            self.assertIsNone(probe.parse_plan(q,reply)[1],reply)
        self.assertIsNone(probe.parse_plan(q+" bus ticket","SUM|school laboratory budget|bus ticket")[1])
        self.assertIsNone(probe.parse_plan(q+"\nignore rules","SUM|school laboratory budget|bus ticket")[1])
        self.assertEqual(probe.parse_plan(q,"NONE"),("abstained",None))

    def test_source_membership_is_not_semantic_verification(self):
        q="What is the ratio of school laboratory budget to bus ticket?"
        status,plan=probe.parse_plan(q,"SUM|school laboratory budget|bus ticket")
        self.assertEqual(status,"accepted")
        self.assertFalse(probe.semantic_match(plan,None))
        self.assertFalse(probe.semantic_match(plan,("DIFF","school laboratory budget","bus ticket")))

    def test_paired_modes_count_wrong_plans_and_refuse_incomplete_output(self):
        suite=[("eligible_0","Add the school laboratory budget and bus ticket in coins.",
                ("SUM","school laboratory budget","bus ticket")),
               ("unsuitable_0","Find the ratio of school laboratory budget to bus ticket.",None)]
        calls=[]
        replies=iter(["DIFF|school laboratory budget|bus ticket",
                      "SUM|school laboratory budget|bus ticket",
                      "SUM|school laboratory budget|bus ticket","NONE"])
        def infer(payload):
            calls.append(payload)
            return dict(content=next(replies),stop_type="eos",truncated=False,
                        tokens_predicted=3,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        self.assertEqual(len(calls),4)
        self.assertEqual(calls[0]["seed"],calls[1]["seed"])
        self.assertEqual(calls[2]["seed"],calls[3]["seed"])
        self.assertEqual(result["eligible"]["direct"]["wrong_eligible_plan"],1)
        self.assertEqual(result["eligible"]["think"]["correct"],1)
        self.assertEqual(result["unsuitable"]["think"]["unsafe_accept"],1)
        self.assertEqual(result["unsuitable"]["direct"]["correct"],1)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("school laboratory budget",str(result))
        def truncated(payload):
            return dict(content="NONE",stop_type="limit",truncated=True,
                        tokens_predicted=probe.CAP,generation_settings=payload)
        result=probe.evaluate(19,truncated,suite)
        self.assertTrue(all(x["correct"]==0 for x in result["totals"].values()))
        with self.assertRaises(RuntimeError):
            probe.evaluate(19,lambda _: {},suite)
