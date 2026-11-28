"""Conservative strict and content-only retention scoring."""
import re
import qwen_full_answer_retention as suite

def score(raw,case,complete):
    if type(complete) is not bool or not isinstance(raw,str):
        raise ValueError("Invalid response")
    if not complete or not raw.strip() or re.search(r"<\|[^>\r\n]*\|>|</?think>",raw,re.I):
        return False,False
    if suite.grade(raw,case):
        return True,True
    lines=raw.strip().splitlines()
    labels=[(i,re.fullmatch(r"\s*Answer:\s*(\S.*)",line,re.I))
            for i,line in enumerate(lines) if re.match(r"^\s*Answer\s*:",line,re.I)]
    if len(labels)!=1 or labels[0][0]!=len(lines)-1 or labels[0][1] is None:
        return False,False
    return False,suite.grade(labels[0][1].group(1),case)

def summarize(rows):
    strict=suite.summarize(rows)
    if any(type(r["content_correct"]) is not bool or
           (r["content_correct"] and not r["complete"]) or
           (r["correct"] and not r["content_correct"]) for r in rows):
        raise ValueError("Invalid content correctness")
    content=suite.summarize([dict(r,correct=r["content_correct"]) for r in rows])
    return {"strict":strict,"content":content}

def paired(base,adapted):
    summarize(base)
    summarize(adapted)
    return {"strict":suite.paired(base,adapted),
            "content":suite.paired([dict(r,correct=r["content_correct"]) for r in base],
                                   [dict(r,correct=r["content_correct"]) for r in adapted])}
