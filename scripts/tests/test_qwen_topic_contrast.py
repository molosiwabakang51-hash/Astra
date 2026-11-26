"""Matched planning prompt and component metrics regression tests."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_topic_contrast as probe


class TopicContrastTests(unittest.TestCase):
    def test_seeded_cases_and_examples_do_not_reveal_topics(self):
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
                    self.assertTrue(all(topic not in probe.EXAMPLES for topic in oracle[1:]))
                    status,plan=probe.prior.parse_plan(q,"|".join(oracle))
                    self.assertEqual(status,"accepted")
                    self.assertTrue(probe.prior.semantic_match(plan,oracle))

    def test_separate_operation_and_exact_topic_metrics(self):
        oracle=("SUM","harbour maintenance fund","tram fare")
        self.assertEqual(probe.components(("DIFF",*oracle[1:]),oracle),
                         dict(operation_correct=False,topics_correct=True,exact_topics=2))
        self.assertEqual(probe.components(("SUM","maintenance fund","tram fare"),oracle),
                         dict(operation_correct=True,topics_correct=False,exact_topics=1))
        self.assertEqual(probe.components(None,oracle)["exact_topics"],0)
        self.assertEqual(probe.components(oracle,None)["exact_topics"],0)
        self.assertEqual(probe.components(("SUM","tram fare","harbour maintenance fund"),oracle)["exact_topics"],2)

    def test_matched_prompt_change_and_wrong_accepted_plan_accounting(self):
        suite=[("eligible_0","Add the harbour maintenance fund and tram fare in coins.",
                ("SUM","harbour maintenance fund","tram fare")),
               ("unsuitable_0","Multiply the harbour maintenance fund by the tram fare.",None)]
        calls=[]
        replies=iter(["DIFF|harbour maintenance fund|tram fare",
                      "SUM|harbour maintenance fund|tram fare",
                      "SUM|harbour maintenance fund|tram fare","NONE"])
        def infer(payload):
            calls.append(payload)
            return dict(content=next(replies),stop_type="eos",truncated=False,
                        tokens_predicted=3,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        self.assertEqual(len(calls),4)
        for i in (0,2):
            self.assertEqual({k:v for k,v in calls[i].items() if k!="prompt"},
                             {k:v for k,v in calls[i+1].items() if k!="prompt"})
        self.assertEqual(calls[1]["prompt"].replace(probe.EXAMPLES,""),calls[0]["prompt"])
        self.assertEqual(calls[2]["prompt"].replace(probe.EXAMPLES,""),calls[3]["prompt"])
        self.assertEqual(result["eligible"]["baseline"]["wrong_eligible_plan"],1)
        self.assertEqual(result["eligible"]["baseline"]["topics_correct"],1)
        self.assertEqual(result["eligible"]["baseline"]["operation_correct"],0)
        self.assertEqual(result["eligible"]["contrast"]["correct"],1)
        self.assertEqual(result["unsuitable"]["contrast"]["unsafe_accept"],1)
        self.assertEqual(result["matched"]["candidate_only"],1)
        self.assertEqual(result["matched"]["baseline_only"],1)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("harbour maintenance fund",str(result))

    def test_failed_calls_cannot_receive_abstention_credit(self):
        suite=probe.cases(19)
        def infer(payload):
            return dict(content="NONE",stop_type="limit",truncated=True,
                        tokens_predicted=probe.CAP,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        for arm in ("baseline","contrast"):
            self.assertEqual(result["totals"][arm]["attempted"],16)
            self.assertEqual(result["totals"][arm]["correct"],0)
            self.assertEqual(result["totals"][arm]["explicit_abstention"],0)
        with self.assertRaises(RuntimeError):
            probe.evaluate(19,lambda _: {},suite)
