import pathlib
import sys
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"quality"))
from qwen_neural_attenuation import attenuate,verify
try:
    import torch
except ImportError:
    torch=None

@unittest.skipIf(torch is None,"PyTorch not installed")
class DeltaTest(unittest.TestCase):
    def state(self):
        g=torch.Generator().manual_seed(2738)
        return {"m.lora_A.weight":torch.randn(8,11,generator=g),
                "m.lora_B.weight":torch.randn(13,8,generator=g)}

    def test_exact_effective_delta_and_forward(self):
        s=self.state()
        before={k:t.clone() for k,t in s.items()}
        t=attenuate(s)
        self.assertEqual(verify(s,t,3)["max_dense_delta_error"],0.)
        x=torch.arange(11,dtype=torch.float64)
        a,b=s.values()
        aa,bb=t.values()
        self.assertTrue(torch.allclose(2*bb.double()@aa.double()@x,
                                       b.double()@a.double()@x,rtol=1e-12,atol=1e-12))
        self.assertTrue(all(torch.equal(s[k],v) for k,v in before.items()))
        self.assertTrue(all(s[k].data_ptr()!=t[k].data_ptr() for k in s))

    def test_reject_invalid_and_noop(self):
        s=self.state()
        a,b=s
        for bad in ({},{a:s[a]},dict(s,extra=torch.zeros(1)),
                    dict(s,**{a:torch.zeros(7,11)}),
                    dict(s,**{a:s[a].double()}),
                    dict(s,**{a:torch.full((8,11),float("nan"))})):
            with self.assertRaises(ValueError): attenuate(bad)
        t=attenuate(s)
        t[b][0,0]+=1
        with self.assertRaises(ValueError): verify(s,t)
        zero=dict(s,**{b:torch.zeros_like(s[b])})
        with self.assertRaises(ValueError): verify(zero,attenuate(zero))

    def test_real_peft_lora_forward(self):
        try:
            from peft import LoraConfig,get_peft_model
            from peft import get_peft_model_state_dict,set_peft_model_state_dict
        except ImportError:
            self.skipTest("PEFT not installed")
        class Net(torch.nn.Module):
            def __init__(self):
                super().__init__()
                self.proj=torch.nn.Linear(11,13,bias=False)
            def forward(self,x): return self.proj(x)
        cfg=LoraConfig(r=8,lora_alpha=16,lora_dropout=0,target_modules=["proj"])
        m=get_peft_model(Net(),cfg,adapter_name="full")
        m.add_adapter("half",cfg)
        with torch.no_grad():
            for n,p in m.named_parameters():
                if ".full.weight" in n:p.copy_(torch.arange(p.numel()).reshape(p.shape)*.0007+.01)
        full=get_peft_model_state_dict(m,adapter_name="full")
        half=attenuate(full)
        set_peft_model_state_dict(m,half,adapter_name="half")
        verify(full,get_peft_model_state_dict(m,adapter_name="half"))
        x=torch.randn(2,11)
        m.eval()
        with torch.no_grad():
            with m.disable_adapter(): base=m(x)
            m.set_adapter("full"); y=m(x)
            m.set_adapter("half"); z=m(x)
        self.assertTrue(torch.allclose(z-base,(y-base)*.5,rtol=2e-4,atol=2e-7))

if __name__=="__main__":
    unittest.main()
