import pathlib
import sys
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"quality"))
from qwen_rationale_retention import score,summarize,paired
from qwen_full_answer_retention import cases

class RetentionTest(unittest.TestCase):
    def test_bare_and_trace_are_distinct(self):
        for c in cases():
            for alias in c.aliases:
                self.assertEqual(score(alias,c,True),(True,True))
                self.assertEqual(score("Reason: a derivation\nAnswer: "+alias,c,True),(False,True))
                self.assertEqual(score(alias,c,False),(False,False))

    def test_reject_substrings_and_conflicting_labels(self):
        c=cases()[0]
        for raw in ("not evaporation","Answer: evaporation\nAnswer: condensation",
                    "Answer: evaporation\nextra","Answer : evaporation",
                    "Answer: evaporation and condensation","<|im_start|>assistant\nAnswer: evaporation",
                    "<think>guess</think>\nAnswer: evaporation"):
            self.assertEqual(score(raw,c,True),(False,False),raw)

    def test_paired_separates_format_regression_from_content_loss(self):
        def row(i,strict,content):
            return dict(ident=str(i),domain="knowledge",prompt_sha256=str(i),
                        complete=True,correct=strict,content_correct=content,reference_nll=1.)
        a=[row(1,True,True),row(2,True,True),row(3,False,False)]
        b=[row(1,False,True),row(2,False,False),row(3,True,True)]
        self.assertEqual(paired(a,b)["strict"],dict(preserved=0,lost=2,gained=1,neither=0))
        self.assertEqual(paired(a,b)["content"],dict(preserved=1,lost=1,gained=1,neither=0))
        self.assertEqual(summarize(b)["content"]["all"]["correct"],2)
        with self.assertRaises(ValueError): paired(a,b[:-1])
        with self.assertRaises(ValueError): summarize([row(1,True,False)])
        with self.assertRaises(ValueError): paired(a,[dict(b[0],prompt_sha256="wrong"),*b[1:]])
        with self.assertRaises(ValueError): summarize([dict(b[0],complete=False)])
        with self.assertRaises(ValueError): summarize([dict(b[0],reference_nll=float("nan"))])

if __name__=="__main__":
    unittest.main()
