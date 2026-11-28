import pathlib
import sys
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"quality"))
from evaluate_qwen_rationale_gguf import shapes,check_adapters,score_reply,CAP

class NativeTransferTests(unittest.TestCase):
    def test_conversion_base_requires_exact_shards(self):
        import hashlib
        import tempfile
        from unittest.mock import patch
        import evaluate_qwen_rationale_gguf as gguf
        with tempfile.TemporaryDirectory() as tmp:
            root=pathlib.Path(tmp)/gguf.REVISION
            root.mkdir()
            pins={"a.safetensors":hashlib.sha256(b"a").hexdigest(),
                  "b.safetensors":hashlib.sha256(b"b").hexdigest()}
            with patch.object(gguf,"BASE_HASHES",pins):
                with self.assertRaises(RuntimeError):gguf.verify_conversion_base(root)
                (root/"a.safetensors").write_bytes(b"a")
                with self.assertRaises(RuntimeError):gguf.verify_conversion_base(root)
                (root/"b.safetensors").write_bytes(b"b")
                self.assertEqual(gguf.verify_conversion_base(root),pins)
                (root/"b.safetensors").write_bytes(b"changed")
                with self.assertRaises(RuntimeError):gguf.verify_conversion_base(root)
                (root/"b.safetensors").write_bytes(b"b")
                (root/"extra.safetensors").write_bytes(b"extra")
                with self.assertRaises(RuntimeError):gguf.verify_conversion_base(root)
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
