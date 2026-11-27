import importlib.util
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
from qwen_projected_update import project
import qwen_projection_anchors as anchor

class AnchorContracts(unittest.TestCase):
    def test_reference_split_and_masks(self):
        class Tokenizer:
            eos_token_id=999
            def apply_chat_template(self,messages,**kw):
                self.assertions=kw
                return list(messages[-1]["content"].encode())
            def encode(self,text,add_special_tokens=False):
                return list(text.encode())
        tok=Tokenizer()
        train=anchor.references(tok,True)
        dev=anchor.references(tok,False)
        self.assertEqual((len(train),len(dev)),(32,16))
        self.assertFalse({tuple(r[2]) for r in train}&{tuple(r[2]) for r in dev})
        for ids,labels,prefix in train+dev:
            self.assertEqual(labels,[-100]*len(prefix)+ids[len(prefix):])
            self.assertEqual(ids[-1],999)

@unittest.skipUnless(importlib.util.find_spec("torch"),"Requires dedicated tensor workflow")
class ProjectionContracts(unittest.TestCase):
    def test_known_projection_and_unmodified_control(self):
        import torch
        p=torch.nn.Parameter(torch.tensor([2.,3.]))
        old=[torch.tensor([0.,0.])]
        g=[torch.tensor([1.,0.])]
        r=project([p],old,g,apply=False)
        self.assertEqual(r["proposed_dot"],2.)
        self.assertFalse(r["projected"])
        self.assertTrue(torch.equal(p,torch.tensor([2.,3.])))
        r=project([p],old,g)
        self.assertTrue(r["projected"])
        self.assertEqual(r["projected_dot"],0.)
        self.assertTrue(torch.equal(p,torch.tensor([0.,3.])))

    def test_global_projection_across_parameters(self):
        import torch
        torch.manual_seed(802)
        for _ in range(64):
            old=[torch.randn(5),torch.randn(7)]
            proposed=[torch.nn.Parameter(x+torch.randn_like(x)*.1) for x in old]
            g=[torch.randn_like(x) for x in old]
            expected=sum(float(((p.detach().double()-b.double())*h.double()).sum()) for p,b,h in zip(proposed,old,g))
            r=project(proposed,old,g)
            self.assertAlmostEqual(r["proposed_dot"],expected,places=10)
            actual=sum(float(((p.detach().double()-b.double())*h.double()).sum()) for p,b,h in zip(proposed,old,g))
            self.assertLessEqual(actual,max(0,expected)+1e-6)
            if expected>0:
                self.assertLessEqual(actual,1e-8+1e-5*abs(expected)+1e-12)
                self.assertTrue(r["projected"])
            else:
                self.assertFalse(r["projected"])

    def test_actual_adamw_preconditioned_update(self):
        import torch
        p=torch.nn.Parameter(torch.tensor([1.,2.]))
        opt=torch.optim.AdamW([p],lr=.1,weight_decay=0.,foreach=False)
        old=[p.detach().clone()]
        loss=(p*torch.tensor([-1.,-4.])).sum()
        loss.backward()
        opt.step()
        moments=opt.state[p]["exp_avg"].clone()
        r=project([p],old,[torch.tensor([1.,0.])])
        self.assertGreater(r["proposed_dot"],0.)
        self.assertLessEqual(r["projected_dot"],1e-7)
        self.assertAlmostEqual(float(p[0]),1.,places=6)
        self.assertGreater(float(p[1]),2.)
        self.assertTrue(torch.equal(opt.state[p]["exp_avg"],moments))

    def test_zero_gradient_and_malformed_inputs(self):
        import torch
        p=torch.nn.Parameter(torch.tensor([1.,2.]))
        before=[torch.zeros(2)]
        self.assertFalse(project([p],before,[torch.zeros(2)])["projected"])
        for g in ([],[torch.ones(3)],[torch.tensor([float("nan"),0.])]):
            with self.assertRaises(ValueError):
                project([p],before,g)
        with self.assertRaises(ValueError):
            project([p,p],before+before,[torch.ones(2)]*2)

if __name__=="__main__":
    unittest.main()
