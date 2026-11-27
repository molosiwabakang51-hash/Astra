import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import evaluate_qwen_rationale_checkpoints as recovery

class RecoveryTests(unittest.TestCase):
    def test_left_padding_preserves_prompt_and_masks(self):
        ids,masks=recovery.padded_batch([[9,8],[7],[6,5,4]],99)
        self.assertEqual(ids,[[99,9,8],[99,99,7],[6,5,4]])
        self.assertEqual(masks,[[0,1,1],[0,0,1],[1,1,1]])
        for row,mask,original in zip(ids,masks,[[9,8],[7],[6,5,4]]):
            self.assertEqual([x for x,m in zip(row,mask) if m],original)
        for bad in ([],[[]],[[1],[]]):
            with self.assertRaises(ValueError):
                recovery.padded_batch(bad,99)

    def test_eos_padding_and_completion_status(self):
        self.assertEqual(recovery.completion_tokens([1,2,99,99,99],99),([1,2,99],True))
        self.assertEqual(recovery.completion_tokens([99,99],99),([99],True))
        self.assertEqual(recovery.completion_tokens([1,2,3],99),([1,2,3],False))
        for bad in ([],[1,99,2],[1]*97):
            with self.assertRaises(ValueError):
                recovery.completion_tokens(bad,99)

    @unittest.skipUnless(importlib.util.find_spec("torch") and importlib.util.find_spec("safetensors"),
                         "Requires pinned tensor dependencies in dedicated workflow")
    def test_unpinned_checkpoint_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            with self.assertRaises(ValueError):
                recovery.recover(root,"rationale",{}, {})
            (root/"manifest.json").write_text("{}")
            (root/"state.safetensors").write_bytes(b"bad")
            with self.assertRaisesRegex(ValueError,"Unpinned"):
                recovery.recover(root,"rationale",{}, {})

if __name__=="__main__":
    unittest.main()
