import json
import math
from pathlib import Path
import re
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_transfer_choices as probe
import evaluate_qwen_transfer_logits as evaluator

class TransferLogitTests(unittest.TestCase):
    def test_balanced_distinct_deterministic_cases(self):
        suite=probe.cases()
        self.assertEqual(suite,probe.cases())
        self.assertNotEqual(suite,probe.cases(probe.SEED+1))
        self.assertEqual(len(suite),24)
        self.assertEqual(len({c.question for c in suite}),24)
        self.assertEqual(len({c.ident for c in suite}),24)
        for domain in probe.DOMAINS:
            subset=[c for c in suite if c.domain==domain]
            self.assertEqual(sorted(c.correct for c in subset),[0,1,2,3])
            for c in subset:
                self.assertEqual(len(set(c.options)),4)
                self.assertTrue(probe.prompt(c).endswith("Reply with only A, B, C or D."))
                self.assertNotIn(c.ident,probe.prompt(c))

    def test_independent_arithmetic_and_causal_oracles(self):
        for c in probe.cases():
            numbers=list(map(int,re.findall(r"\d+",c.question)))
            actual=c.options[c.correct]
            if c.domain=="causal":
                a,b,forced=numbers
                expected_y=int(forced!=b)
                self.assertEqual(forced,1-max(a,b))
                self.assertEqual(actual,f"X={forced}, Y={expected_y}")
            elif c.domain=="abstraction":
                a,b=numbers
                for _ in range(2):
                    a,b=b,a+b
                self.assertEqual(actual,f"({a},{b})")
            elif c.domain=="quantitative":
                total,groups,extra=numbers
                self.assertEqual(int(actual)*groups+extra,total)

    def test_stable_scores_and_ties(self):
        uniform=probe.score([0.0]*4,2)
        self.assertFalse(uniform["correct"])
        self.assertTrue(uniform["tie"])
        self.assertAlmostEqual(uniform["negative_log_likelihood"],math.log(4))
        self.assertAlmostEqual(uniform["brier"],0.75)
        high=probe.score([-10000.0,10000.0,0.0,1.0],0)
        self.assertEqual(high["predicted"],"B")
        self.assertFalse(high["correct"])
        self.assertTrue(math.isfinite(high["negative_log_likelihood"]))
        self.assertAlmostEqual(high["negative_log_likelihood"],20000)
        self.assertTrue(probe.score([1,2,3,4],3)["correct"])
        for bad in ([1,2,3],[1,2,float("nan"),4],[1,2,3,float("inf")]):
            with self.assertRaises(ValueError):
                probe.score(bad,0)

    def test_paired_requires_same_prompts(self):
        a=[dict(ident="x",domain="logic",prompt_sha256="abc",correct=True)]
        b=[dict(a[0],correct=False)]
        self.assertEqual(evaluator.paired(a,b)["regressed"],1)
        for bad in ([],[dict(b[0],prompt_sha256="changed")],b+b):
            with self.assertRaises(ValueError):
                evaluator.paired(a,bad)

    def test_adapter_rejects_wrong_base_and_unmodified_parameters(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            (root/"adapter").mkdir()
            (root/"adapter"/"adapter_model.safetensors").write_bytes(b"test")
            config=dict(peft_type="LORA",task_type="CAUSAL_LM",r=8,
                        target_modules=["q_proj","v_proj"],layers_to_transform=[24,25,26,27])
            (root/"adapter"/"adapter_config.json").write_text(json.dumps(config))
            hashes={"model-00001.safetensors":"hash"}
            report=dict(status="COMPLETE_NEURAL_ADAPTATION_PILOT",model=evaluator.MODEL,
                        revision=evaluator.REVISION,base_weight_sha256=hashes,
                        frozen_baseline_reproduced=True,initial_adapter_sha256="before",
                        final_adapter_sha256="after")
            (root/"report.json").write_text(json.dumps(report))
            self.assertEqual(len(evaluator.adapter_manifest(root,hashes)),64)
            with self.assertRaises(ValueError):
                evaluator.adapter_manifest(root,{"model-00001.safetensors":"other"})
            report["final_adapter_sha256"]="before"
            (root/"report.json").write_text(json.dumps(report))
            with self.assertRaises(ValueError):
                evaluator.adapter_manifest(root,hashes)

if __name__=="__main__":
    unittest.main()
