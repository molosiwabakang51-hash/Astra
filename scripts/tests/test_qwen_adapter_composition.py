import importlib.util
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
from qwen_adapter_composition import compose,verify

@unittest.skipUnless(importlib.util.find_spec("torch"),"Requires CPU torch")
class CompositionTests(unittest.TestCase):
    def pair(self,r=4,i=11,o=13,seed=9):
        import torch
        g=torch.Generator().manual_seed(seed)
        return {"x.lora_A.weight":torch.randn(r,i,generator=g)*.1,
                "x.lora_B.weight":torch.randn(o,r,generator=g)*.1}

    def test_random_exact_delta_and_forward(self):
        import torch
        for seed in range(32):
            l,r=self.pair(seed=seed),self.pair(seed=seed+100)
            a,b="x.lora_A.weight","x.lora_B.weight"
            for w in ((.5,.5),(1.,0.),(0.,1.),(.25,.75)):
                c=compose(l,r,w,2.,1.)
                self.assertEqual(c[a].shape,(8,11))
                self.assertEqual(c[b].shape,(13,8))
                self.assertEqual(verify(l,r,c,w,2.,1.)["elements_checked"],143)
                x=torch.randn(3,11,generator=torch.Generator().manual_seed(seed))
                expected=2*(w[0]*((x@l[a].T)@l[b].T)+w[1]*((x@r[a].T)@r[b].T))
                torch.testing.assert_close((x@c[a].T)@c[b].T,expected,rtol=2e-5,atol=2e-7)

    def test_factor_averaging_is_not_delta_averaging(self):
        import torch
        a,b="x.lora_A.weight","x.lora_B.weight"
        l={a:torch.tensor([[1.,0.]]),b:torch.tensor([[1.],[0.]])}
        r={a:torch.tensor([[0.,1.]]),b:torch.tensor([[0.],[1.]])}
        c=compose(l,r)
        want=torch.eye(2)*.5
        torch.testing.assert_close(c[b]@c[a],want)
        self.assertFalse(torch.equal(((l[b]+r[b])/2)@((l[a]+r[a])/2),want))

    def test_invalid_inputs_and_tampered_factors(self):
        import torch
        l,r=self.pair(),self.pair(seed=123)
        a,b="x.lora_A.weight","x.lora_B.weight"
        for bad in ({}, {a:r[a]},dict(r,unexpected=torch.ones(1)),
                    {**r,a:r[a].double()},{**r,b:r[b][:,:2]},
                    {**r,a:torch.full_like(r[a],float("nan"))}):
            with self.assertRaises(ValueError):
                compose(l,bad)
        for w in ((1.,1.),(-1.,2.),(float("nan"),.5),(1.,)):
            with self.assertRaises(ValueError):
                compose(l,r,w)
        c=compose(l,r)
        c[b][0,0]+=1
        with self.assertRaises(ValueError):
            verify(l,r,c)

    @unittest.skipUnless(importlib.util.find_spec("peft") and importlib.util.find_spec("transformers"),
                         "Requires actual PEFT")
    def test_peft_loaded_rank16_matches_two_real_adapters(self):
        import torch
        from transformers import Qwen3Config,Qwen3ForCausalLM
        from peft import LoraConfig,get_peft_model,get_peft_model_state_dict,set_peft_model_state_dict
        torch.manual_seed(37)
        base=Qwen3ForCausalLM(Qwen3Config(vocab_size=64,hidden_size=32,intermediate_size=64,
            num_hidden_layers=1,num_attention_heads=4,num_key_value_heads=2,head_dim=8))
        def cfg(rank):
            return LoraConfig(task_type="CAUSAL_LM",r=rank,lora_alpha=rank*2,
                lora_dropout=0.,bias="none",target_modules=["q_proj","v_proj"])
        model=get_peft_model(base,cfg(8),adapter_name="left")
        model.add_adapter("right",cfg(8))
        states={}
        for name in ("left","right"):
            state={k:torch.randn_like(v)*.1 for k,v in get_peft_model_state_dict(model,adapter_name=name).items()}
            set_peft_model_state_dict(model,state,adapter_name=name)
            states[name]=get_peft_model_state_dict(model,adapter_name=name)
        model.add_adapter("blend",cfg(16))
        set_peft_model_state_dict(model,compose(states["left"],states["right"]),adapter_name="blend")
        checked=verify(states["left"],states["right"],get_peft_model_state_dict(model,adapter_name="blend"))
        self.assertGreater(checked["elements_checked"],0)
        modules=[m for m in model.modules() if hasattr(m,"lora_A") and "blend" in m.lora_A]
        self.assertEqual(len(modules),2)
        for m in modules:
            for n in ("left","right","blend"):
                self.assertEqual(m.scaling[n],2.)
            torch.testing.assert_close(m.get_delta_weight("blend"),
                .5*m.get_delta_weight("left")+.5*m.get_delta_weight("right"),rtol=2e-5,atol=2e-7)
            x=torch.randn(3,32)
            model.eval()
            m.set_adapter("blend")
            base_output=m.base_layer(x)
            want=base_output+((x@m.lora_A["left"].weight.T)@m.lora_B["left"].weight.T)+((x@m.lora_A["right"].weight.T)@m.lora_B["right"].weight.T)
            torch.testing.assert_close(m(x),want,rtol=2e-5,atol=2e-7)

if __name__=="__main__":
    unittest.main()
