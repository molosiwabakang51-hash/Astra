"""CPU resume tests; the dedicated workflow requires real torch/safetensors."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_neural_resume as resume

@unittest.skipUnless(importlib.util.find_spec("torch") and importlib.util.find_spec("safetensors"),
                     "Requires PyTorch and safetensors")
class ResumeTests(unittest.TestCase):
    def setup_run(self):
        import torch
        torch.manual_seed(51092)
        model=torch.nn.Sequential(torch.nn.Linear(4,6),torch.nn.Dropout(.3),torch.nn.Linear(6,2))
        # Simulate a frozen prefix and trainable adapter.
        for p in model[0].parameters():
            p.requires_grad_(False)
        named=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
        opt=torch.optim.AdamW([p for _,p in named],lr=.001,foreach=False)
        return model,named,opt

    def update(self,model,opt):
        import torch
        x=torch.randn(3,4)
        y=torch.randn(3,2)
        opt.zero_grad(set_to_none=True)
        loss=(model(x)-y).square().mean()
        loss.backward()
        opt.step()
        return float(loss.detach())

    def test_exact_resume_including_optimizer_moments_and_random_state(self):
        import torch
        model,named,opt=self.setup_run()
        losses=[self.update(model,opt) for _ in range(3)]
        identity=dict(base="pinned",curriculum="fixed-order",seed=51092,dtype="float32")
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"step-003"
            resume.save(path,named,opt,identity,3,losses)
            continuation=[self.update(model,opt) for _ in range(5)]
            expected=[p.detach().clone() for _,p in named]
            expected_rng=torch.get_rng_state().clone()
            expected_moments=[{k:v.clone() for k,v in opt.state[p].items()} for _,p in named]
            restored,names,optimizer=self.setup_run()
            cursor,history=resume.restore(path,names,optimizer,identity)
            self.assertEqual(cursor,3)
            self.assertEqual(history,losses)
            self.assertEqual([self.update(restored,optimizer) for _ in range(5)],continuation)
            self.assertTrue(torch.equal(torch.get_rng_state(),expected_rng))
            for i,(_,p) in enumerate(names):
                self.assertTrue(torch.equal(p,expected[i]))
                for k,v in optimizer.state[p].items():
                    self.assertTrue(torch.equal(v,expected_moments[i][k]))
            with self.assertRaises(ValueError):
                resume.save(path,names,optimizer,identity,8,losses+continuation)

    def test_invalid_checkpoint_rejected_before_mutation(self):
        import torch
        model,named,opt=self.setup_run()
        losses=[self.update(model,opt)]
        identity={"base":"pinned","order":"abc"}
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"step"
            resume.save(path,named,opt,identity,1,losses)
            model2,names,optimizer=self.setup_run()
            before=[p.detach().clone() for _,p in names]
            rng=torch.get_rng_state().clone()
            def rejected(expected):
                with self.assertRaises(ValueError):
                    resume.restore(path,names,optimizer,expected)
                self.assertFalse(optimizer.state)
                self.assertTrue(torch.equal(rng,torch.get_rng_state()))
                for value,(_,param) in zip(before,names):
                    self.assertTrue(torch.equal(value,param))
            rejected({"base":"pinned","order":"different"})
            manifest=path/"manifest.json"
            original=json.loads(manifest.read_text())
            for key,value in (("completed_steps",2),("losses",[]),("parameter_names",["bad"]),
                              ("torch_version","different"),("threads",-1),("state_sha256","bad")):
                m=dict(original);m[key]=value
                manifest.write_text(json.dumps(m))
                rejected(identity)
            manifest.write_text(json.dumps(original))
            with (path/"state.safetensors").open("ab") as f:
                f.write(b"tamper")
            rejected(identity)

    def test_rehashed_invalid_moments_and_unexpected_tensors(self):
        import torch
        from safetensors.torch import load_file,save_file
        model,named,opt=self.setup_run()
        losses=[self.update(model,opt)]
        identity={"base":"pinned"}
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"step"
            resume.save(path,named,opt,identity,1,losses)
            sf=path/"state.safetensors"
            original=load_file(str(sf))
            manifest=path/"manifest.json"
            m=json.loads(manifest.read_text())
            for key,value in (("0.exp_avg_sq",torch.full_like(original["0.exp_avg_sq"],-1)),
                              ("0.step",torch.tensor(2.)),("0.weight",torch.full_like(original["0.weight"],float("nan"))),
                              ("unexpected",torch.tensor(0.))):
                data=dict(original);data[key]=value
                save_file(data,str(sf))
                m["state_sha256"]=resume.digest(sf)
                manifest.write_text(json.dumps(m))
                _,names,optimizer=self.setup_run()
                before=[p.detach().clone() for _,p in names]
                with self.assertRaises(ValueError):
                    resume.restore(path,names,optimizer,identity)
                self.assertFalse(optimizer.state)
                self.assertTrue(all(torch.equal(v,p) for v,(_,p) in zip(before,names)))

    def test_optimizer_contract_and_cursor(self):
        import torch
        model,named,opt=self.setup_run()
        self.update(model,opt)
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"step"
            for cursor,losses in ((0,[]),(2,[1.]),(1,[float("inf")]),(True,[1.])):
                with self.assertRaises(ValueError):
                    resume.save(path,named,opt,{"base":"pinned"},cursor,losses)
            with self.assertRaises(ValueError):
                resume.save(path,list(reversed(named)),opt,{"base":"pinned"},1,[1.])
            with self.assertRaises(ValueError):
                resume.save(path,named,opt,{"base":"pinned"},2,[1.,2.])
            wrong=torch.optim.AdamW([p for _,p in named],lr=.001)
            with self.assertRaises(ValueError):
                resume.contract(named,wrong)
            self.assertFalse(path.exists())

if __name__=="__main__":
    unittest.main()
