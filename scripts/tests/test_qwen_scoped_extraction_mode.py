"""Regression checks for matched scoped extraction profiles."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_scoped_extraction_mode as probe


class ScopedExtractionModeTests(unittest.TestCase):
    @staticmethod
    def builder(suite):
        data={}
        for item in suite:
            q,h=item["question"],tuple(item["history"])
            data[q,h]=item["required"]
            plan=probe.prior.decompose(q)
            if plan:
                for j,sub in enumerate(plan[1]):
                    data[sub,h]=(item["required"][j],)
        return lambda q,h: "".join('Previous user: "'+f+'"\n' for f in data[q,tuple(h)])+probe.prior.BOUNDARY+q

    def test_seeded_cases_and_routes(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({r["case"] for r in suite}),12)
            for domain in ("complete","missing","control"):
                indices=[i for i,r in enumerate(suite) if r["case"].startswith(domain+"_")]
                self.assertEqual(len(indices),4)
                self.assertEqual(sum(i%2==0 for i in indices),2)
            build=self.builder(suite)
            for item in suite:
                p=probe.prepare(build,item)
                self.assertEqual(p["route"],"ordinary" if item["case"].startswith("control_") else "scoped")
                self.assertEqual(len(p["candidate"]),len(item["operands"]))
                for j,prompt in enumerate(p["candidate"]):
                    self.assertIn(item["required"][j],prompt)

    def test_preflight_rejects_incomplete_or_irrelevant_evidence(self):
        item=probe.cases(19)[0]
        build=self.builder([item])
        for wrong in (
            lambda q,h:q,
            lambda q,h:build(q,h).replace(item["required"][0],"Unrelated."),
            lambda q,h:"[Some linked corrections are omitted.]\n"+build(q,h),
            lambda q,h:'Previous user: "Actually, use spicy rice."\n'+build(q,h),
        ):
            with self.assertRaises(RuntimeError):
                probe.prepare(wrong,item)

    def test_equal_calls_and_only_scoped_profiles_change(self):
        suite=probe.cases(19)
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="UNKNOWN",stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result=probe.evaluate(19,infer,self.builder(suite),suite)
        self.assertEqual(len(calls),40)
        for arm in ("think","direct"):
            self.assertEqual(result["totals"][arm]["calls"],20)
            self.assertEqual(result["totals"][arm]["correct"],4)
            self.assertEqual(result["by_domain"]["complete"][arm]["false_unknown"],4)
            self.assertEqual(result["by_domain"]["missing"][arm]["false_unknown_operands"],4)
            self.assertEqual(result["by_domain"]["missing"][arm]["correct_result_wrong_operand"],4)
        for item in suite:
            rows=[r for r in result["cases"] if r["case"]==item["case"]]
            if item["case"].startswith("control_"):
                self.assertEqual(rows[0]["calls"][0]["prompt_sha256"],rows[1]["calls"][0]["prompt_sha256"])
                self.assertTrue(all(r["calls"][0]["mode"]=="think" for r in rows))
            else:
                self.assertEqual({r["calls"][0]["mode"] for r in rows},{"think","direct"})
        self.assertIsNone(result["official_ai_rating"])

    def test_numeric_claim_on_missing_operand_and_failed_call_separate(self):
        item=next(i for i in probe.cases(19) if i["case"]=="missing_0")
        known=item["operands"][0]
        answers=iter([(str(known),False),("2",False),(str(known),False),("UNKNOWN",True)])
        def infer(payload):
            value,failed=next(answers)
            return dict(content=value,stop_type="limit" if failed else "eos",
                        truncated=failed,tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result=probe.evaluate(19,infer,self.builder([item]),[item])
            with self.assertRaises(RuntimeError):
                probe.evaluate(19,lambda _: {},self.builder([item]),[item])
        self.assertEqual(result["totals"]["think"]["unsupported_numeric"],1)
        self.assertEqual(result["totals"]["think"]["unsupported_numeric_operands"],1)
        self.assertEqual(result["totals"]["direct"]["complete"],0)
        self.assertEqual(result["totals"]["direct"]["correct"],0)
        self.assertEqual(result["totals"]["direct"]["false_unknown_operands"],0)
