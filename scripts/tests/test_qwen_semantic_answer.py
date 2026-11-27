import importlib.util
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_semantic_answer as semantic
from qwen_contrastive_curriculum import training_cases
from qwen_transfer_choices import cases

class SemanticContracts(unittest.TestCase):
    def test_reversal_and_training_distractors(self):
        class Tokenizer:
            eos_token_id=999
            def apply_chat_template(self,messages,**kw):
                assert kw==dict(tokenize=True,add_generation_prompt=True,enable_thinking=False)
                self.messages=messages
                return [10,11,12]
            def encode(self,text,add_special_tokens):
                assert add_special_tokens is False
                return list(text.encode())
        t=Tokenizer()
        train=training_cases()
        self.assertEqual(len(train),32)
        self.assertEqual(semantic.distractors(train),semantic.distractors(train))
        self.assertTrue(all(i!=c.correct for i,c in zip(semantic.distractors(train),train)))
        for c in train+cases():
            a=semantic.prepare(t,c)
            b=semantic.prepare(t,c,reverse=True)
            self.assertEqual(a[1],list(reversed(b[1])))
            self.assertEqual(a[1][a[2]],b[1][b[2]])
            self.assertIn("Options:\n- ",t.messages[-1]["content"])
            self.assertTrue(all(s[-1]==999 for s in a[1]))
        with self.assertRaises(ValueError):
            semantic.prepare(t,train[0],maximum=4)

    def test_paired_order_scoring(self):
        rows=[]
        for c in cases():
            for order in ("original","reversed"):
                rows.append(dict(ident=c.ident,domain=c.domain,order=order,
                                 selected_semantic=c.correct,correct=True))
        report=semantic.summarize(rows)
        self.assertEqual(report["all"]["questions"],24)
        self.assertEqual(report["all"]["both_correct"],24)
        wrong=[dict(r,selected_semantic=(r["selected_semantic"]+1)%4,correct=False) for r in rows]
        report2=semantic.summarize(wrong)
        self.assertEqual(report2["all"]["consistent"],24)
        self.assertEqual(report2["all"]["both_correct"],0)
        self.assertEqual(semantic.paired(report2,report)["new_both_correct"],24)
        for bad in (rows[:-1],rows+rows[:1],[dict(rows[0],order="unknown")]+rows[1:]):
            with self.assertRaises(ValueError):
                semantic.summarize(bad)

@unittest.skipUnless(importlib.util.find_spec("torch"),"Requires PyTorch in dedicated workflow")
class TensorContracts(unittest.TestCase):
    def test_batched_scores_and_gradients_equal_unpadded_full_projection(self):
        import torch
        import torch.nn.functional as F
        from types import SimpleNamespace
        torch.manual_seed(109)
        w=torch.randn(17,17,requires_grad=True)
        def model(input_ids,attention_mask,position_ids,use_cache,logits_to_keep):
            self.assertFalse(use_cache)
            z=w[input_ids]+position_ids.unsqueeze(-1)*torch.arange(17)*0.01
            return SimpleNamespace(logits=z[:,-logits_to_keep:,:])
        for prefix in ([1],[2,4,6],[3,6,9,12]):
            for suffixes in ([[2,5],[1,3,7,5]],[[9],[4,2,1],[6,8]]):
                actual=semantic.sequence_scores(model,prefix,suffixes,0)
                expected=[]
                for s in suffixes:
                    ids=torch.tensor(prefix+s)
                    positions=torch.arange(len(ids))
                    z=w[ids]+positions.unsqueeze(-1)*torch.arange(17)*0.01
                    expected.append(-F.cross_entropy(z[len(prefix)-1:-1],torch.tensor(s)))
                expected=torch.stack(expected)
                self.assertTrue(torch.allclose(actual,expected,atol=1e-6))
                g1=torch.autograd.grad(actual.sum(),w,retain_graph=True)[0]
                g2=torch.autograd.grad(expected.sum(),w)[0]
                self.assertTrue(torch.allclose(g1,g2,atol=1e-6))

    def test_ranking_objective_gradient(self):
        import torch
        for values in ((-1.,-3.),(-4.,-1.),(-2.,-2.)):
            z=torch.tensor(values,requires_grad=True)
            ordinary=semantic.objective(z,0)
            ranked=semantic.objective(z,1)
            self.assertTrue(torch.equal(ordinary,-z[0]))
            g=torch.autograd.grad(ranked,z)[0]
            p=torch.sigmoid(torch.tensor(.5+values[1]-values[0]))
            self.assertTrue(torch.allclose(g,torch.stack((-1-p,p))))
            self.assertLess(float(g[0]),-1)
            self.assertGreater(float(g[1]),0)

    def test_nonfinite_and_malformed_inputs(self):
        import torch
        for scores,weight in ((torch.zeros(3),1),(torch.tensor([0.,float("nan")]),1),(torch.zeros(2),-1)):
            with self.assertRaises(ValueError):
                semantic.objective(scores,weight)
        with self.assertRaises(ValueError):
            semantic.sequence_scores(None,[],[[1]],0)

if __name__=="__main__":
    unittest.main()
