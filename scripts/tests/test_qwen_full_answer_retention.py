import importlib.util
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_full_answer_retention as suite
import evaluate_qwen_full_answer_retention as runner

class RetentionContracts(unittest.TestCase):
    def test_balanced_distinct_suite_and_explicit_aliases(self):
        rows=suite.cases()
        self.assertEqual(len(rows),32)
        for d in ("knowledge","language","arithmetic","grounding"):
            self.assertEqual(sum(r.domain==d for r in rows),8)
        for r in rows:
            for a in r.aliases:
                self.assertTrue(suite.grade(a,r))
            self.assertFalse(suite.grade("The answer is "+r.aliases[0],r))
            self.assertFalse(suite.grade(r.aliases[0]+" or another answer",r))
        self.assertFalse(suite.grade("1020",rows[16]))
        self.assertFalse(suite.grade("-102",rows[16]))
        self.assertTrue(suite.grade("  CARBON   DIOXIDE. ",rows[3]))
        self.assertEqual(suite.suite_digest(rows),suite.suite_digest(suite.cases()))

    def test_paired_preserved_lost_gained_and_neither(self):
        a=[dict(ident=str(i),domain="arithmetic",complete=True,correct=i<2,
                reference_nll=.1,prompt_sha256=str(i)) for i in range(4)]
        b=[dict(r,correct=i%2==0) for i,r in enumerate(a)]
        self.assertEqual(suite.paired(a,b),dict(preserved=1,lost=1,gained=1,neither=1))
        self.assertEqual(suite.summarize(a)["all"]["correct"],2)
        for bad in (b[:-1],b+b,[dict(b[0],prompt_sha256="wrong")]+b[1:]):
            with self.assertRaises(ValueError):
                suite.paired(a,bad)
        for bad in ([dict(a[0],reference_nll=float("nan"))],
                    [dict(a[0],complete=False)],a+a):
            with self.assertRaises(ValueError):
                suite.summarize(bad)

    def test_padding_and_eos_boundaries(self):
        self.assertEqual(runner.padded([[1,2],[3]],9),([[1,2],[9,3]],[[1,1],[0,1]]))
        self.assertEqual(runner.completion([1,9,9],9),([1,9],True))
        self.assertEqual(runner.completion([1,2],9),([1,2],False))
        for bad in ([],[1,9,2],[1]*25):
            with self.assertRaises(ValueError):
                runner.completion(bad,9)

    @unittest.skipUnless(importlib.util.find_spec("torch"),"Requires dedicated tensor workflow")
    def test_reference_nll_matches_full_shifted_projection(self):
        import torch
        import torch.nn.functional as F
        from types import SimpleNamespace
        torch.manual_seed(283)
        for n in range(1,7):
            for k in range(1,5):
                prefix=list(range(n))
                suffix=list(range(k))
                z=torch.randn(1,n+k,11)
                def model(**kw):
                    self.assertEqual(kw["logits_to_keep"],k+1)
                    self.assertEqual(kw["input_ids"].tolist(),[prefix+suffix])
                    return SimpleNamespace(logits=z[:,-(k+1):])
                expected=float(F.cross_entropy(z[0,n-1:-1],torch.tensor(suffix)))
                self.assertAlmostEqual(runner.reference_nll(model,prefix,suffix),expected,places=6)

if __name__=="__main__":
    unittest.main()
