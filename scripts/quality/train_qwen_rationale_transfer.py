"""Actual Qwen3 1.7B supervised rationale-versus-answer-only adaptation.

The synthetic teacher is programmatic and public. Same base, inputs,
initialization, example order and optimizer; target lengths intentionally
differ. No tool assistance at evaluation, no independent score or deployment.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import time

from qwen_rationale_curriculum import curriculum, controls, digest, encode, grade, SYSTEM
from train_qwen_neural_pilot import MODEL, REVISION, LAYERS, file_digest, assistant_loss
import qwen_neural_resume as resume

SEED=3820171
BASE_HASHES={
    "model-00001-of-00002.safetensors":"169ad53ec313c3a34b06c0809216e4fc072cce444a5d4ff2b59690d064130ed5",
    "model-00002-of-00002.safetensors":"912becff8d60672aa8628ef08c05898d9adf17c2ad4ae3caf99b065622fdeff9",
}
ARMS={"answer_only":False,"rationale":True}
CAP=96

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

def run(out, per_domain=16):
    if type(per_domain) is not int or not 4<=per_domain<=32:
        raise ValueError("Bounded curriculum requires 4..32 examples per domain")
    import torch
    import transformers
    import peft
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import LoraConfig,get_peft_model,get_peft_model_state_dict,set_peft_model_state_dict

    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    out.mkdir(parents=True,exist_ok=False)
    train=curriculum(True,per_domain)
    dev=curriculum(False,8)+controls()
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
    model=get_peft_model(base,config(),adapter_name="answer_only")
    model.add_adapter("rationale",config())
    initial={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="answer_only").items()}
    set_peft_model_state_dict(model,initial,adapter_name="rationale")
    initial_hash=state_digest(initial)
    if state_digest(get_peft_model_state_dict(model,adapter_name="rationale"))!=initial_hash:
        raise RuntimeError("Unequal initial adapters")
    training={name:[encode(tok,e,flag) for e in train] for name,flag in ARMS.items()}
    if any(a[2]!=b[2] for a,b in zip(training["answer_only"],training["rationale"])):
        raise RuntimeError("Training inputs differ")
    # Evaluation input is only the shared system and user prompt, never targets.
    development=[encode(tok,e,False)[2] for e in dev]
    code_root=Path(__file__).resolve().parent
    sources={name:file_digest(code_root/name) for name in (
        "train_qwen_rationale_transfer.py","qwen_rationale_curriculum.py",
        "qwen_neural_curriculum.py","train_qwen_neural_pilot.py","qwen_neural_resume.py")}

    def evaluate():
        model.eval()
        rows=[]
        for index,(e,prefix) in enumerate(zip(dev,development)):
            x=torch.tensor([prefix],dtype=torch.long)
            start=time.monotonic()
            with torch.inference_mode():
                output=model.generate(input_ids=x,attention_mask=torch.ones_like(x),
                    do_sample=False,temperature=None,top_p=None,top_k=None,
                    max_new_tokens=CAP,pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,
                    use_cache=True)
            generated=output[0,len(prefix):].tolist()
            complete=bool(generated and generated[-1]==tok.eos_token_id)
            content=visible(tok.decode(generated,skip_special_tokens=True))
            answer_correct,terminal=grade(content,e.answer)
            # Public synthetic evaluation only. Preserve bounded visible output
            # for diagnosing wrong answers versus output-format failures.
            rows.append(dict(ident=str(index),domain=e.domain,complete=complete,
                correct=complete and answer_correct,terminal_format=complete and terminal,
                tokens=len(generated),elapsed_ms=round((time.monotonic()-start)*1000),
                expected=e.answer,visible_output=content[:2000],
                output_sha256=hashlib.sha256(content.encode()).hexdigest(),
                prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest()))
        return rows

    print("Evaluating frozen baseline",flush=True)
    with model.disable_adapter():
        baseline=evaluate()
    results={}
    for name,flag in ARMS.items():
        model.set_adapter(name)
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=initial_hash:
            raise RuntimeError("Adapter initialization changed")
        params=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
        if not params or any(f".{name}." not in n or "lora_" not in n or
                not any(f".layers.{i}." in n for i in LAYERS) for n,p in params):
            raise RuntimeError("Unexpected trainable parameters")
        if sum(p.numel() for _,p in params)!=229376:
            raise RuntimeError("Unexpected adapter size")
        optimizer=torch.optim.AdamW([p for _,p in params],lr=2e-4,weight_decay=0.0,foreach=False)
        torch.manual_seed(SEED)
        model.train()
        losses=[]
        encoded=training[name]
        ordered=hashlib.sha256(json.dumps(encoded,separators=(",",":")).encode()).hexdigest()
        identity=dict(model=MODEL,revision=REVISION,base_weight_sha256=hashes,dtype="float32",
            arm=name,train_sha256=digest(train),development_sha256=digest(dev),
            ordered_tokens_sha256=ordered,initial_adapter_sha256=initial_hash,
            source_sha256=sources,seed=SEED,versions=dict(transformers=transformers.__version__,peft=peft.__version__))
        start=time.monotonic()
        for step,(ids,labels,prefix) in enumerate(encoded,start=1):
            optimizer.zero_grad(set_to_none=True)
            loss=assistant_loss(model,ids,labels,prefix)
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
        rows=evaluate()
        model.save_pretrained(out/name,selected_adapters=[name],safe_serialization=True)
        results[name]=dict(rationale_supervision=flag,train_steps=len(losses),losses=losses,
            training_ms=training_ms,supervised_tokens=sum(len(ids)-len(prefix) for ids,_,prefix in encoded),
            initial_adapter_sha256=initial_hash,final_adapter_sha256=final_hash,
            rows=rows,summary=summarize(rows),paired_vs_base=paired(baseline,rows))
        del optimizer
    for name,record in results.items():
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=record["final_adapter_sha256"]:
            raise RuntimeError("Another adapter changed during training")
    with model.disable_adapter():
        repeat=evaluate()
    if any({k:v for k,v in a.items() if k!="elapsed_ms"}!={k:v for k,v in b.items() if k!="elapsed_ms"}
            for a,b in zip(baseline,repeat)):
        raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_RATIONALE_TRANSFER",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,training_dtype="float32",seed=SEED,train_cases=len(train),
        development_cases=len(dev),train_sha256=digest(train),development_sha256=digest(dev),
        system_sha256=hashlib.sha256(SYSTEM.encode()).hexdigest(),source_sha256=sources,
        baseline=baseline,baseline_summary=summarize(baseline),adapters=results,
        paired_rationale_vs_answer_only=paired(results["answer_only"]["rows"],results["rationale"]["rows"]),
        frozen_baseline_reproduced=True,official_ai_rating=None,
        scope="Public synthetic generative transfer. Rationale correctness is not scored; target length and supervised token count differ. No independent score or deployment approval.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    return report

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("--per-domain",type=int,default=16)
    args=p.parse_args()
    run(args.output,args.per_domain)
