import re
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_rationale_curriculum as data
import train_qwen_rationale_transfer as trainer

class RationaleTests(unittest.TestCase):
    def test_balanced_disjoint_and_reproducible(self):
        train,dev=data.curriculum(True,16),data.curriculum(False,8)
        self.assertEqual(len(train),96)
        self.assertEqual(len(dev),48)
        self.assertEqual(train,data.curriculum(True,16))
        self.assertEqual(dev,data.curriculum(False,8))
        self.assertFalse({e.question for e in train}&{e.question for e in dev})
        for suite,n in ((train,16),(dev,8)):
            for d in data.DOMAINS:
                self.assertEqual(sum(e.domain==d for e in suite),n)
            self.assertEqual({e.answer for e in suite if e.domain=="causal"},{"0","1"})
            self.assertGreater(len({e.answer for e in suite if e.domain=="grounding"}),2)
        for n in (0,65,True):
            with self.assertRaises(ValueError):
                data.curriculum(True,n)

    def test_independent_answer_oracles(self):
        for train in (True,False):
            for e in data.curriculum(train,64):
                q=e.question
                if e.domain=="quantity":
                    a,b,c,d=map(int,re.findall(r"\d+",q))
                    self.assertEqual(e.answer,str(a+b-c+d if train else a-b+c-d))
                elif e.domain=="causal":
                    if train:
                        u,v,forced=map(int,re.search(r"U=(\d), V=(\d).*Override R to (\d)",q).groups())
                        result=forced!=u
                    else:
                        a,b,forced=map(int,re.search(r"A=(\d), B=(\d).*Force X=(\d)",q).groups())
                        result=forced!=b
                    self.assertEqual(e.answer,str(int(result)))
                elif e.domain=="abstraction":
                    x,y=map(int,re.search(r"From \((\d+),(\d+)\)",q).groups())
                    for _ in range(2 if train else 3):
                        x,y=(x+y,y-x) if train else (y,x+y)
                    self.assertEqual(e.answer,str(x if train else y))
                elif e.domain=="logic":
                    expected="YES" if "Must K be" in q else "NO" if "K is not a" in q else "UNKNOWN"
                    self.assertEqual(e.answer,expected)
                elif e.domain=="grounding":
                    expected=re.search(r"transferred to ([A-Z])\." if train else r"then zone ([A-Z])\.",q).group(1)
                    self.assertEqual(e.answer,expected)
                else:
                    values=re.findall(r"\d+",q)
                    self.assertEqual(e.answer,values[-1] if len(values)==2 else "UNKNOWN")

    def test_matched_prompt_and_assistant_only_loss_mask(self):
        class Tokenizer:
            eos_token_id=99
            def apply_chat_template(self,messages,**kwargs):
                self.messages=messages
                assert kwargs==dict(tokenize=True,add_generation_prompt=True,enable_thinking=False)
                return [10,11,12]
            def encode(self,text,add_special_tokens):
                assert add_special_tokens is False
                return list(text.encode())
        t=Tokenizer()
        for e in data.curriculum(True,16):
            a,b=data.encode(t,e,False),data.encode(t,e,True)
            self.assertEqual(a[2],b[2])
            self.assertEqual(t.messages[-1]["content"],e.question)
            for ids,labels,prefix in (a,b):
                self.assertEqual(labels,[-100]*len(prefix)+ids[len(prefix):])
                self.assertEqual(ids[-1],99)
            self.assertEqual(data.completion(e,False),"Answer: "+e.answer)
            self.assertTrue(data.completion(e,True).endswith("\nAnswer: "+e.answer))
        with self.assertRaises(ValueError):
            data.encode(t,e,True,4)

    def test_strict_terminal_answer_and_output_diagnostics(self):
        self.assertEqual(data.grade("12","12"),(True,False))
        self.assertEqual(data.grade("Answer: 12","12"),(True,True))
        self.assertEqual(data.grade("Reason: step 1.\nStep 2.\nAnswer: 12","12"),(True,True))
        self.assertEqual(data.grade("Reason: 12.\nAnswer: 13","12"),(False,True))
        for text in ("The answer is 12.","Answer: 12\nMore text","Answer: 13\nAnswer: 12","Answer : 12"):
            self.assertEqual(data.grade(text,"12"),(False,False))
        self.assertEqual(trainer.visible("<think>unfinished"),"")
        self.assertEqual(trainer.visible("<think>private</think>\nAnswer: 12"),"Answer: 12")

    def test_fixed_paired_denominator(self):
        a=[dict(ident="1",domain="logic",prompt_sha256="p",correct=True)]
        b=[dict(a[0],correct=False)]
        self.assertEqual(trainer.paired(a,b)["regressed"],1)
        for bad in ([],b+b,[dict(b[0],prompt_sha256="changed")]):
            with self.assertRaises(ValueError):
                trainer.paired(a,bad)

if __name__=="__main__":
    unittest.main()
