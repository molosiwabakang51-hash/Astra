"""Synthetic contrastive formalization tests, not neural measurements."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_logic_contrast as probe


class LogicContrastTests(unittest.TestCase):
    def test_fixture_coverage_and_disjoint_example_symbols(self):
        self.assertEqual(len({row[0] for row in probe.CASES}),16)
        for domain,count in (("basic",6),("extended",6),("unsuitable",4)):
            self.assertEqual(sum(row[0].startswith(domain+"_") for row in probe.CASES),count)
        for name,mapping,argument,premises,conclusion,verdict in probe.CASES:
            for contrast in (False,True):
                prompt=probe.planning_prompt(mapping,argument,contrast)
                self.assertIn(mapping,prompt)
                self.assertTrue(prompt.endswith('ARGUMENT: "'+argument+'"'))
                self.assertEqual(probe.EXAMPLES in prompt,contrast)
            if premises is not None:
                symbols=set(probe.re.findall(r"\b([A-Z]) =",mapping))
                self.assertEqual(probe.parse("FORM: "+"; ".join(premises)+" => "+conclusion,symbols),
                                 (premises,conclusion))
                self.assertFalse(symbols & set("PQRSTUV"))

    def test_strict_syntax_and_known_symbols(self):
        self.assertEqual(probe.parse("FORM: A ^ B; A => !B",{"A","B"}),(("A ^ B","A"),"!B"))
        self.assertEqual(probe.parse("FORM: A <-> B; B => A",{"A","B"}),(("A <-> B","B"),"A"))
        for bad in ("FORM: P ^ Q => P","FORM: A;; B => A",
                    "FORM: A => B\nFORM: B => A","FORM: A => B => A","NONE","Answer: FORM: A => A"):
            self.assertIsNone(probe.parse(bad,{"A","B"}))

    def test_paired_settings_and_wrong_faithful_distinction(self):
        cases=(("basic_0","A = p; B = q.","Argument",("A",),"A","ENTAILED"),
               ("unsuitable_0","A = p; B = q.","Ambiguous",None,None,None))
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(generation_settings=payload,stop_type="eos",truncated=False,
                        tokens_predicted=8,content="FORM: B => B" if len(calls)<=2 else "NONE")
        def verify(question):
            return "ENTAILED" if question.startswith("Premises: B. Conclusion: B.") else "COUNTEREXAMPLE"
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(infer,verify,cases)
        self.assertEqual(len(calls),4)
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="prompt"},
                             {k:v for k,v in b.items() if k!="prompt"})
            self.assertEqual(a["prompt"].replace(probe.EXAMPLES,""),b["prompt"].replace(probe.EXAMPLES,""))
        for arm in ("baseline","contrast"):
            self.assertEqual(report["totals"][arm]["correct"],1)
            self.assertEqual(report["totals"][arm]["wrong_accepted"],1)
            self.assertEqual(report["totals"][arm]["faithful"],0)
            self.assertEqual(report["totals"][arm]["verdict_correct"],1)
        self.assertIsNone(report["official_ai_rating"])

    def test_incomplete_or_invalid_native_metadata_fails_closed(self):
        def infer(payload):
            return dict(generation_settings=payload,stop_type="limit",truncated=False,
                        tokens_predicted=probe.CAP,content="FORM: A => A")
        def verify(question):
            self.fail("Incomplete output reached verifier")
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(infer,verify)
            with self.assertRaises(RuntimeError):
                probe.evaluate(lambda _: {},verify)
        self.assertEqual(len(report["cases"]),32)
        self.assertEqual(report["matched"]["neither"],16)
        self.assertTrue(all(not row["accepted"] for row in report["cases"]))
