"""Matched compact-trace training: identical tokens, answer loss weight 1 versus 4."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import time

from qwen_rationale_curriculum import grade, SYSTEM
from qwen_answer_weighted import suites, digest, encode, assistant_loss
from train_qwen_neural_pilot import MODEL, REVISION, LAYERS, file_digest
from train_qwen_gsm8k import padded
from evaluate_qwen_rationale_checkpoints import completion_tokens
import qwen_neural_resume as resume

SEED=918326
BASE_HASHES={
    "model-00001-of-00002.safetensors":"169ad53ec313c3a34b06c0809216e4fc072cce444a5d4ff2b59690d064130ed5",
    "model-00002-of-00002.safetensors":"912becff8d60672aa8628ef08c05898d9adf17c2ad4ae3caf99b065622fdeff9",
}
ARMS={"ordinary":1.,"weighted":4.}
CAP=128
BATCH=4

def state_digest(state):
    h=hashlib.sha256()
    for name,p in sorted(state.items()):
        h.update(name.encode())
        h.update(p.detach().float().cpu().contiguous().numpy().tobytes())
    return h.hexdigest()

def visible(raw):
    if not isinstance(raw,str):
        return ""
    text=re.sub(r"<think>.*?</think>","",raw,flags=re.I|re.S)
    if re.search(r"</?think>",text,re.I):
        return ""
    return text.strip()

def summarize(rows):
    if not rows or len({r["ident"] for r in rows})!=len(rows):
        raise ValueError("Invalid evaluation set")
    def aggregate(rr):
        return dict(attempted=len(rr),complete=sum(r["complete"] for r in rr),
                    correct=sum(r["correct"] for r in rr),terminal_format=sum(r["terminal_format"] for r in rr),
                    generated_tokens=sum(r["tokens"] for r in rr))
    return {"all":aggregate(rows),**{d:aggregate([r for r in rows if r["domain"]==d])
            for d in sorted({r["domain"] for r in rows})}}

def paired(before,after):
    a={r["ident"]:r for r in before}; b={r["ident"]:r for r in after}
    if not a or len(a)!=len(before) or len(b)!=len(after) or a.keys()!=b.keys() or any(
            a[k]["prompt_sha256"]!=b[k]["prompt_sha256"] or a[k]["domain"]!=b[k]["domain"] for k in a):
        raise ValueError("Evaluation cases changed")
    return dict(improved=sum(not a[k]["correct"] and b[k]["correct"] for k in a),
                regressed=sum(a[k]["correct"] and not b[k]["correct"] for k in a),
                both_correct=sum(a[k]["correct"] and b[k]["correct"] for k in a),
                neither=sum(not a[k]["correct"] and not b[k]["correct"] for k in a))

def run(out):
    import torch
    import transformers
    import peft
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import LoraConfig,get_peft_model,get_peft_model_state_dict,set_peft_model_state_dict

    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    out.mkdir(parents=True,exist_ok=False)
    train,dev=suites()
    if {e.question for e in train}&{e.question for e in dev}:
        raise RuntimeError("Train/development overlap")
    root=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    hashes={p.name:file_digest(p) for p in sorted(root.glob("*.safetensors"))}
    if root.name!=REVISION or hashes!=BASE_HASHES:
        raise RuntimeError("Unexpected base checkpoint")
    tok=AutoTokenizer.from_pretrained(root,trust_remote_code=False,local_files_only=True)
    base=AutoModelForCausalLM.from_pretrained(root,torch_dtype=torch.float32,trust_remote_code=False,
        local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if (base.config.model_type!="qwen3" or base.config.num_hidden_layers!=28 or
        base.config.hidden_size!=2048 or tok.eos_token_id!=151645):
        raise RuntimeError("Unexpected model architecture/tokenizer")
    def config():
        return LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,lora_dropout=0.0,bias="none",
            target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers")
    model=get_peft_model(base,config(),adapter_name="ordinary")
    model.add_adapter("weighted",config())
    initial={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="ordinary").items()}
    set_peft_model_state_dict(model,initial,adapter_name="weighted")
    initial_hash=state_digest(initial)
    if state_digest(get_peft_model_state_dict(model,adapter_name="weighted"))!=initial_hash:
        raise RuntimeError("Unequal initial adapters")
    training=[encode(tok,e) for e in train]
    # Evaluation input is only the shared system and user prompt, never targets.
    development=[encode(tok,e)[1] for e in dev]
    code_root=Path(__file__).resolve().parent
    sources={name:file_digest(code_root/name) for name in (
        "train_qwen_answer_weighted.py","qwen_answer_weighted.py","qwen_rationale_curriculum.py",
        "qwen_neural_curriculum.py","train_qwen_neural_pilot.py","qwen_neural_resume.py")}

    def evaluate():
        model.eval()
        rows=[]
        for start in range(0,len(dev),BATCH):
            prefixes=development[start:start+BATCH]
            ids,mask=padded(prefixes,tok.eos_token_id)
            x=torch.tensor(ids,dtype=torch.long)
            before=time.monotonic()
            with torch.inference_mode():
                output=model.generate(input_ids=x,attention_mask=torch.tensor(mask,dtype=torch.long),
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=CAP,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
            elapsed=round((time.monotonic()-before)*1000)
            for j,prefix in enumerate(prefixes):
                e=dev[start+j]
                tokens,complete=completion_tokens(output[j,x.shape[1]:].tolist(),tok.eos_token_id,CAP)
                content=visible(tok.decode(tokens,skip_special_tokens=True))
                answer_correct,terminal=grade(content,e.answer)
                rows.append(dict(ident=str(start+j),domain=e.domain,complete=complete,
                    correct=complete and answer_correct,terminal_format=complete and terminal,
                    tokens=len(tokens),elapsed_ms=elapsed,expected=e.answer,visible_output=content[:3000],
                    output_sha256=hashlib.sha256(content.encode()).hexdigest(),
                    prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest()))
            print(f"evaluated {len(rows)}/{len(dev)}",flush=True)
        return rows

    results={}
    for name,weight in ARMS.items():
        model.set_adapter(name)
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=initial_hash:
            raise RuntimeError("Adapter initialization changed")
        params=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
        if not params or any(f".{name}." not in n or "lora_" not in n or
                not any(f".layers.{i}." in n for i in LAYERS) for n,p in params):
            raise RuntimeError("Unexpected trainable parameters")
        if sum(p.numel() for _,p in params)!=229376:
            raise RuntimeError("Unexpected adapter size")
        optimizer=torch.optim.AdamW([p for _,p in params],lr=5e-5,weight_decay=0.0,foreach=False)
        torch.manual_seed(SEED)
        model.train()
        losses=[]
        encoded=training
        ordered=hashlib.sha256(json.dumps(encoded,separators=(",",":")).encode()).hexdigest()
        identity=dict(model=MODEL,revision=REVISION,base_weight_sha256=hashes,dtype="float32",
            arm=name,answer_weight=weight,train_sha256=digest(train),development_sha256=digest(dev),
            ordered_tokens_sha256=ordered,initial_adapter_sha256=initial_hash,
            source_sha256=sources,seed=SEED,versions=dict(transformers=transformers.__version__,peft=peft.__version__))
        start=time.monotonic()
        for step,(ids,prefix,mask) in enumerate(encoded,start=1):
            optimizer.zero_grad(set_to_none=True)
            loss=assistant_loss(model,ids,prefix,mask,weight)
            if not torch.isfinite(loss).item():
                raise RuntimeError("Nonfinite training loss")
            loss.backward()
            norm=torch.nn.utils.clip_grad_norm_([p for _,p in params],1.0)
            if not torch.isfinite(norm).item():
                raise RuntimeError("Nonfinite gradient")
            optimizer.step()
            losses.append(float(loss.detach()))
            del loss
            if any(not torch.isfinite(p).all().item() for _,p in params):
                raise RuntimeError("Nonfinite adapter weights")
            if step%16==0 or step==len(encoded):
                resume.save(out/"resume"/name/f"step-{step:03d}",params,optimizer,identity,step,losses)
                print(f"{name}: {step}/{len(encoded)} checkpointed",flush=True)
        training_ms=round((time.monotonic()-start)*1000)
        final_hash=state_digest(get_peft_model_state_dict(model,adapter_name=name))
        if final_hash==initial_hash:
            raise RuntimeError("No neural update")
        model.save_pretrained(out/name,selected_adapters=[name],safe_serialization=True)
        results[name]=dict(answer_weight=weight,train_steps=len(losses),losses=losses,
            training_ms=training_ms,supervised_tokens=sum(len(mask) for _,_,mask in encoded),
            answer_and_eos_tokens=sum(sum(mask) for _,_,mask in encoded),
            initial_adapter_sha256=initial_hash,final_adapter_sha256=final_hash,
            adapter_file_sha256=file_digest(out/name/name/"adapter_model.safetensors"))
        del optimizer
    for name,record in results.items():
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=record["final_adapter_sha256"]:
            raise RuntimeError("Another adapter changed during training")
    # Retain both actual weights and training metadata before generation.
    (out/"training.json").write_text(json.dumps(dict(status="TRAINED_UNEVALUATED",
        model=MODEL,revision=REVISION,base_weight_sha256=hashes,train_sha256=digest(train),
        development_sha256=digest(dev),adapters=results,source_sha256=sources,
        official_ai_rating=None),indent=2,allow_nan=False)+"\n")
    with model.disable_adapter():
        baseline=evaluate()
    for name in ARMS:
        model.set_adapter(name)
        rows=evaluate()
        results[name].update(rows=rows,summary=summarize(rows),paired_vs_base=paired(baseline,rows))
    with model.disable_adapter():
        repeat=evaluate()
    if any({k:v for k,v in a.items() if k!="elapsed_ms"}!={k:v for k,v in b.items() if k!="elapsed_ms"}
            for a,b in zip(baseline,repeat)):
        raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_ANSWER_WEIGHTED_TRANSFER",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,training_dtype="float32",seed=SEED,train_cases=len(train),
        development_cases=len(dev),train_sha256=digest(train),development_sha256=digest(dev),
        system_sha256=hashlib.sha256(SYSTEM.encode()).hexdigest(),source_sha256=sources,
        baseline=baseline,baseline_summary=summarize(baseline),adapters=results,
        paired_weighted_vs_ordinary=paired(results["ordinary"]["rows"],results["weighted"]["rows"]),
        frozen_baseline_reproduced=True,official_ai_rating=None,
        scope="Public synthetic generative transfer. Identical target tokens, different answer loss weights. Mixed in-family and new transformation families; no independent score or deployment approval.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    return report

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    args=p.parse_args()
    run(args.output)
