"""Regression tests for matched scenario-aware extraction and evidence completeness."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_scoped_evidence_semantics as probe


class ScopedEvidenceSemanticsTests(unittest.TestCase):
    @staticmethod
    def builder(suite):
        data={}
        for item in suite:
            q,h=item["question"],tuple(item["history"])
            data[q,h]=tuple(f for group in item["required"] for f in group)
            plan=probe.prior.decompose(q)
            if plan:
                for j,sub in enumerate(plan[1]):
                    data[sub,h]=item["required"][j]
        return lambda q,h: "".join('Previous user: "'+f+'"\n' for f in data[q,tuple(h)])+probe.prior.BOUNDARY+q

    def test_fresh_balanced_cases_and_evidence(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({i["case"] for i in suite}),16)
            for domain in ("absolute","relative","missing","control"):
                indices=[j for j,i in enumerate(suite) if i["case"].startswith(domain+"_")]
                self.assertEqual(len(indices),4)
                self.assertEqual(sum(j%2==0 for j in indices),2)
            missing=[i for i in suite if i["case"].startswith("missing_")]
            self.assertEqual(sum(i["operands"][0]=="UNKNOWN" for i in missing),2)
            self.assertEqual(sum(i["operands"][1]=="UNKNOWN" for i in missing),2)
            for i in suite:
                p=probe.prepare(self.builder([i]),i)
                self.assertEqual(len(p["candidate"]),len(i["operands"]))
                for j,prompt in enumerate(p["candidate"]):
                    self.assertTrue(all(f in prompt for f in i["required"][j]))

    def test_relative_fixture_requires_all_preceding_evidence(self):
        item=next(i for i in probe.cases(19) if i["case"]=="relative_0")
        build=self.builder([item])
        for fragment in item["required"][0]:
            with self.assertRaises(RuntimeError):
                probe.prepare(lambda q,h:build(q,h).replace(fragment,"Omitted"),item)
        for extra in ("[Some linked corrections are omitted.]\n",
                      'Previous user: "Actually, use spicy rice."\n'):
            with self.assertRaises(RuntimeError):
                probe.prepare(lambda q,h:extra+build(q,h),item)

    def test_exact_pairing_and_false_uncertainty_metrics(self):
        suite=probe.cases(19)
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="UNKNOWN",stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result=probe.evaluate(19,infer,self.builder(suite),suite)
        self.assertEqual(len(calls),56)
        self.assertEqual(sum(probe.EVIDENCE_RULE in p["prompt"] for p in calls),24)
        for arm in ("baseline","evidence"):
            self.assertEqual(result["totals"][arm]["calls"],28)
            self.assertEqual(result["totals"][arm]["correct"],4)
            self.assertEqual(result["totals"][arm]["false_unknown"],12)
            self.assertEqual(result["totals"][arm]["false_unknown_operands"],24)
            self.assertEqual(result["totals"][arm]["correct_result_wrong_operand"],4)
        for item in suite:
            rows=[r for r in result["cases"] if r["case"]==item["case"]]
            if item["case"].startswith("control_"):
                self.assertEqual(rows[0]["calls"][0]["prompt_sha256"],rows[1]["calls"][0]["prompt_sha256"])
        # Verify full payload pairing: only the candidate's system text can change.
        offset=0
        for i,item in enumerate(suite):
            n=len(item["operands"])
            a,b=calls[offset:offset+n],calls[offset+n:offset+2*n]
            for left,right in zip(a,b):
                self.assertEqual({k:v for k,v in left.items() if k!="prompt"},
                                 {k:v for k,v in right.items() if k!="prompt"})
                self.assertEqual(left["prompt"].replace(probe.EVIDENCE_RULE,""),
                                 right["prompt"].replace(probe.EVIDENCE_RULE,""))
            offset+=2*n
        self.assertIsNone(result["official_ai_rating"])

    def test_incomplete_output_and_numeric_guess_not_rewarded(self):
        item=next(i for i in probe.cases(19) if i["case"]=="missing_0")
        known=item["operands"][1]
        answers=iter([("42",False),(str(known),False),("UNKNOWN",True),(str(known),False)])
        def infer(payload):
            value,failed=next(answers)
            return dict(content=value,stop_type="limit" if failed else "eos",truncated=failed,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result=probe.evaluate(19,infer,self.builder([item]),[item])
            with self.assertRaises(RuntimeError):
                probe.evaluate(19,lambda _: {},self.builder([item]),[item])
        self.assertEqual(result["totals"]["baseline"]["unsupported_numeric"],1)
        self.assertEqual(result["totals"]["baseline"]["unsupported_numeric_operands"],1)
        self.assertEqual(result["totals"]["evidence"]["complete"],0)
        self.assertEqual(result["totals"]["evidence"]["correct"],0)
