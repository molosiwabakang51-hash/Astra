"""Two-phase public mathematical reasoning adaptation; never train on test targets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time

import qwen_gsm8k_curriculum as data
import qwen_neural_resume as resume
from train_qwen_neural_pilot import MODEL, REVISION, file_digest, assistant_loss

BASE_HASHES={
    "model-00001-of-00002.safetensors":"169ad53ec313c3a34b06c0809216e4fc072cce444a5d4ff2b59690d064130ed5",
    "model-00002-of-00002.safetensors":"912becff8d60672aa8628ef08c05898d9adf17c2ad4ae3caf99b065622fdeff9",
}
LAYERS=list(range(20,28))
SEED=821139
LR=5e-5
BATCH=4
CAP=128
PARAMETERS=458752

def digest(state):
    h=hashlib.sha256()
    for key,t in sorted(state.items()):
        h.update(key.encode())
        h.update(t.detach().float().cpu().contiguous().numpy().tobytes())
    return h.hexdigest()

def sources():
    root=Path(__file__).resolve().parent
    return {name:file_digest(root/name) for name in (
        "train_qwen_gsm8k.py","qwen_gsm8k_curriculum.py",
        "train_qwen_neural_pilot.py","qwen_neural_resume.py")}

def load():
    import torch
    from huggingface_hub import snapshot_download
    from transformers import AutoModelForCausalLM,AutoTokenizer
    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    root=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    hashes={p.name:file_digest(p) for p in sorted(root.glob("*.safetensors"))}
    if root.name!=REVISION or hashes!=BASE_HASHES:
        raise RuntimeError("Unexpected base weights")
    tok=AutoTokenizer.from_pretrained(root,trust_remote_code=False,local_files_only=True)
    model=AutoModelForCausalLM.from_pretrained(root,torch_dtype=torch.float32,
        trust_remote_code=False,local_files_only=True,low_cpu_mem_usage=True,
        attn_implementation="sdpa")
    if (model.config.model_type!="qwen3" or model.config.num_hidden_layers!=28
        or model.config.hidden_size!=2048 or tok.eos_token_id!=151645):
        raise RuntimeError("Unexpected model architecture")
    return model,tok

def identity(tok):
    train,dev,files=data.load(tok)
    encoded=[data.encode(tok,p) for p in train]
    info=dict(model=MODEL,model_revision=REVISION,base_weight_sha256=BASE_HASHES,
        dataset=data.DATASET,dataset_revision=data.REVISION,dataset_file_sha256=files,
        train_sha256=data.digest(train),test_sha256=data.digest(dev),
        ordered_tokens_sha256=hashlib.sha256(json.dumps(encoded,separators=(",",":")).encode()).hexdigest(),
        source_sha256=sources(),dtype="float32",seed=SEED,learning_rate=LR,
        target_layers=LAYERS,target_modules=["q_proj","v_proj"],rank=8)
    return encoded,dev,info

def train(out):
    import torch
    import transformers
    import peft
    from peft import LoraConfig,get_peft_model,get_peft_model_state_dict
    out.mkdir(parents=True,exist_ok=False)
    base,tok=load()
    encoded,_,info=identity(tok)
    config=LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,lora_dropout=0.0,bias="none",
        target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers")
    model=get_peft_model(base,config)
    before=digest(get_peft_model_state_dict(model))
    params=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
    if (sum(p.numel() for _,p in params)!=PARAMETERS or any(
        ".lora_" not in n or not any(f".layers.{i}." in n for i in LAYERS)
        for n,p in params)):
        raise RuntimeError("Unexpected trainable parameters")
    optimizer=torch.optim.AdamW([p for _,p in params],lr=LR,weight_decay=0.,foreach=False)
    torch.manual_seed(SEED)
    model.train()
    info.update(initial_adapter_sha256=before,
                versions=dict(torch=str(torch.__version__),transformers=transformers.__version__,peft=peft.__version__))
    losses=[]
    start=time.monotonic()
    for step,(ids,labels,prefix) in enumerate(encoded,1):
        optimizer.zero_grad(set_to_none=True)
        loss=assistant_loss(model,ids,labels,prefix)
        if not torch.isfinite(loss).item():
            raise RuntimeError("Nonfinite loss")
        loss.backward()
        norm=torch.nn.utils.clip_grad_norm_([p for _,p in params],1.)
        if not torch.isfinite(norm).item():
            raise RuntimeError("Nonfinite gradient")
        optimizer.step()
        if any(not torch.isfinite(p).all().item() for _,p in params):
            raise RuntimeError("Nonfinite adapter")
        losses.append(float(loss.detach()))
        del loss
        if step%16==0 or step==len(encoded):
            resume.save(out/"resume"/f"step-{step:03d}",params,optimizer,info,step,losses)
            print(f"{step}/{len(encoded)} updates checkpointed",flush=True)
    final=digest(get_peft_model_state_dict(model))
    if final==before:
        raise RuntimeError("No parameter update")
    model.save_pretrained(out/"adapter",safe_serialization=True)
    path=out/"adapter"/"adapter_model.safetensors"
    report=dict(status="TRAINED_UNEVALUATED",identity=info,completed_steps=len(losses),
        losses=losses,trainable_parameters=PARAMETERS,training_ms=round((time.monotonic()-start)*1000),
        final_adapter_sha256=final,adapter_file_sha256=file_digest(path),
        official_ai_rating=None)
    (out/"training.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")

def padded(prefixes,pad):
    if not prefixes or any(not p for p in prefixes):
        raise ValueError("Empty prompt")
    n=max(map(len,prefixes))
    return ([[pad]*(n-len(p))+p for p in prefixes],
            [[0]*(n-len(p))+[1]*len(p) for p in prefixes])

def completion(tokens,eos):
    if not tokens or len(tokens)>CAP:
        raise ValueError("Invalid completion length")
    if eos not in tokens:
        return tokens,False
    end=tokens.index(eos)+1
    if any(t!=eos for t in tokens[end:]):
        raise ValueError("Nonpadding token after EOS")
    return tokens[:end],True

def summarize(rows):
    if len(rows)!=data.TEST_COUNT or len({r["ident"] for r in rows})!=len(rows):
        raise ValueError("Incomplete evaluation set")
    return dict(attempted=len(rows),complete=sum(r["complete"] for r in rows),
        correct=sum(r["correct"] for r in rows),generated_tokens=sum(r["tokens"] for r in rows))

def compare(a,b):
    aa={r["ident"]:r for r in a}
    bb={r["ident"]:r for r in b}
    if len(aa)!=len(a) or len(bb)!=len(b) or aa.keys()!=bb.keys() or not aa:
        raise ValueError("Unmatched evaluation questions")
    if any(aa[k]["prompt_sha256"]!=bb[k]["prompt_sha256"] for k in aa):
        raise ValueError("Changed evaluation prompt")
    return dict(gains=sum(not aa[k]["correct"] and bb[k]["correct"] for k in aa),
                losses=sum(aa[k]["correct"] and not bb[k]["correct"] for k in aa))

def evaluate(out):
    import torch
    import transformers
    import peft
    from peft import PeftModel,get_peft_model_state_dict
    from safetensors.torch import load_file
    report=json.loads((out/"training.json").read_text())
    base,tok=load()
    _,dev,info=identity(tok)
    old=report["identity"]
    if (report["status"]!="TRAINED_UNEVALUATED" or report["completed_steps"]!=data.TRAIN_COUNT
        or any(old.get(k)!=v for k,v in info.items())
        or old["versions"]!=dict(torch=str(torch.__version__),transformers=transformers.__version__,peft=peft.__version__)
        or report["trainable_parameters"]!=PARAMETERS):
        raise ValueError("Training identity mismatch")
    path=out/"adapter"/"adapter_model.safetensors"
    if not 0<path.stat().st_size<=16*1024*1024 or file_digest(path)!=report["adapter_file_sha256"]:
        raise ValueError("Invalid adapter file")
    state=load_file(str(path),device="cpu")
    if (len(state)!=32 or sum(t.numel() for t in state.values())!=PARAMETERS or
        any(t.dtype!=torch.float32 or not torch.isfinite(t).all().item() for t in state.values())
        or digest(state)!=report["final_adapter_sha256"]):
        raise ValueError("Adapter tensor mismatch")
    cfg=json.loads((out/"adapter"/"adapter_config.json").read_text())
    if (cfg["peft_type"]!="LORA" or cfg["r"]!=8 or cfg["lora_alpha"]!=16
        or cfg["layers_to_transform"]!=LAYERS or sorted(cfg["target_modules"])!=["q_proj","v_proj"]):
        raise ValueError("Adapter configuration mismatch")
    model=PeftModel.from_pretrained(base,out/"adapter",is_trainable=False)
    if digest(get_peft_model_state_dict(model))!=report["final_adapter_sha256"]:
        raise RuntimeError("Loaded adapter differs from saved adapter")
    prefixes=[data.prefix(tok,p) for p in dev]

    def generate():
        model.eval()
        rows=[]
        for start in range(0,len(dev),BATCH):
            batch=prefixes[start:start+BATCH]
            ids,mask=padded(batch,tok.eos_token_id)
            x=torch.tensor(ids,dtype=torch.long)
            t=time.monotonic()
            with torch.inference_mode():
                output=model.generate(input_ids=x,attention_mask=torch.tensor(mask,dtype=torch.long),
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=CAP,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
            elapsed=round((time.monotonic()-t)*1000)
            for j,prefix in enumerate(batch):
                tokens,done=completion(output[j,x.shape[1]:].tolist(),tok.eos_token_id)
                text=tok.decode(tokens,skip_special_tokens=True).strip()
                p=dev[start+j]
                rows.append(dict(ident=p.ident,complete=done,correct=done and data.grade(text,p.answer),
                    tokens=len(tokens),expected=p.answer,output_sha256=hashlib.sha256(text.encode()).hexdigest(),
                    visible_output=text[:3000],batch_elapsed_ms=elapsed,
                    prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest()))
            print(f"evaluated {len(rows)}/{len(dev)}",flush=True)
        return rows

    with model.disable_adapter():
        baseline=generate()
    adapted=generate()
    with model.disable_adapter():
        repeated=generate()
    def stable(rows):
        return [{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(baseline)!=stable(repeated):
        raise RuntimeError("Frozen baseline changed")
    result=dict(status="COMPLETE_PUBLIC_GSM8K_TRANSFER",training=report,
        baseline=baseline,adapted=adapted,baseline_summary=summarize(baseline),
        adapted_summary=summarize(adapted),paired=compare(baseline,adapted),
        frozen_baseline_reproduced=True,batch_size=BATCH,generation_cap=CAP,official_ai_rating=None,
        scope="Small public test-split diagnostic; pretraining overlap unknown, strict terminal numeric grading, not general intelligence or deployment approval.")
    (out/"report.json").write_text(json.dumps(result,indent=2,allow_nan=False)+"\n")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("phase",choices=("train","evaluate"))
    p.add_argument("output",type=Path)
    a=p.parse_args()
    (train if a.phase=="train" else evaluate)(a.output)
