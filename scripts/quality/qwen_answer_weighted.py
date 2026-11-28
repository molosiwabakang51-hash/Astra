"""Public compact reasoning curriculum and exact answer-token weighting.

Both objective arms consume identical sequences; only the loss weights differ.
Synthetic templates are development data, not a private assessment.
"""
import random
from qwen_neural_curriculum import Example, digest, controls
from qwen_rationale_curriculum import curriculum as prior, SYSTEM

SEED=918326
DOMAINS=("quantity","abstraction","causal","logic","grounding","uncertainty")

def targeted(train=True):
    rng=random.Random(SEED if train else SEED+1)
    rows=[]
    n=32 if train else 8
    for i in range(n):
        # Signs and direction vary independently, with new quantities in dev.
        start=rng.randrange(50,180) if train else rng.randrange(320,550)
        a,b,c=rng.sample(range(2,29),3)
        changes=(a if i&1 else -a,b if i&2 else -b,c if i&4 else -c)
        total=start+sum(changes)
        events=", then ".join(("receive " if d>0 else "send ")+str(abs(d)) for d in changes)
        if i%2==0:
            q=f"A stock ledger starts at {start}. The operations are {events}. How many remain?"
            x=start
            trace=[]
            for d in changes:
                trace.append(f"{x}{d:+d}={x+d}"); x+=d
            ans=total
        else:
            q=f"After the operations {events}, a stock ledger ends at {total}. What was its initial balance?"
            x=total
            trace=[]
            for d in reversed(changes):
                trace.append(f"{x}{-d:+d}={x-d}"); x-=d
            ans=start
        rows.append(Example("quantity",q,"; ".join(trace)+".",str(ans)))

        x,y=rng.sample(range(2,25) if train else range(31,65),2)
        # Train with three transformation families; transfer uses a fourth.
        kind=i%3 if train else 3
        mapping=(" (y,x+y)"," (x+y,y-x)"," (x-y,x+y)"," (y-x,x+y)")[kind].strip()
        steps=2+(i//3)%2 if train else 3
        coord=(i//6)%2 if train else i%2
        q=f"Transform (x,y) into {mapping} simultaneously, {steps} times, starting at ({x},{y}). Give coordinate {'first' if coord==0 else 'second'} of the result."
        trace=[]
        for _ in range(steps):
            x,y=((y,x+y) if kind==0 else (x+y,y-x) if kind==1 else (x-y,x+y) if kind==2 else (y-x,x+y))
            trace.append(f"({x},{y})")
        rows.append(Example("abstraction",q," -> ".join(trace)+".",str((x,y)[coord])))
    if len({r.question for r in rows})!=len(rows):
        raise ValueError("Duplicate targeted questions")
    return tuple(rows)

def suites():
    train=list(targeted(True))+[r for r in prior(True,8) if r.domain not in ("quantity","abstraction")]
    rng=random.Random(SEED+2)
    rng.shuffle(train)
    # Original structural transfer cases retained; fresh additional target
    # templates and retention controls use separate questions.
    old=prior(False,8)
    dev=[r for r in old if r.domain in ("quantity","abstraction")]+list(targeted(False))
    for domain in DOMAINS[2:]:
        dev.extend([r for r in old if r.domain==domain][:2])
    dev+=list(controls())
    if len(train)!=96 or len(dev)!=44 or len({r.question for r in train})!=len(train) or len({r.question for r in dev})!=len(dev) or {r.question for r in train}&{r.question for r in dev}:
        raise ValueError("Invalid or overlapping curriculum")
    return tuple(train),tuple(dev)

def encode(tok,example,maximum=384):
    prefix=tok.apply_chat_template([{"role":"system","content":SYSTEM},
        {"role":"user","content":example.question}],tokenize=True,add_generation_prompt=True,enable_thinking=False)
    if not prefix or tok.eos_token_id is None:
        raise ValueError("Missing generation boundary")
    trace="Reason: "+example.rationale+"\nAnswer: "
    suffix=trace+example.answer
    encoded=tok(suffix,add_special_tokens=False,return_offsets_mapping=True)
    ids=list(encoded["input_ids"])
    offsets=encoded["offset_mapping"]
    if len(ids)!=len(offsets) or not ids or any(not 0<=a<b<=len(suffix) for a,b in offsets):
        raise ValueError("Invalid suffix token offsets")
    if ids!=tok.encode(suffix,add_special_tokens=False):
        raise ValueError("Suffix encoding mismatch")
    # Include any token overlapping the answer value and final EOS; no
    # assumptions about how spaces, signs, digits or labels tokenize.
    mask=[int(b>len(trace)) for a,b in offsets]+[1]
    if not any(mask[:-1]) or all(mask):
        raise ValueError("Missing answer or rationale tokens")
    ids+=[tok.eos_token_id]
    if len(prefix)+len(ids)>maximum:
        raise ValueError("Unbounded sequence")
    return prefix+ids,prefix,mask

def weighted_ce(logits,targets,mask,answer_weight):
    import torch
    import torch.nn.functional as F
    if answer_weight not in (1.,4.) or logits.ndim!=2 or targets.ndim!=1 or mask.ndim!=1 or logits.shape[0]!=targets.numel() or targets.shape!=mask.shape or mask.dtype!=torch.bool or not mask.any().item() or mask.all().item():
        raise ValueError("Invalid answer-weighted loss contract")
    loss=F.cross_entropy(logits.float(),targets,reduction="none")
    weights=torch.where(mask,answer_weight,1.).to(loss.dtype)
    return (loss*weights).sum()/weights.sum()

def assistant_loss(model,ids,prefix,mask,weight):
    import torch
    n=len(ids)-len(prefix)
    if not prefix or ids[:len(prefix)]!=prefix or n!=len(mask) or any(m not in (0,1) for m in mask):
        raise ValueError("Invalid assistant suffix")
    x=torch.tensor([ids],dtype=torch.long)
    result=model(input_ids=x,attention_mask=torch.ones_like(x),use_cache=False,logits_to_keep=n+1)
    if result.logits.shape[:2]!=(1,n+1):
        raise RuntimeError("Unexpected projection shape")
    return weighted_ce(result.logits[0,:-1],torch.tensor(ids[len(prefix):],dtype=torch.long),
                       torch.tensor(mask,dtype=torch.bool),weight)
