import pathlib
import sys
import unittest
from itertools import product
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"quality"))
from qwen_paraphrase_supervision import suite,schedule,encode_task,digest

class CurriculumTests(unittest.TestCase):
    def test_independent_oracles(self):
        for t in (*suite(True),*suite(False)):
            o=t.oracle
            if t.domain=="induction":
                table,obs,q=o
                outputs={row[q] for row in table if not any(row[j]!=v for j,v in obs)}
                expected="YES" if outputs=={1} else "NO" if outputs=={0} else "UNKNOWN"
            elif t.domain=="abduction":
                names,patterns,obs=o
                compatible={name for name,row in zip(names,patterns) if sum(row[j]!=v for j,v in obs)==0}
                expected=next(iter(compatible)) if len(compatible)==1 else "UNKNOWN"
            elif t.domain=="deduction":
                parity,facts=o
                values=set()
                for bits in product([False,True],repeat=3):
                    if (bits[0]^bits[1]^bits[2])==bool(parity) and all(bits[j]==bool(v) for j,v in facts):
                        values.add(bits[2])
                expected="YES" if values=={True} else "NO" if values=={False} else "UNKNOWN"
            else:
                initial,events,target=o
                v=next(v for key,v in reversed(initial+events) if key==target)
                expected=v if v is not None else "UNKNOWN"
            self.assertEqual(t.answer,expected,t.ident)

    def test_splits_and_matched_schedule(self):
        train,dev=suite(True),suite(False)
        self.assertEqual((len(train),len(dev)),(32,16))
        self.assertEqual(digest(train),digest(suite(True)))
        self.assertNotEqual(digest(train),digest(dev))
        a={p.turns for t in train for p in t.probes()}
        b={p.turns for t in dev for p in t.probes()}
        self.assertEqual((len(a),len(b)),(64,32))
        self.assertFalse(a&b)
        control,candidate=schedule(train,False),schedule(train,True)
        self.assertEqual(len(control),64)
        self.assertEqual([t for t,_ in control],[t for t,_ in candidate])
        self.assertEqual([f for _,f in control],[0]*64)
        self.assertEqual([f for _,f in candidate],[0,1]*32)
        for t in (*train,*dev):
            p,q=t.probes()
            self.assertEqual(p.answer,q.answer)
            self.assertNotEqual(p.turns,q.turns)

    def test_target_suffix_and_message_roles(self):
        class Tokenizer:
            eos_token_id=1000
            def apply_chat_template(self,messages,**kwargs):
                self.messages=messages
                return [1,2,3] if messages[-1]["content"].startswith("Set") else [4,5]
            def encode(self,s,**kwargs): return [ord(c) for c in s]
            def __call__(self,s,**kwargs):
                return {"input_ids":self.encode(s),"offset_mapping":[(i,i+1) for i in range(len(s))]}
        tok=Tokenizer()
        for t in suite(True):
            x,p,m=encode_task(tok,t,0)
            y,q,n=encode_task(tok,t,1)
            self.assertEqual(x[len(p):],y[len(q):])
            self.assertEqual(m,n)
            self.assertEqual(m[-1],1)
            self.assertEqual(sum(m),len(t.answer)+1)
            if t.domain=="multiturn":
                self.assertEqual([r["role"] for r in tok.messages],["system","user","assistant","user"])

if __name__=="__main__":unittest.main()
