"""Score actual semantic answer strings, not option letters.

All supplied options are possible completions. Evaluation is restricted
recognition, not free generation; length-normalized scores are not calibrated
probabilities. No external answer tools or private assessment.
"""
import hashlib
import json
import random

SYSTEM=("Choose the correct answer using the given information. "
        "Respond with the exact text of one offered option, without a label.")

def prepare(tokenizer,case,reverse=False,maximum=384):
    if len(case.options)!=4 or len(set(case.options))!=4 or not 0<=case.correct<4:
        raise ValueError("Invalid options")
    options=tuple(reversed(case.options)) if reverse else tuple(case.options)
    correct=3-case.correct if reverse else case.correct
    question=case.question+"\nOptions:\n"+"\n".join("- "+v for v in options)
    prefix=tokenizer.apply_chat_template(
        [{"role":"system","content":SYSTEM},{"role":"user","content":question}],
        tokenize=True,add_generation_prompt=True,enable_thinking=False)
    eos=tokenizer.eos_token_id
    if not prefix or eos is None:
        raise ValueError("Missing generation prefix/end token")
    suffixes=[tokenizer.encode(v,add_special_tokens=False)+[eos] for v in options]
    if (any(len(t)<2 or len(prefix)+len(t)>maximum or eos in t[:-1] for t in suffixes)
        or len({tuple(t) for t in suffixes})!=4):
        raise ValueError("Duplicate, empty or oversized answer completion")
    return prefix,suffixes,correct

def digest(value):
    return hashlib.sha256(json.dumps(value,separators=(",",":"),ensure_ascii=True).encode()).hexdigest()

def sequence_scores(model,prefix,suffixes,pad):
    """Mean log probability of each exact answer completion, including EOS.

    Left pad batches, explicitly set position IDs, project only suffix
    predicting positions. No padding or prompt tokens enter the score.
    """
    import torch
    import torch.nn.functional as F
    if not prefix or not suffixes or any(not s for s in suffixes):
        raise ValueError("Empty prefix or completion")
    lengths=[len(s) for s in suffixes]
    keep=max(lengths)+1
    sequences=[prefix+s for s in suffixes]
    longest=max(map(len,sequences))
    padded=[[pad]*(longest-len(s))+s for s in sequences]
    masks=[[0]*(longest-len(s))+[1]*len(s) for s in sequences]
    x=torch.tensor(padded,dtype=torch.long)
    mask=torch.tensor(masks,dtype=torch.long)
    positions=(mask.cumsum(-1)-1).clamp_min(0)
    z=model(input_ids=x,attention_mask=mask,position_ids=positions,
            use_cache=False,logits_to_keep=keep).logits
    if z.ndim!=3 or z.shape[:2]!=(len(suffixes),keep):
        raise RuntimeError("Unexpected suffix projection shape")
    values=[]
    for i,(suffix,n) in enumerate(zip(suffixes,lengths)):
        # Last output predicts the token *after* the given completion;
        # the preceding n positions predict exactly the n supplied tokens.
        selected=z[i,-(n+1):-1,:].float()
        targets=torch.tensor(suffix,dtype=torch.long)
        value=-F.cross_entropy(selected,targets,reduction="mean")
        values.append(value)
    result=torch.stack(values)
    if not torch.isfinite(result).all().item():
        raise ValueError("Nonfinite sequence scores")
    return result

def objective(scores,weight=0.0,margin=0.5):
    import torch
    import torch.nn.functional as F
    if scores.ndim!=1 or scores.shape[0]!=2 or not torch.isfinite(scores).all().item():
        raise ValueError("Expected correct and distractor scores")
    if type(weight) not in (int,float) or not 0<=weight<=4 or type(margin) not in (int,float) or not 0<=margin<=4:
        raise ValueError("Invalid contrastive objective")
    # First score is always the correct semantic completion. Both arms
    # calculate both scores; only the ranking penalty differs.
    return -scores[0]+weight*F.softplus(margin+scores[1]-scores[0])

def distractors(suite,seed=812041):
    rng=random.Random(seed)
    return [rng.choice([i for i in range(4) if i!=case.correct]) for case in suite]

def summarize(rows):
    grouped={}
    for row in rows:
        k=row["ident"]
        order=row["order"]
        if order not in ("original","reversed") or order in grouped.setdefault(k,{}):
            raise ValueError("Duplicate or invalid order")
        grouped[k][order]=row
    if not grouped or any(set(g)!={"original","reversed"} for g in grouped.values()):
        raise ValueError("Incomplete paired order")
    by_question=[]
    for k,g in grouped.items():
        a,b=g["original"],g["reversed"]
        if a["domain"]!=b["domain"]:
            raise ValueError("Domain mismatch")
        by_question.append(dict(ident=k,domain=a["domain"],
            original_correct=a["correct"],reversed_correct=b["correct"],
            both_correct=a["correct"] and b["correct"],
            consistent=a["selected_semantic"] is not None and a["selected_semantic"]==b["selected_semantic"]))
    def agg(qs):
        return dict(questions=len(qs),original_correct=sum(q["original_correct"] for q in qs),
                    reversed_correct=sum(q["reversed_correct"] for q in qs),
                    both_correct=sum(q["both_correct"] for q in qs),
                    consistent=sum(q["consistent"] for q in qs))
    return dict(all=agg(by_question),by_domain={d:agg([q for q in by_question if q["domain"]==d])
                for d in sorted({q["domain"] for q in by_question})},by_question=by_question)

def paired(before,after):
    a={r["ident"]:r for r in before["by_question"]}
    b={r["ident"]:r for r in after["by_question"]}
    if not a or a.keys()!=b.keys() or any(a[k]["domain"]!=b[k]["domain"] for k in a):
        raise ValueError("Unmatched questions")
    return dict(new_both_correct=sum(not a[k]["both_correct"] and b[k]["both_correct"] for k in a),
                lost_both_correct=sum(a[k]["both_correct"] and not b[k]["both_correct"] for k in a))
