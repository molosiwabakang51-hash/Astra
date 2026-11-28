import hashlib
import pathlib
import sys
import tempfile
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"quality"))
from qwen_weighted_budget import pinned_json,projected_short,compare_archive,SHORT,LONG

class RecoveryBudgetTests(unittest.TestCase):
    def test_boundaries(self):
        self.assertEqual(projected_short([1]*127+[2],True,2),([1]*127+[2],True))
        self.assertEqual(projected_short([1]*128+[2],True,2),([1]*128,False))
        self.assertEqual(projected_short([1]*LONG,False,2),([1]*SHORT,False))
        for tokens,done in [([],False),([1]*127,False),([2,1],True),([2]*LONG,False),([1]*LONG,True)]:
            with self.assertRaises(ValueError): projected_short(tokens,done,2)
    def test_pin(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=pathlib.Path(tmp)/"r.json"; p.write_bytes(b'{"ok":true}')
            pin=hashlib.sha256(p.read_bytes()).hexdigest()
            self.assertEqual(pinned_json(p,pin),{"ok":True})
            p.write_bytes(b'{"ok":false}')
            with self.assertRaises(ValueError): pinned_json(p,pin)
            with self.assertRaises(ValueError): pinned_json(p,pin,1)
    def test_archive(self):
        row=dict(ident="1",domain="quantity",prompt_sha256="p",expected="4",
                 complete=False,correct=False,terminal_format=False,tokens=128,output_sha256="h")
        self.assertTrue(compare_archive([row],[row])["all_reproduced"])
        self.assertFalse(compare_archive([row],[dict(row,output_sha256="changed")])["all_reproduced"])
        for invalid in [[],[row,row],[dict(row,expected="5")]]:
            with self.assertRaises(ValueError): compare_archive([row],invalid)

if __name__=="__main__": unittest.main()
