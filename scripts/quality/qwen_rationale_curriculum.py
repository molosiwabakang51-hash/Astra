"""Public synthetic teacher traces; no private user data or external teacher model.

The same question/answer pairs are used in both supervision arms. One arm
receives short verified intermediate derivations, the other the final answer.
"""
import random
import re
from qwen_neural_curriculum import Example, controls, digest

SEED=3820171
DOMAINS=("quantity","logic","causal","abstraction","grounding","uncertainty")
SYSTEM=("Solve the current problem using the stated information. You may give a brief derivation. "
        "End with a separate line Answer: VALUE. If information is insufficient, use UNKNOWN.")

def curriculum(train=True, per_domain=16):
    if type(per_domain) is not int or not 1<=per_domain<=64:
        raise ValueError("Invalid curriculum size")
    rng=random.Random(SEED if train else SEED+1)
    examples=[]
    for i in range(per_domain):
        a=20+i*7 if train else 301+i*13
        b,c,d=rng.sample(range(2,18),3)
        if train:
            q=f"A depot starts with {a} items. It receives {b}, ships {c}, and receives {d}. What is the ending count?"
            r=f"{a}+{b}={a+b}; {a+b}-{c}={a+b-c}; {a+b-c}+{d}={a+b-c+d}."
            ans=a+b-c+d
        else:
            q=f"A store ends with {a} items after adding {b}, losing {c}, then adding {d}. What was the starting count?"
            ans=a-d+c-b
            r=f"Reverse the last addition: {a}-{d}={a-d}; reverse the loss and first addition: {a-d}+{c}-{b}={ans}."
        examples.append(Example("quantity",q,r,str(ans)))

        # Explicitly named predicates avoid external factual assumptions.
        names=(f"tav{i}",f"mep{i}",f"zul{i}") if train else (f"ren{i}",f"vos{i}",f"kip{i}")
        x,y,z=names
        if i%3==0:
            q=f"Every {x} is a {y}; every {y} is a {z}. Object K is a {x}. Must K be a {z}? YES, NO or UNKNOWN."
            r=f"K is a {x}, hence a {y}, hence a {z}."; ans="YES"
        elif i%3==1:
            q=f"Every {x} is a {y}; every {y} is a {z}. K is not a {z}. Is K a {x}? YES, NO or UNKNOWN."
            r=f"Being a {x} implies being a {z}, which contradicts the given observation."; ans="NO"
        else:
            q=f"Every {x} is a {y}; every {y} is a {z}. K is a {z}. Is K a {x}? YES, NO or UNKNOWN."
            r=f"A {z} need not be a {y} or a {x}; both possibilities remain."; ans="UNKNOWN"
        examples.append(Example("logic",q,r,ans))

        # Training AND/XOR; transfer OR/XOR with a different dependency.
        u,v=i%2,(i//2)%2
        force=((i//4)+(i%2))%2
        if train:
            q=f"Device {i}: U={u}, V={v}. R=U AND V; S=R XOR U. Override R to {force}. What is S, 0 or 1?"
            ans=force^u
            r=f"The override sets R={force}; S={force} XOR {u}={ans}."
        else:
            q=f"System {i}: A={u}, B={v}. X=A OR B; Y=X XOR B. Force X={force}, keeping B unchanged. What is Y, 0 or 1?"
            ans=force^v
            r=f"X becomes {force}; Y={force} XOR {v}={ans}."
        examples.append(Example("causal",q,r,str(ans)))

        x,y=(i+2,rng.randrange(4,30)) if train else (i+71,rng.randrange(32,70))
        if train:
            q=f"Map (x,y) to (x+y,y-x) simultaneously. From ({x},{y}), apply it twice. What is the final first coordinate?"
            p,qv=x+y,y-x
            ans=p+qv
            r=f"First ({p},{qv}); second first coordinate is {p}+{qv}={ans}."
        else:
            q=f"Map (x,y) to (y,x+y) simultaneously. From ({x},{y}), apply it three times. What is the final second coordinate?"
            p,qv=y,x+y
            p2,q2=qv,p+qv
            ans=p2+q2
            r=f"First ({p},{qv}); second ({p2},{q2}); third second coordinate {p2}+{q2}={ans}."
        examples.append(Example("abstraction",q,r,str(ans)))

        old=100+i*7 if train else 501+i*11
        new=old+b
        if train:
            p1,p2,p3=rng.sample(("A","B","C","D","E"),3)
            q=f"Ticket {old} belongs to room {p1}. Ticket {old+1} belongs to {p2}. Ticket {old} is transferred to {p3}. What room holds ticket {old}?"
            r=f"The later transfer changes the requested ticket to {p3}, not the other ticket."; ans=p3
        else:
            p1,p2,p3,p4=rng.sample(("J","K","L","M","N"),4)
            q=f"Crate {old} is in zone {p1}. Crate {old+1} moves to zone {p2}. Later crate {old} moves to zone {p3}, then zone {p4}. Where is crate {old} now?"
            r=f"Only transfers of the requested crate count; the last transfer is to {p4}."; ans=p4
        examples.append(Example("grounding",q,r,ans))

        if train:
            q=f"The initial estimate was {old} units. " + (f"It is replaced with a confirmed value of {new}." if i%2==0 else "It is withdrawn without a replacement.")+" What is the current confirmed value, or UNKNOWN?"
        else:
            q=f"A record lists {old} units. That figure is invalidated. " + (f"A new confirmed value is {new}." if i%2==0 else "No later value is supplied.")+" Give the current confirmed value, or UNKNOWN."
        r="The confirmed replacement is authoritative." if i%2==0 else "There is no valid value after withdrawal."
        examples.append(Example("uncertainty",q,r,str(new) if i%2==0 else "UNKNOWN"))
    if len({e.question for e in examples})!=len(examples):
        raise RuntimeError("Duplicate question")
    rng.shuffle(examples)
    return tuple(examples)

def completion(example, rationale):
    return ("Reason: "+example.rationale+"\n" if rationale else "")+"Answer: "+example.answer

def encode(tokenizer, example, rationale, maximum=384):
    prefix=tokenizer.apply_chat_template(
        [{"role":"system","content":SYSTEM},{"role":"user","content":example.question}],
        tokenize=True,add_generation_prompt=True,enable_thinking=False)
    if not prefix or tokenizer.eos_token_id is None:
        raise ValueError("Missing generation prefix/end token")
    suffix=tokenizer.encode(completion(example,rationale),add_special_tokens=False)+[tokenizer.eos_token_id]
    if len(prefix)+len(suffix)>maximum:
        raise ValueError("Example exceeds token budget")
    return prefix+suffix,[-100]*len(prefix)+suffix,prefix

def grade(text, expected):
    """Exact terminal answer or exact bare answer; no arbitrary number mining.

    Allows multi-line explanations but rejects multiple Answer: lines or
    anything after the terminal answer. Does not certify the explanation.
    """
    if not isinstance(text,str):
        return False,False
    clean=text.strip()
    lines=clean.splitlines()
    matches=[(i,re.fullmatch(r"Answer:\s*([^\r\n]+)",line,re.I))
             for i,line in enumerate(lines) if re.match(r"^\s*Answer\s*:",line,re.I)]
    if not matches:
        return clean.casefold()==expected.casefold(),False
    if len(matches)!=1 or matches[0][0]!=len(lines)-1 or matches[0][1] is None:
        return False,False
    return matches[0][1].group(1).strip().casefold()==expected.casefold(),True
