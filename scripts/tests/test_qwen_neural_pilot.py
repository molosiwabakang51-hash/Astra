"""Lightweight tests of the neural pilot contracts; no model or GPU needed."""
import re
import importlib.util
import json
import tempfile
import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))

import qwen_neural_curriculum as data
import train_qwen_neural_pilot as pilot


class FakeTokenizer:
    eos_token_id=99
    def apply_chat_template(self,messages,**kwargs):
        assert [m["role"] for m in messages]==["system","user"]
        assert kwargs==dict(tokenize=True,add_generation_prompt=True,enable_thinking=False)
        self.messages=messages
        return [10,11,12]
    def encode(self,text,add_special_tokens):
        assert add_special_tokens is False
        self.completion=text
        return [20,21,22]


class NeuralPilotTests(unittest.TestCase):
    def test_public_splits_are_deterministic_disjoint_and_balanced(self):
        for n in (4,32,64,256):
            train,dev=data.curriculum(True,n),data.curriculum(False,n)
            self.assertEqual(train,data.curriculum(True,n))
            self.assertEqual(len(train),n)
            self.assertEqual(len({e.question for e in train}),n)
            self.assertFalse({e.question for e in train}&{e.question for e in dev})
            self.assertNotEqual(data.digest(train),data.digest(dev))
            for domain in data.DOMAINS:
                self.assertEqual(sum(e.domain==domain for e in train),n//4)
                self.assertEqual(sum(e.domain==domain for e in dev),n//4)
        for n in (0,3,257,True):
            with self.assertRaises(ValueError):
                data.curriculum(True,n)

    def test_numerical_and_correction_oracles(self):
        for train in (True,False):
            for e in data.curriculum(train,256):
                # Ignore unique case labels before the actual problem.
                question=e.question.split(": ",1)[-1]
                if e.domain=="quantity":
                    a,b,c=map(int,re.findall(r"\d+",question))
                    self.assertEqual(e.answer,str(a+b-c if train else a-b+c))
                elif e.domain=="pattern":
                    # There may be another colon before the sequence.
                    sequence=list(map(int,re.findall(r"-?\d+",question)))
                    self.assertEqual(e.answer,str(sequence[-1]+sequence[-1]-sequence[-2]))
                elif e.domain=="correction":
                    numbers=re.findall(r"\d+",question)
                    self.assertEqual(e.answer,numbers[-1] if len(numbers)==2 else "UNKNOWN")
                else:
                    self.assertIn(e.answer,("YES","NO","UNKNOWN"))

    def test_mask_excludes_system_and_user_tokens_and_rejects_truncation(self):
        t=FakeTokenizer()
        example=data.curriculum(True,4)[0]
        ids,labels,prefix=data.token_example(t,example,7)
        self.assertEqual(ids,[10,11,12,20,21,22,99])
        self.assertEqual(labels,[-100,-100,-100,20,21,22,99])
        self.assertEqual(prefix,[10,11,12])
        self.assertEqual(t.completion,example.completion)
        self.assertEqual(t.messages[-1]["content"],example.question)
        with self.assertRaises(ValueError):
            data.token_example(t,example,6)
        t.eos_token_id=None
        with self.assertRaises(ValueError):
            data.token_example(t,example,7)

    def test_final_answer_not_extracted_from_arbitrary_prose(self):
        self.assertTrue(data.grade("15","15"))
        self.assertTrue(data.grade("Reason: 10 + 5 = 15.\nAnswer: 15","15"))
        self.assertFalse(data.grade("Reason: 15 might be correct.\nAnswer: 20","15"))
        self.assertFalse(data.grade("The result might be 15.","15"))
        self.assertFalse(data.grade("Reason: result.\nAnswer: 15\nAnswer: 20","15"))
        self.assertFalse(data.formatted("15"))
        self.assertTrue(data.formatted("Reason: exact.\nAnswer: 15"))
        # This grading measures final answers, not explanation validity.
        self.assertTrue(data.grade("Reason: a false premise.\nAnswer: 15","15"))

    def test_paired_score_preserves_fixed_denominator_and_control_regressions(self):
        def row(d,correct,fmt=False):
            return {"domain":d,"correct":correct,"formatted":fmt}
        before={"a":row("quantity",False),"b":row("control",True),"c":row("quantity",True)}
        after={"a":row("quantity",True,True),"b":row("control",False),"c":row("quantity",True,True)}
        result=pilot.paired_report(before,after)
        self.assertEqual(result["attempted"],3)
        self.assertEqual(result["improved"],1)
        self.assertEqual(result["regressed"],1)
        self.assertEqual(result["both_correct"],1)
        self.assertEqual(result["by_domain"]["control"]["adapted_correct"],0)
        with self.assertRaises(ValueError):
            pilot.paired_report(before,{"a":after["a"]})
        with self.assertRaises(ValueError):
            pilot.paired_report(before,dict(after,b=row("pattern",True)))


    def test_partial_checkpoint_is_immutable_and_not_a_completed_report(self):
        class FakeModel:
            def save_pretrained(self,path,**kwargs):
                path.mkdir()
                (path/"adapter_model.safetensors").write_bytes(b"test-only tensor")
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory)
            pilot.save_partial(FakeModel(),out,1,"initial","updated",[1.25],"float32",{"base":"hash"})
            checkpoint=out/"partial"/"step-001"
            manifest=json.loads((checkpoint/"checkpoint.json").read_text())
            self.assertEqual(manifest["status"],"PARTIAL_NEURAL_ADAPTATION_NOT_EVALUATED")
            self.assertEqual(manifest["completed_steps"],1)
            self.assertEqual(manifest["adapter_file_sha256"],pilot.file_digest(checkpoint/"adapter_model.safetensors"))
            self.assertFalse((out/"report.json").exists())
            with self.assertRaises(RuntimeError):
                pilot.save_partial(FakeModel(),out,1,"initial","updated",[1.25],"float32",{})

    @unittest.skipUnless(importlib.util.find_spec("torch"),"Requires PyTorch; mandatory in training workflow")
    def test_suffix_projection_matches_full_shifted_ce_and_gradients(self):
        import torch
        import torch.nn.functional as F
        from types import SimpleNamespace
        torch.manual_seed(711)
        for prefix_length in (1,3,9):
            for suffix_length in (1,2,5):
                ids=list(range(prefix_length+suffix_length))
                prefix=ids[:prefix_length]
                labels=[-100]*prefix_length+ids[prefix_length:]
                logits=torch.randn(1,len(ids),32,requires_grad=True)
                def fake_model(**kwargs):
                    self.assertEqual(kwargs["logits_to_keep"],suffix_length+1)
                    return SimpleNamespace(logits=logits[:,-kwargs["logits_to_keep"]:,:])
                actual=pilot.assistant_loss(fake_model,ids,labels,prefix)
                expected=F.cross_entropy(logits[0,:-1],torch.tensor(labels[1:]))
                self.assertTrue(torch.allclose(actual,expected))
                a=torch.autograd.grad(actual,logits,retain_graph=True)[0]
                b=torch.autograd.grad(expected,logits)[0]
                self.assertTrue(torch.allclose(a,b))
        with self.assertRaises(ValueError):
            pilot.assistant_loss(lambda **kw: self.fail("invalid mask entered model"),[1,2],[1,2],[1])


if __name__=="__main__":
    unittest.main()
