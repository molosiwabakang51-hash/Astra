"""Regression tests of bounded end-to-end decomposition and fallback."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_contract_scoped as probe


class ContractScopedTests(unittest.TestCase):
    @staticmethod
    def build(question,history):
        return "\n".join(f"Previous {role}: {content}" for role,content in history if role=="user")+probe.BOUNDARY+question

    def test_exact_decomposition_and_units(self):
        q="Add my current orchard budget and my current tram ticket, both in coins."+probe.SUFFIX
        op,subs=probe.decompose(q)
        self.assertEqual(op,"SUM")
        self.assertEqual(subs,("What is the current orchard budget in coins?"+probe.SUFFIX,
                               "What is the current tram ticket in coins?"+probe.SUFFIX))
        self.assertEqual(probe.decompose(q.replace("coins","euros"))[1][0],
                         "What is the current orchard budget in euros?"+probe.SUFFIX)
        for bad in (q.removesuffix(probe.SUFFIX), "Suppose the budget doubles. "+q,
                    q.replace("tram ticket","tram ticket minus five"),
                    q.replace("tram ticket","orchard budget"),q+" Then double it."):
            self.assertIsNone(probe.decompose(bad))

    def test_seeded_fixtures_and_no_oracle_routing(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({r["case"] for r in suite}),12)
            for item in suite:
                prepared=probe.prepare(self.build,item)
                self.assertEqual(prepared,probe.prepare(self.build,
                    {k:v for k,v in item.items() if k in ("question","history")}))
                if item["case"].startswith("control_"):
                    self.assertEqual(prepared["route"],"ordinary")
                    self.assertEqual(prepared["baseline"],prepared["candidate"])
                else:
                    self.assertEqual(prepared["route"],"scoped")
                    self.assertEqual(len(prepared["candidate"]),2)
            self.assertEqual(probe.prepare(lambda q,h:q,suite[0])["route"],"fallback")
            self.assertEqual(probe.prepare(
                lambda q,h:"[Some linked corrections are omitted"+probe.BOUNDARY+q,
                suite[0])["route"],"fallback")
            with self.assertRaises(RuntimeError):
                probe.prepare(lambda q,h:q+" extra",suite[0])

    def test_composition_requires_two_valid_scalars(self):
        self.assertEqual(probe.compose("SUM",(-4,8)),4)
        self.assertEqual(probe.compose("DIFF",(8,-4)),12)
        self.assertEqual(probe.compose("SUM",("UNKNOWN",4)),"UNKNOWN")
        for values in ((None,"UNKNOWN"),(None,3),(True,4),("42",4),(3,),(3,4,5)):
            self.assertIsNone(probe.compose("SUM",values))
        self.assertIsNone(probe.compose("MUL",(2,3)))

    def test_inference_accounting_and_unknown_not_full_comprehension(self):
        suite=probe.cases(19)
        item=suite[1]
        # A deliberately wrong known operand is masked by correct UNKNOWN.
        p=probe.prepare(self.build,item)
        answers={p["baseline"][0]:"UNKNOWN",p["candidate"][0]:"-1",
                 p["candidate"][1]:"UNKNOWN"}
        calls=[]
        def infer(payload):
            calls.append(payload)
            answer=next((v for k,v in answers.items() if k in payload["prompt"]),"UNKNOWN")
            return dict(content=answer,stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(19,infer,self.build,[item])
            self.assertEqual(report["totals"]["candidate"]["correct"],1)
            self.assertEqual(report["totals"]["candidate"]["correct_operands"],1)
            self.assertEqual(report["totals"]["candidate"]["wrong_operand_correct_result"],1)
            self.assertEqual(len(calls),3)
            calls.clear()
            full=probe.evaluate(19,infer,self.build,suite)
            self.assertEqual(len(calls),32)
            self.assertEqual(full["totals"]["baseline"]["calls"],12)
            self.assertEqual(full["totals"]["candidate"]["calls"],20)
            self.assertEqual(full["routes"],dict(ordinary=4,fallback=0,scoped=8))
            self.assertIsNone(full["official_ai_rating"])
            with self.assertRaises(RuntimeError):
                probe.evaluate(19,lambda _: {},self.build,[item])
