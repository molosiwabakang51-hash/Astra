"""Lightweight curriculum contracts plus actual tensor/gradient tests when torch is installed."""
import importlib.util
import math
from pathlib import Path
import re
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_contrastive_curriculum as curriculum
from qwen_transfer_choices import cases
from qwen_calibration_loss import choice_loss

class ContrastiveCurriculumTests(unittest.TestCase):
    def test_disjoint_balanced_pairs(self):
        suite=curriculum.training_cases()
        self.assertEqual(suite,curriculum.training_cases())
        self.assertNotEqual(suite,curriculum.training_cases(curriculum.SEED+1))
        self.assertEqual(len(suite),32)
        self.assertEqual(len({c.question for c in suite}),32)
        self.assertFalse({c.question for c in suite}&{c.question for c in cases()})
        by_id={c.ident:c for c in suite}
        for domain in curriculum.DOMAINS:
            subset=[c for c in suite if c.domain==domain]
            self.assertEqual(sorted(c.correct for c in subset),[0,0,1,1,2,2,3,3])
            for i in range(4):
                a,b=[by_id[f"train_{domain}_{i}_{k}"] for k in (0,1)]
                self.assertEqual(a.options,b.options)
                self.assertNotEqual(a.correct,b.correct)
                self.assertNotEqual(a.question,b.question)

    def test_independent_answers(self):
        for c in curriculum.training_cases():
            actual=c.options[c.correct]
            nums=list(map(int,re.findall(r"\d+",c.question)))
            if c.domain=="causal":
                a,b,r=nums
                self.assertEqual(actual,f"R={r}, S={int(r!=a)}")
            elif c.domain=="logic":
                expected=("The first condition must be false." if "observed to be false" in c.question
                          else "The first condition may be true or false.")
                self.assertEqual(actual,expected)
            elif c.domain=="abstraction":
                a,b,n=nums
                for _ in range(n):
                    a,b=a+b,b-a
                self.assertEqual(actual,f"({a},{b})")
            else:
                boxes,extra,total=nums
                self.assertEqual(int(actual)*boxes+extra,total)

@unittest.skipUnless(importlib.util.find_spec("torch"),"Requires PyTorch; mandatory in training workflow")
class CalibrationLossTests(unittest.TestCase):
    def test_zero_smoothing_is_exact_full_vocabulary_ce(self):
        import torch
        import torch.nn.functional as F
        x=torch.tensor([[2.,-1.,3.,0.,4.,-2.],[0.,1.,-3.,2.,5.,-1.]],requires_grad=True)
        ids=[0,2,3,5]
        y=torch.tensor([2,1])
        actual=choice_loss(x,ids,y,0)
        expected=F.cross_entropy(x,torch.tensor([3,2]))
        self.assertTrue(torch.equal(actual,expected))
        actual.backward()
        self.assertTrue(torch.isfinite(x.grad).all())

    def test_smoothing_preserves_nonanswer_mass_gradient_and_penalizes_overconfidence(self):
        import torch
        x=torch.tensor([[12.,-4.,-3.,-2.,2.,1.]],requires_grad=True)
        labels=[0,1,2,3]
        target=torch.tensor([0])
        base=choice_loss(x,labels,target,0)
        g0=torch.autograd.grad(base,x)[0]
        smooth=choice_loss(x,labels,target,.1)
        g1=torch.autograd.grad(smooth,x)[0]
        self.assertTrue(torch.allclose(g0[:,4:],g1[:,4:],atol=1e-6))
        self.assertGreater(float(g1[0,0]),0)
        self.assertLess(float(g1[0,1]),0)
        self.assertGreater(float(smooth),float(base))

    def test_gradient_optimization_improves_wrong_choice(self):
        import torch
        x=torch.nn.Parameter(torch.tensor([[-3.,3.,0.,0.,0.,0.]]))
        opt=torch.optim.SGD([x],lr=.5)
        y=torch.tensor([0])
        initial=float(choice_loss(x,[0,1,2,3],y,.1).detach())
        for _ in range(60):
            opt.zero_grad()
            loss=choice_loss(x,[0,1,2,3],y,.1)
            loss.backward()
            opt.step()
        self.assertLess(float(choice_loss(x,[0,1,2,3],y,.1).detach()),initial)
        self.assertEqual(int(x.argmax()),0)
        self.assertTrue(torch.isfinite(x).all())

    def test_invalid_inputs_fail_closed(self):
        import torch
        x=torch.zeros((1,6))
        y=torch.tensor([0])
        for ids,target,smoothing in (([0,0,1,2],y,.1),([0,1,2,9],y,.1),
                                     ([0,1,2,3],torch.tensor([4]),.1),
                                     ([0,1,2,3],y,.75),([0,1,2,3],y,float("nan"))):
            with self.assertRaises(ValueError):
                choice_loss(x,ids,target,smoothing)
        x[0,0]=float("nan")
        with self.assertRaises(ValueError):
            choice_loss(x,[0,1,2,3],y,.1)

if __name__=="__main__":
    unittest.main()
