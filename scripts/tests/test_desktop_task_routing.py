"""Classification and split routing evidence tests; no physical model claims."""
import hashlib
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_task_routing as R
import astra_generated_validation as G


class TaskRoutingTests(unittest.TestCase):
    def setUp(self):
        self.seeds=[]
        questions=set()
        for n in range(1,100):
            seed=f"{n:032x}"
            texts=[c.text for c in G.generate(seed)]
            if len(set(texts))!=len(texts) or questions.intersection(texts):
                continue
            questions.update(texts)
            self.seeds.append(seed)
            if len(self.seeds)==4:
                break
        self.assertEqual(4,len(self.seeds))

    def reports(self):
        result=[]
        for seed in self.seeds:
            rows=[]
            for case in G.generate(seed):
                rows.append({"id":case.id,"family":case.family,
                             "self_review":{"first_correct":False,"final_correct":False,"seconds":10,"calls":3},
                             "cooperation":{"first_correct":False,"final_correct":True,"seconds":12,"calls":3}})
            result.append({"suite":G.VERSION,"seed_commitment":hashlib.sha256(bytes.fromhex(seed)).hexdigest(),
                           "cases":rows,"deployment_allowed":True})
        return result

    def test_task_forms_are_advisory(self):
        for question,expected in (("Compare bonds and shares","comparison"),
                                  ("Calculate the present value","quantitative"),
                                  ("How do I value a bond?","procedure"),
                                  ("Assess the risks","evaluation"),
                                  ("Explain duration","explanation"),
                                  ("Why?","contextual"),
                                  ("Clear conversation","context_reset"),
                                  ("Bonjour","unclassified")):
            with self.subTest(question=question):
                self.assertEqual(expected,R.classify(question)["task_form"])
        self.assertFalse(R.classify("Why?")["review_candidate"])
        for invalid in ("", "   ", "x"*4097, "x\x00y", 3):
            with self.assertRaises(ValueError):
                R.classify(invalid)

    def test_calibration_and_validation_both_required(self):
        reports=self.reports()
        for report in reports[2:]:
            for row in report["cases"]:
                if row["family"]=="present_value":
                    row["cooperation"]["final_correct"]=False
        result=R.validate_transfer_reports(self.seeds,reports)
        self.assertTrue(result["calibration"]["present_value"]["passes"])
        self.assertFalse(result["validation"]["present_value"]["passes"])
        self.assertNotIn("present_value",result["candidate_families"])
        self.assertIn("simple_interest",result["candidate_families"])
        self.assertEqual(288,result["model_calls"])
        self.assertFalse(result["deployment_allowed"])

    def test_latency_gate_and_malformed_records(self):
        reports=self.reports()
        for row in reports[0]["cases"]:
            if row["family"]=="weighted_return":
                row["cooperation"]["seconds"]=100
        self.assertNotIn("weighted_return",R.validate_transfer_reports(self.seeds,reports)["candidate_families"])
        for bad in (float("nan"),0,True,1201):
            report=self.reports()
            report[0]["cases"][0]["self_review"]["seconds"]=bad
            with self.assertRaises(ValueError):
                R.validate_transfer_reports(self.seeds,report)
        with self.assertRaises(ValueError):
            R.validate_transfer_reports([self.seeds[0]]*4,reports)
        reports[0]["cases"].pop()
        with self.assertRaises(ValueError):
            R.validate_transfer_reports(self.seeds,reports)

    def test_owner_approval_and_preselected_trial_order(self):
        reports=self.reports()
        with patch.object(G,"evaluate",side_effect=reports) as run:
            with self.assertRaises(ValueError):
                R.evaluate(lambda *_:None,seeds=self.seeds)
            run.assert_not_called()
            result=R.evaluate(lambda *_:None,approved=True,seeds=self.seeds)
        self.assertEqual(self.seeds,[call.args[0] for call in run.call_args_list])
        self.assertEqual(self.seeds,result["reproduction_seeds"])
        self.assertFalse(result["deployment_allowed"])

if __name__=="__main__":
    unittest.main()
