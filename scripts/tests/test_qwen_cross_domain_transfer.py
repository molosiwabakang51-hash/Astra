import itertools
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_cross_domain_transfer as p

class CrossDomainTests(unittest.TestCase):
    def test_independent_truth_table_oracle(self):
        inputs=list(itertools.product((0,1),repeat=2))
        tables=((0,0,0,1),(0,1,1,1),(0,1,1,0),(1,0,0,1))
        for values in itertools.product((-1,0,1),repeat=4):
            obs=[(*inputs[i],v) for i,v in enumerate(values) if v!=-1]
            valid=[t for t in tables if all(v==-1 or t[i]==v for i,v in enumerate(values))]
            for i,query in enumerate(inputs):
                answers={t[i] for t in valid}
                expected="YES" if answers=={1} else "NO" if answers=={0} else "UNKNOWN"
                self.assertEqual(p.induction_answer(obs,query),expected)

    def test_independent_abduction_oracle(self):
        signatures={"JAM":"101","LEAK":"011","POWER":"110"}
        for state in itertools.product("01?",repeat=3):
            compatible=[k for k,v in signatures.items() if all(a=="?" or a==b for a,b in zip(state,v))]
            obs=[(i,int(v)) for i,v in enumerate(state) if v!="?"]
            self.assertEqual(p.abduction_answer(obs),compatible[0] if len(compatible)==1 else "UNKNOWN")

    def test_deduction_two_possible_worlds(self):
        worlds=("1010","0101")
        for state in itertools.product("01?",repeat=4):
            compatible=[w for w in worlds if all(a=="?" or a==b for a,b in zip(state,w))]
            facts=[(k,int(v)) for k,v in zip("ABCD",state) if v!="?"]
            for i,k in enumerate("ABCD"):
                values={w[i] for w in compatible}
                self.assertEqual(p.deduction_answer(facts,k),"YES" if values=={"1"} else "NO" if values=={"0"} else "UNKNOWN")

    def test_suite_expected_labels_and_actual_history(self):
        suite=p.suite()
        labels={"induction":["YES","NO","UNKNOWN","YES"],"abduction":["JAM","LEAK","POWER","UNKNOWN"],
            "deduction":["YES","NO","UNKNOWN","YES"],"multiturn":["WEST","UNKNOWN","NORTH","SOUTH"]}
        self.assertEqual(len(suite),32)
        self.assertEqual(p.digest(suite),p.digest(p.suite()))
        for item in suite:
            self.assertEqual(item.answer,labels[item.domain][int(item.ident.rsplit("-",1)[1])])
            self.assertEqual([r for r,_ in item.turns],["user","assistant","user"] if item.domain=="multiturn" else ["user"])
        self.assertEqual(p.record_answer((("R","A"),("R",None),("S","B")),"R"),"UNKNOWN")
        self.assertEqual(p.record_answer((("R","A"),("R",None),("R","C")),"R"),"C")

    def test_generation_prefix_never_adds_answer(self):
        class Tok:
            def apply_chat_template(self,messages,**kw):
                self.messages=messages
                self.kw=kw
                return [1,2]
        tok=Tok()
        for item in p.suite():
            self.assertEqual(p.prompt(tok,item),[1,2])
            self.assertEqual(tok.messages,[{"role":"system","content":p.SYSTEM}]+[
                {"role":r,"content":t} for r,t in item.turns])
            self.assertEqual(tok.kw,dict(tokenize=True,add_generation_prompt=True,enable_thinking=False))

    def test_question_level_robustness_and_invalid_rows(self):
        suite=p.suite()
        rows=[dict(ident=e.ident,form=e.form,domain=e.domain,expected=e.answer,
            complete=True,correct=e.form==0,prompt_sha256=f"{e.ident}/{e.form}") for e in suite]
        a=p.summarize(rows,suite)["all"]
        self.assertEqual(a,dict(questions=16,prompts=32,completed=32,correct_prompts=16,both_correct=0,any_correct=16))
        better=[dict(r,correct=True) for r in rows]
        self.assertEqual(p.paired(rows,better),dict(prompt_gains=16,prompt_losses=0,robust_gains=16,robust_losses=0))
        for bad in (rows[:-1],rows+[rows[0]],[dict(rows[0],expected="BAD")]+rows[1:],
                    [dict(rows[0],complete=False)]+rows[1:]):
            with self.assertRaises(ValueError):
                p.summarize(bad,suite)
        with self.assertRaises(ValueError):
            p.paired(rows,[dict(better[0],prompt_sha256="changed")]+better[1:])

if __name__=="__main__":
    unittest.main()
