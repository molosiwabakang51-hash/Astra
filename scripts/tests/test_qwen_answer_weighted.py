import importlib.util
from pathlib import Path
import re
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_answer_weighted as data

class CurriculumTests(unittest.TestCase):
    def test_sizes_reproducible_and_disjoint(self):
        train,dev=data.suites()
        self.assertEqual((len(train),len(dev)),(96,44))
        self.assertEqual(data.suites(),(train,dev))
        self.assertFalse({e.question for e in train}&{e.question for e in dev})
        for domain,n in (("quantity",32),("abstraction",32),("logic",8),
                         ("causal",8),("grounding",8),("uncertainty",8)):
            self.assertEqual(sum(e.domain==domain for e in train),n)

    def test_independent_target_oracles_and_sign_balance(self):
        signs=set()
        for is_train in (True,False):
            for e in data.targeted(is_train):
                q=e.question
                if e.domain=="quantity":
                    nums=[(1 if a=="receive" else -1)*int(b) for a,b in re.findall(r"(receive|send) (\d+)",q)]
                    start=re.search(r"starts at (\d+)",q)
                    value=int(start.group(1))+sum(nums) if start else int(re.search(r"ends at (\d+)",q).group(1))-sum(nums)
                    self.assertEqual(str(value),e.answer)
                    if is_train:
                        signs.add((bool(start),tuple(n>0 for n in nums)))
                else:
                    x,y=map(int,re.search(r"starting at \((\d+),(\d+)\)",q).groups())
                    p,qv=re.search(r"into \(([^,]+),([^)]+)\)",q).groups()
                    matrices={"y":(0,1),"x+y":(1,1),"y-x":(-1,1),"x-y":(1,-1)}
                    a,b=matrices[p];c,d=matrices[qv]
                    for _ in range(int(re.search(r"simultaneously, (\d+) times",q).group(1))):
                        x,y=a*x+b*y,c*x+d*y
                    self.assertEqual(str(x if "coordinate first" in q else y),e.answer)
        self.assertEqual(len(signs),16)

    def test_offsets_and_eos_weighting(self):
        class Tok:
            eos_token_id=999
            def apply_chat_template(self,*args,**kwargs):
                return [10,11,12]
            def encode(self,s,add_special_tokens=False):
                return [ord(c) for c in s]
            def __call__(self,s,**kwargs):
                return dict(input_ids=self.encode(s),offset_mapping=[(i,i+1) for i in range(len(s))])
        for e in data.targeted(True):
            ids,prefix,mask=data.encode(Tok(),e)
            self.assertEqual(prefix,[10,11,12])
            self.assertEqual(len(mask),len(ids)-len(prefix))
            self.assertEqual(sum(mask),len(e.answer)+1)
            self.assertEqual(mask[-1],1)
            self.assertEqual(ids[-1],999)
        with self.assertRaises(ValueError):
            data.encode(Tok(),e,4)

@unittest.skipUnless(importlib.util.find_spec("torch"),"Requires real tensor dependencies")
class LossTests(unittest.TestCase):
    def test_weight_one_matches_cross_entropy_and_gradient(self):
        import torch
        import torch.nn.functional as F
        g=torch.Generator().manual_seed(127)
        for n in range(2,18):
            z=torch.randn(n,17,generator=g,requires_grad=True)
            target=torch.randint(0,17,(n,),generator=g)
            mask=torch.arange(n)>=n//2
            actual=data.weighted_ce(z,target,mask,1.)
            expected=F.cross_entropy(z,target)
            torch.testing.assert_close(actual,expected)
            torch.testing.assert_close(torch.autograd.grad(actual,z)[0],torch.autograd.grad(expected,z)[0])

    def test_weight_four_matches_explicit_replication(self):
        import torch
        import torch.nn.functional as F
        g=torch.Generator().manual_seed(128)
        for n in range(2,18):
            z=torch.randn(n,11,generator=g,requires_grad=True)
            t=torch.randint(0,11,(n,),generator=g)
            m=torch.arange(n)>=n//2
            repeat=1+3*m.long()
            a=data.weighted_ce(z,t,m,4.)
            b=F.cross_entropy(z.repeat_interleave(repeat,dim=0),t.repeat_interleave(repeat))
            torch.testing.assert_close(a,b)
            torch.testing.assert_close(torch.autograd.grad(a,z)[0],torch.autograd.grad(b,z)[0])
        with self.assertRaises(ValueError):
            data.weighted_ce(z,t,torch.ones_like(m),4.)

if __name__=="__main__":
    unittest.main()
