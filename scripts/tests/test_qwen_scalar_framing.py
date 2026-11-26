import sys
from pathlib import Path
import random
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_scalar_framing as probe


class ScalarFramingTests(unittest.TestCase):
    def test_suite_reproducible_and_arithmetic_keys_independently_checked(self):
        for seed in range(128):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({name for name, _, _ in suite}), 12)
            for domain in ("arithmetic", "inverse", "control"):
                self.assertEqual(sum(n.startswith(domain+"_") for n, _, _ in suite), 4)
            rng = random.Random(seed)
            a,b,c,d,e = (rng.randint(12,23),rng.randint(3,7),rng.randint(2,5),
                         rng.randint(9,15),rng.randint(2,6))
            recurrence = d
            for _ in range(c):
                recurrence = (recurrence-e)*3
            keys = {n: v for n,_,v in suite}
            self.assertEqual(keys["arithmetic_recurrence"], str(recurrence))
            self.assertEqual(keys["arithmetic_negative"], str(d-sum([a]*b)))
            self.assertEqual(keys["arithmetic_profit"], str(sum([d+b]*a)-sum([d]*a)-c))
            self.assertEqual(keys["arithmetic_split"], str(len([1]*((a-c)*b+d))))
            self.assertEqual(int(keys["inverse_update"])*b+c*b, (d+c)*b)
            self.assertEqual(int(keys["inverse_inventory"])*b-c*b, (a-c)*b)

    def test_transfer_suite_new_structures_and_independent_reference_values(self):
        for seed in range(128):
            suite = probe.transfer_cases(seed)
            self.assertEqual(suite, probe.transfer_cases(seed))
            self.assertEqual(len({name for name, _, _ in suite}), 12)
            for domain in ("arithmetic", "inverse", "control"):
                self.assertEqual(sum(n.startswith(domain+"_") for n, _, _ in suite), 4)
            self.assertFalse({q for _,q,_ in suite} & {q for _,q,_ in probe.cases(seed)})
            rng = random.Random(seed)
            a,b,c,d,e = (rng.randint(7,13),rng.randint(3,6),rng.randint(15,29),
                         rng.randint(2,5),rng.randint(4,9))
            keys = {n:v for n,_,v in suite}
            self.assertEqual(int(keys["arithmetic_weighted"]),sum([b]*a+[e]*d))
            self.assertEqual(int(keys["arithmetic_ceiling"]),len(list(range(0,a*b+1,b))))
            self.assertEqual(int(keys["arithmetic_fraction"]),a*b*d-(a*b*d)//b+d)
            self.assertEqual(int(keys["arithmetic_percentage"]),c*10+c-e)
            self.assertEqual(2*int(keys["inverse_difference"])+d,2*c+d)
            self.assertEqual(2*(int(keys["inverse_perimeter"])+a),2*(a+c))
            self.assertEqual((c-d)+(c+e)+int(keys["inverse_average"]),3*c)
            self.assertEqual(int(keys["inverse_repeated"])+b*d,c+b*d)
            self.assertEqual(int(keys["control_quoted_data"]),a)

    def test_transfer_keeps_fixed_denominators_and_same_native_settings(self):
        suite=probe.transfer_cases(42)
        calls=[]
        def infer(payload):
            i=len(calls)//2
            calls.append(payload)
            return dict(generation_settings=payload,stop_type="eos",truncated=False,
                        tokens_predicted=5,content=suite[i][2])
        report=probe.evaluate(42,infer,suite)
        self.assertEqual(len(calls),24)
        self.assertEqual(report["matched"],dict(both_correct=12,baseline_only=0,candidate_only=0,neither=0))
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="prompt"},
                             {k:v for k,v in b.items() if k!="prompt"})
        for domain in report["by_domain"].values():
            self.assertEqual(domain["baseline"]["attempted"],4)
            self.assertEqual(domain["framing"]["attempted"],4)

    def test_only_prompt_changes_and_paired_regression_is_counted(self):
        calls = []
        suite = [("arithmetic_a", "First question", "7"), ("inverse_b", "Second question", "9")]
        replies = iter(["0", "7", "0", "9"])
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload,stop_type="eos",truncated=False,
                        tokens_predicted=5,content=next(replies))
        report = probe.evaluate(42, infer, suite)
        self.assertEqual(report["matched"],dict(both_correct=0,baseline_only=1,candidate_only=1,neither=0))
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="prompt"},
                             {k:v for k,v in b.items() if k!="prompt"})
            self.assertEqual(sum(probe.SUFFIX in p["prompt"] for p in (a,b)),1)
        self.assertNotIn(probe.SUFFIX,calls[0]["prompt"])
        self.assertIn(probe.SUFFIX,calls[2]["prompt"])
        self.assertIsNone(report["official_ai_rating"])
        self.assertTrue(all("content" not in row for row in report["cases"]))

    def test_incomplete_and_verbose_outputs_not_credited(self):
        replies=iter([("limit",True,"7"),("eos",False,"The answer is 7")])
        def infer(payload):
            stop,truncated,content=next(replies)
            return dict(generation_settings=payload,stop_type=stop,truncated=truncated,
                        tokens_predicted=5,content=content)
        report=probe.evaluate(42,infer,[("arithmetic_a","Question","7")])
        self.assertEqual(report["matched"]["neither"],1)
        self.assertEqual([r["failure"] for r in report["cases"]],["incomplete","noncanonical_ungraded"])

    def test_wrong_native_settings_fail_closed(self):
        def infer(payload):
            return dict(generation_settings=dict(payload,n_predict=32),stop_type="eos",
                        truncated=False,tokens_predicted=1,content="7")
        with self.assertRaisesRegex(RuntimeError,"settings"):
            probe.evaluate(42,infer,[("arithmetic_a","Question","7")])

if __name__ == "__main__":
    unittest.main()
