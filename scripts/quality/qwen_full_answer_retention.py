"""Public development-only retention cases, distinct from the cloze anchors.

Full-answer exact/explicit alias grading is deliberately conservative.
Reference continuation NLL is measured separately from free generation.
Neither metric is an independent intelligence or factual accuracy rating.
"""
from dataclasses import dataclass
import hashlib
import json
import re
import unicodedata

SYSTEM="Answer the question directly with only the requested short answer. Do not add an explanation."

@dataclass(frozen=True)
class Case:
    ident: str
    domain: str
    question: str
    aliases: tuple

RAW=(
    ("knowledge","What is the name of the process by which a liquid becomes a gas at its surface below its boiling point?",("evaporation",)),
    ("knowledge","What is the name of the layer of gases surrounding Earth?",("atmosphere","the atmosphere")),
    ("knowledge","Which country has the city of Buenos Aires as its national capital?",("Argentina",)),
    ("knowledge","What is the common name for the gas with chemical formula CO2?",("carbon dioxide",)),
    ("knowledge","What is the name of the force opposing relative sliding of two contacting surfaces?",("friction",)),
    ("knowledge","What instrument has keys, hammers and strings and is commonly used in classical music?",("piano","a piano")),
    ("knowledge","Which continent contains the country of Peru?",("South America",)),
    ("knowledge","What is the process of changing a solid directly into a gas called?",("sublimation",)),
    ("language","Translate the English noun 'house' into French. Give only the noun without an article.",("maison",)),
    ("language","Translate the English adjective 'white' into Spanish in the masculine singular.",("blanco",)),
    ("language","Translate the English noun 'water' into German, without an article.",("Wasser",)),
    ("language","Translate the English noun 'thank you' as the ordinary Russian expression.",("спасибо",)),
    ("language","What is the English past participle of 'break'?",("broken",)),
    ("language","Give the English plural of 'tooth'.",("teeth",)),
    ("language","Give a single English antonym of 'permanent'.",("temporary","impermanent","transient")),
    ("language","What is the English superlative form of 'bad'?",("worst",)),
    ("arithmetic","What is 17 multiplied by 6?",("102",)),
    ("arithmetic","What is 225 divided by 15?",("15",)),
    ("arithmetic","What is three quarters of 84?",("63",)),
    ("arithmetic","What is 12 percent of 250?",("30",)),
    ("arithmetic","What is the smallest positive integer divisible by both 6 and 8?",("24",)),
    ("arithmetic","Convert 2.5 hours to minutes. Give the number only.",("150",)),
    ("arithmetic","What is 14 squared minus 20?",("176",)),
    ("arithmetic","What is the median of 3, 7, 10, 14 and 20?",("10",)),
    ("grounding","A notice says the green line terminates at Oak Street, but the blue line terminates at River Park. Where does the blue line terminate?",("River Park",)),
    ("grounding","A recipe requires two apples and one pear. The cook replaces the pear with a peach. Which fruit replaces the pear?",("peach","a peach")),
    ("grounding","A message says the first appointment is at 9 and the second at 11. The first is cancelled. At what hour is the remaining appointment?",("11","11 am","11 a.m.")),
    ("grounding","The file called East is archived. The file called West is active. Which file is active?",("West",)),
    ("grounding","Rita has a red pen, then trades it for a black one. What colour is her pen after the trade?",("black",)),
    ("grounding","A catalogue lists item H as out of stock and item G as available. Which item is available?",("G","item G")),
    ("grounding","The original delivery address was Cedar Lane. The confirmed replacement is Pine Road. What is the current address?",("Pine Road",)),
    ("grounding","The passage says a bird landed on a fence but gives no species. What species was the bird? Reply UNKNOWN if unspecified.",("UNKNOWN",)),
)

def cases():
    result=tuple(Case(f"ret-{i:02d}",domain,q,answers) for i,(domain,q,answers) in enumerate(RAW))
    if len(result)!=32 or len({r.question for r in result})!=32:
        raise RuntimeError("Invalid retention suite")
    return result

def normalized(text):
    if not isinstance(text,str):
        return ""
    # Only remove one terminal sentence punctuation mark; do not strip
    # mathematical signs, infer missing content or search arbitrary substrings.
    s=" ".join(unicodedata.normalize("NFC",text).casefold().split())
    return s[:-1].rstrip() if s.endswith(".") else s

def grade(text,case):
    return bool(normalized(text)) and normalized(text) in {normalized(x) for x in case.aliases}

def suite_digest(suite):
    return hashlib.sha256(json.dumps([r.__dict__ for r in suite],sort_keys=True,
        ensure_ascii=False,separators=(",",":")).encode()).hexdigest()

def prefix(tokenizer,case):
    ids=tokenizer.apply_chat_template([{"role":"system","content":SYSTEM},
        {"role":"user","content":case.question}],tokenize=True,add_generation_prompt=True,enable_thinking=False)
    if not ids or len(ids)>256:
        raise ValueError("Invalid retention prefix")
    return ids

def summarize(rows):
    import math
    if not rows or len({r["ident"] for r in rows})!=len(rows) or any(
        not math.isfinite(r["reference_nll"]) or r["reference_nll"]<0 or
        type(r["correct"]) is not bool or type(r["complete"]) is not bool or
        (r["correct"] and not r["complete"]) for r in rows):
        raise ValueError("Invalid retention records")
    def agg(rr):
        return dict(attempted=len(rr),complete=sum(r["complete"] for r in rr),
            correct=sum(r["correct"] for r in rr),
            mean_reference_nll=sum(r["reference_nll"] for r in rr)/len(rr))
    return {"all":agg(rows),**{d:agg([r for r in rows if r["domain"]==d])
                for d in sorted({r["domain"] for r in rows})}}

def paired(base,adapted):
    a={r["ident"]:r for r in base}; b={r["ident"]:r for r in adapted}
    if not a or len(a)!=len(base) or len(b)!=len(adapted) or a.keys()!=b.keys() or any(
            a[k]["domain"]!=b[k]["domain"] or a[k]["prompt_sha256"]!=b[k]["prompt_sha256"] for k in a):
        raise ValueError("Unmatched retention cases")
    return dict(preserved=sum(a[k]["correct"] and b[k]["correct"] for k in a),
        lost=sum(a[k]["correct"] and not b[k]["correct"] for k in a),
        gained=sum(not a[k]["correct"] and b[k]["correct"] for k in a),
        neither=sum(not a[k]["correct"] and not b[k]["correct"] for k in a))
