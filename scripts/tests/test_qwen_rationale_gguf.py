import pathlib
import sys
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"quality"))
from evaluate_qwen_rationale_gguf import shapes,check_adapters,score_reply,CAP

class NativeTransferTests(unittest.TestCase):
    def test_shapes(self):
        s=shapes()
        self.assertEqual(len(s),16)
        self.assertEqual(sum(a*b for a,b in s.values()),229376)
    def test_adapter_identity(self):
        p=pathlib.Path("candidate.gguf")
        row={"id":0,"path":str(p),"scale":1.}
        check_adapters([row],p,1.)
        for r in [[],[row,row],[dict(row,scale=0.)],[dict(row,path="wrong.gguf")],[dict(row,id=True)]]:
            with self.assertRaises(ValueError):check_adapters(r,p,1.)
    def test_native_eos_and_sampling(self):
        row=dict(content="Reason: 2+2=4.\nAnswer: 4",stop_type="eos",truncated=False,
            tokens_predicted=12,generation_settings=dict(temperature=0.,top_k=1,top_p=1.,min_p=0.,n_predict=CAP))
        self.assertTrue(score_reply(row,"4")["correct"])
        self.assertFalse(score_reply(dict(row,stop_type="limit"),"4")["correct"])
        self.assertFalse(score_reply(dict(row,truncated=True),"4")["correct"])
        self.assertFalse(score_reply(dict(row,content="Answer: 4\nAnswer: 5"),"4")["correct"])
        with self.assertRaises(ValueError):score_reply(dict(row,generation_settings={}),"4")
if __name__=="__main__":unittest.main()
