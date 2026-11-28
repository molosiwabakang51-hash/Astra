import unittest
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_gsm8k_curriculum as data
import train_qwen_gsm8k as runner

class Tokenizer:
    eos_token_id=999
    def apply_chat_template(self,messages,**kwargs):
        assert [m["role"] for m in messages]==["system","user"]
        assert kwargs==dict(tokenize=True,add_generation_prompt=True,enable_thinking=False)
        return list(messages[-1]["content"].encode())
    def encode(self,text,add_special_tokens=False):
        assert not add_special_tokens
        return list(text.encode())

def row(i):
    return {"question":f"At position {i}, how many items are there?",
            "answer":f"There are {i} items. <<{i}+0={i}>>\n#### {i}"}

class PublicMathTests(unittest.TestCase):
    def test_dataset_solution_parser_and_numeric_grade(self):
        r=data.parse(row(12))
        self.assertEqual(r.answer,"12")
        self.assertNotIn("<<",r.rationale)
        self.assertTrue(data.grade("Answer: 1,200.00","1200"))
        self.assertTrue(data.grade("Calculation\nAnswer: -0.25","-0.250"))
        self.assertTrue(data.grade("12","12"))
        self.assertTrue(data.grade("<think>not visible</think>\nAnswer: 12","12"))
        for bad in ("12 items","Answer: 12\nAdditional text","Answer: 13\nAnswer: 12",
                    "Answer: 12.0.0","Answer: 1,2","Answer: 12/1",
                    "<think>unfinished Answer: 12","The answer is 12."):
            self.assertFalse(data.grade(bad,"12"),bad)
        for bad in ("NaN","Infinity","1e3","1,23","12/3"):
            with self.assertRaises(ValueError):
                data.parse({"question":"q","answer":"r\n#### "+bad})

    def test_sample_order_reproducible_and_disjoint_from_entire_test_split(self):
        tr=[row(i) for i in range(50)]
        te=[row(i) for i in range(40,65)]
        a,b=data.select(tr,te,Tokenizer(),16,8)
        c,d=data.select(list(reversed(tr)),list(reversed(te)),Tokenizer(),16,8)
        self.assertEqual((a,b),(c,d))
        self.assertFalse({p.ident for p in a}&{data.parse(r).ident for r in te})
        self.assertEqual(len(a),16)
        self.assertEqual(len(b),8)
        # A change only to test solutions must not change selected question IDs.
        changed=[dict(r,answer="A valid alternative explanation.\n#### 999") for r in te]
        _,e=data.select(tr,changed,Tokenizer(),16,8)
        self.assertEqual([p.ident for p in b],[p.ident for p in e])
        self.assertNotEqual(data.digest(b),data.digest(e))

    def test_conflicting_duplicate_and_length_fail_closed(self):
        a=row(1)
        b={"question":"  "+a["question"].upper()+"  ","answer":"Different.\n#### 9"}
        with self.assertRaises(ValueError):
            data.unique([a,b])
        with self.assertRaises(RuntimeError):
            data.select([row(1)],[row(1)],Tokenizer(),1,1)

    def test_no_teacher_answer_in_prefix_and_exact_supervised_suffix(self):
        p=data.parse(row(99))
        ids,labels,prefix=data.encode(Tokenizer(),p)
        self.assertEqual(prefix,list(p.question.encode()))
        self.assertEqual(ids[:len(prefix)],prefix)
        self.assertEqual(labels,[-100]*len(prefix)+ids[len(prefix):])
        self.assertEqual(ids[-1],999)
        self.assertEqual(bytes(ids[len(prefix):-1]).decode(),p.rationale+"\nAnswer: 99")

    def test_batch_completion_and_paired_denominator(self):
        self.assertEqual(runner.padded([[1,2],[3]],99),([[1,2],[99,3]],[[1,1],[0,1]]))
        self.assertEqual(runner.completion([1,99,99],99),([1,99],True))
        self.assertEqual(runner.completion([1,2],99),([1,2],False))
        with self.assertRaises(ValueError):
            runner.completion([99,2],99)
        a=[dict(ident=str(i),prompt_sha256=str(i),correct=i<10,complete=True,tokens=5) for i in range(24)]
        b=[dict(r,correct=i>=10) for i,r in enumerate(a)]
        self.assertEqual(runner.compare(a,b),dict(gains=14,losses=10))
        self.assertEqual(runner.summarize(a)["correct"],10)
        with self.assertRaises(ValueError):
            runner.compare(a,b[:-1])
        with self.assertRaises(ValueError):
            runner.summarize(a[:-1])

if __name__=="__main__":
    unittest.main()
