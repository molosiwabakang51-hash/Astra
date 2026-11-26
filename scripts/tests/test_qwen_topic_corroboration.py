"""Structural corroboration is deliberately narrower than semantic eligibility."""
import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_topic_corroboration as probe


class TopicCorroborationTests(unittest.TestCase):
    def test_scope_and_balanced_seeded_fixtures(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}),24)
            for prefix in ("explicit_","paraphrase_","unsuitable_"):
                self.assertEqual(sum(r[0].startswith(prefix) for r in suite),8)
            for case,q,oracle in suite:
                expected=probe.contract(q)
                if case.startswith("explicit_"):
                    self.assertTrue(probe.prior.semantic_match(expected,oracle))
                else:
                    self.assertIsNone(expected)

    def test_contract_rejects_ambiguous_or_extended_requests(self):
        q="Add my current harvest storage budget and my current tram pass, both in coins."
        self.assertEqual(probe.contract(q),("SUM","harvest storage budget","tram pass"))
        for bad in (q+" Then divide by two.", "Translate: "+q,
                    q.replace("tram pass","tram pass and bus fare"),
                    q.replace("tram pass","tram pass minus fees"),
                    q.replace("tram pass","harvest storage budget"),
                    q.replace("tram pass","future tram pass"),
                    q.replace("both in coins","in euros"),
                    q.replace("harvest storage budget","harvest storage budget in euros"),
                    q.replace("Add","Suppose you add"), q+"\n", " "+q):
            self.assertIsNone(probe.contract(bad),bad)
        self.assertIsNone(probe.corroborate(q,("DIFF","harvest storage budget","tram pass")))
        self.assertIsNone(probe.corroborate(q,("SUM","storage budget","tram pass")))
        self.assertEqual(probe.corroborate(q,("SUM","tram pass","harvest storage budget")),
                         ("SUM","tram pass","harvest storage budget"))

    def test_same_neural_call_reused_and_lost_coverage_recorded(self):
        explicit="Add my current harvest storage budget and my current tram pass, both in coins."
        paraphrase="What is the combined total of my harvest storage budget and tram pass in coins?"
        unsupported="Suppose my harvest storage budget doubled. Add it to my tram pass."
        plan=("SUM","harvest storage budget","tram pass")
        suite=[("explicit_0",explicit,plan),("paraphrase_0",paraphrase,plan),
               ("unsuitable_0",unsupported,None)]
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="|".join(plan),stop_type="eos",truncated=False,
                        tokens_predicted=9,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        self.assertEqual(len(calls),3)
        self.assertEqual(result["additional_inference_calls"],0)
        self.assertEqual(result["totals"]["baseline_correct"],2)
        self.assertEqual(result["totals"]["gated_correct"],2)
        self.assertEqual(result["totals"]["vetoed_correct_plans"],1)
        self.assertEqual(result["totals"]["baseline_wrong_accepted"],1)
        self.assertEqual(result["totals"]["gated_wrong_accepted"],0)
        self.assertEqual(result["totals"]["gains"],1)
        self.assertEqual(result["totals"]["losses"],1)
        self.assertIsNone(result["official_ai_rating"])

    def test_incomplete_or_invalid_output_receives_no_credit(self):
        suite=probe.cases(19)
        def infer(payload):
            return dict(content="NONE",stop_type="limit",truncated=True,
                        tokens_predicted=probe.CAP,generation_settings=payload)
        result=probe.evaluate(19,infer,suite)
        self.assertEqual(result["totals"]["complete"],0)
        self.assertEqual(result["totals"]["gated_correct"],0)
        self.assertEqual(result["totals"]["baseline_correct"],0)
        with self.assertRaises(RuntimeError):
            probe.evaluate(19,lambda _: {},suite)
