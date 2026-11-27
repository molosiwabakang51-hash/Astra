"""GSM8K public developmental trial, not an independent intelligence benchmark.

Source: openai/gsm8k (MIT), immutable dataset revision below. Original
questions and solutions are downloaded at execution, not committed.
"""
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
import hashlib
import json
import re

DATASET="openai/gsm8k"
REVISION="cc7b047b6e5bb11b4f1af84efc572db110a51b3c"
TRAIN_COUNT=96
TEST_COUNT=24
SEED="astra-gsm8k-public-v1-1127"
SYSTEM=("Solve the mathematical word problem. Explain your calculations briefly. "
        "End with a separate line Answer: NUMBER, using only the final numeric value on that line.")
NUMBER=re.compile(r"[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?")

@dataclass(frozen=True)
class Problem:
    ident:str
    question:str
    rationale:str
    answer:str

def numeric(value):
    if not isinstance(value,str) or len(value)>64 or not NUMBER.fullmatch(value.strip()):
        return None
    try:
        result=Decimal(value.strip().replace(",",""))
    except InvalidOperation:
        return None
    return result if result.is_finite() else None

def normalized(question):
    return " ".join(question.casefold().split())

def parse(row):
    if set(row)!={"question","answer"} or not all(isinstance(row[k],str) for k in row):
        raise ValueError("Unexpected GSM8K row schema")
    q=row["question"].strip()
    if not q or len(q)>8192 or row["answer"].count("####")!=1:
        raise ValueError("Invalid question or answer delimiter")
    rationale,answer=row["answer"].rsplit("####",1)
    answer=answer.strip()
    if numeric(answer) is None:
        raise ValueError("Invalid numeric teacher answer")
    # Remove calculator markup; retain the surrounding human-written calculation.
    rationale=re.sub(r"<<[^<>\n]*>>","",rationale).strip()
    if not rationale or "<<" in rationale or ">>" in rationale or len(rationale)>16384:
        raise ValueError("Invalid teacher rationale")
    ident=hashlib.sha256(normalized(q).encode()).hexdigest()
    return Problem(ident,q,rationale,answer.replace(",",""))

def order(rows):
    # Stable order independent of parquet row order or Python hash seed.
    return sorted(rows,key=lambda r:hashlib.sha256((SEED+r.ident).encode()).hexdigest())

def unique(rows):
    result={}
    for row in rows:
        p=parse(row)
        if p.ident in result and numeric(result[p.ident].answer)!=numeric(p.answer):
            raise ValueError("Conflicting duplicate question")
        if p.ident not in result or (p.question,p.rationale,p.answer)<(
                result[p.ident].question,result[p.ident].rationale,result[p.ident].answer):
            result[p.ident]=p
    return order(result.values())

def select(train_rows,test_rows,tokenizer,train_count=TRAIN_COUNT,test_count=TEST_COUNT):
    if not 1<=train_count<=TRAIN_COUNT or not 1<=test_count<=TEST_COUNT:
        raise ValueError("Invalid sample size")
    train=unique(train_rows)
    test=unique(test_rows)
    test_ids={p.ident for p in test}
    # Test selection depends on question and input length only, not target length or model output.
    dev=[p for p in test if len(prefix(tokenizer,p))<=384][:test_count]
    chosen=[]
    for p in train:
        if p.ident in test_ids:
            continue
        encoded=encode(tokenizer,p)
        if len(encoded[0])<=512:
            chosen.append(p)
        if len(chosen)==train_count:
            break
    if len(chosen)!=train_count or len(dev)!=test_count:
        raise RuntimeError("Insufficient disjoint eligible samples")
    return tuple(chosen),tuple(dev)

def prefix(tokenizer,p):
    ids=tokenizer.apply_chat_template(
        [{"role":"system","content":SYSTEM},{"role":"user","content":p.question}],
        tokenize=True,add_generation_prompt=True,enable_thinking=False)
    if not ids:
        raise ValueError("Empty input")
    return ids

def encode(tokenizer,p):
    start=prefix(tokenizer,p)
    completion=p.rationale+"\nAnswer: "+p.answer
    eos=tokenizer.eos_token_id
    if eos is None:
        raise ValueError("Missing EOS")
    end=tokenizer.encode(completion,add_special_tokens=False)
    if not end or eos in end:
        raise ValueError("Unexpected completion tokens")
    end.append(eos)
    return start+end,[-100]*len(start)+end,start

def digest(problems):
    return hashlib.sha256(json.dumps([p.__dict__ for p in problems],sort_keys=True,
        separators=(",",":"),ensure_ascii=True).encode()).hexdigest()

def grade(text,expected):
    """Exact numeric terminal line or bare numeric response; no substring extraction."""
    if not isinstance(text,str):
        return False
    text=text.strip()
    if re.search(r"</?think>",text,re.I):
        text=re.sub(r"<think>.*?</think>","",text,flags=re.I|re.S).strip()
        if re.search(r"</?think>",text,re.I):
            return False
    lines=text.splitlines()
    matches=[i for i,line in enumerate(lines) if re.match(r"^\s*Answer\s*:",line,re.I)]
    if matches:
        if matches!=[len(lines)-1]:
            return False
        m=re.fullmatch(r"Answer:\s*(.+)",lines[-1].strip(),re.I)
        value=numeric(m.group(1)) if m else None
    else:
        value=numeric(text)
    target=numeric(expected)
    return target is not None and value is not None and value==target

def load(tokenizer):
    from huggingface_hub import hf_hub_download
    import pyarrow.parquet as pq
    from pathlib import Path
    files={}
    data={}
    for split,expected_rows in (("train",7473),("test",1319)):
        path=Path(hf_hub_download(repo_id=DATASET,repo_type="dataset",revision=REVISION,
            filename=f"main/{split}-00000-of-00001.parquet"))
        if path.parent.parent.name!=REVISION:
            raise RuntimeError("Unexpected dataset snapshot")
        if not 0<path.stat().st_size<=16*1024*1024:
            raise RuntimeError("Invalid dataset size")
        table=pq.read_table(path,columns=["question","answer"])
        if table.num_rows!=expected_rows:
            raise RuntimeError("Unexpected split size")
        files[split]=hashlib.sha256(path.read_bytes()).hexdigest()
        data[split]=table.to_pylist()
    train,dev=select(data["train"],data["test"],tokenizer)
    return train,dev,files
