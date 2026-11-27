import importlib.util
import math
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_retention_anchor as anchor

class AnchorContracts(unittest.TestCase):
    def test_disjoint_and_bounded_prompts(self):
        anchor.validate_splits()
        class Tokenizer:
            def apply_chat_template(self,messages,**kwargs):
                assert [m["role"] for m in messages]==["system","user"]
                assert kwargs==dict(tokenize=True,add_generation_prompt=True,enable_thinking=False)
                return list(messages[1]["content"].encode())
        a=anchor.encode(Tokenizer(),anchor.TRAIN)
        b=anchor.encode(Tokenizer(),anchor.DEV)
        self.assertEqual(len(a),32)
        self.assertEqual(len(b),16)
        self.assertFalse({tuple(x) for x in a}&{tuple(x) for x in b})
        self.assertNotEqual(anchor.digest(a),anchor.digest(b))
        with self.assertRaises(ValueError):
            anchor.encode(Tokenizer(),["a"*257])

    def test_summary(self):
        self.assertEqual(anchor.summarize([dict(kl=0.1,same_top_token=1),dict(kl=0.3,same_top_token=0)]),
                         dict(attempted=2,mean_kl=0.2,same_top_token=1))
        for rows in ([],[dict(kl=float("nan"),same_top_token=0)],[dict(kl=-1,same_top_token=0)]):
            with self.assertRaises(ValueError):
                anchor.summarize(rows)

@unittest.skipUnless(importlib.util.find_spec("torch"),"Requires PyTorch; mandatory in dedicated workflow")
class TensorContracts(unittest.TestCase):
    def test_exact_kl_and_gradient_against_independent_expression(self):
        import torch
        torch.manual_seed(721)
        for n,k in ((1,4),(2,17),(4,128),(2,1024)):
            student=torch.randn(n,k,requires_grad=True)
            teacher=torch.log_softmax(torch.randn(n,k),dim=-1)
            actual=anchor.retention_loss(student,teacher)
            expected=(teacher.exp()*(teacher-torch.log_softmax(student,dim=-1))).sum()/n
            self.assertTrue(torch.allclose(actual,expected,atol=1e-6))
            grad=torch.autograd.grad(actual,student)[0]
            self.assertTrue(torch.allclose(grad,(student.softmax(-1)-teacher.exp())/n,atol=1e-6))
            self.assertIsNone(teacher.grad)

    def test_identical_teacher_has_zero_penalty(self):
        import torch
        z=torch.randn(3,11,requires_grad=True)
        loss=anchor.retention_loss(z,torch.log_softmax(z.detach(),dim=-1))
        self.assertAlmostEqual(float(loss.detach()),0.0,places=6)
        self.assertTrue(torch.allclose(torch.autograd.grad(loss,z)[0],torch.zeros_like(z),atol=1e-6))

    def test_separate_backward_matches_joint_loss(self):
        import torch
        import torch.nn.functional as F
        p=torch.nn.Parameter(torch.tensor([[1.,2.,-1.],[2.,-1.,0.]]))
        x=torch.tensor([[2.,1.]])
        y=torch.tensor([[1.,-1.]])
        teacher=torch.log_softmax(torch.tensor([[1.,2.,0.]]),dim=-1)
        joint=F.cross_entropy(x@p,torch.tensor([1]))+2*anchor.retention_loss(y@p,teacher)
        joint.backward()
        expected=p.grad.clone()
        p.grad=None
        F.cross_entropy(x@p,torch.tensor([1])).backward()
        (2*anchor.retention_loss(y@p,teacher)).backward()
        self.assertTrue(torch.allclose(p.grad,expected))

    def test_invalid_teacher_fails(self):
        import torch
        x=torch.zeros(1,4)
        for teacher in (torch.zeros(1,4),torch.zeros(1,3),
                        torch.log_softmax(torch.ones(1,4,requires_grad=True),-1),
                        torch.full((1,4),float("nan"))):
            with self.assertRaises(ValueError):
                anchor.retention_loss(x,teacher)

if __name__=="__main__":
    unittest.main()
