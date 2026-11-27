"""Bounded CPU LoRA pilot on official Qwen3-1.7B, not an Android release.

Train assistant completion tokens only. Development data are synthetic and
public. No held-out independent assessment, user histories, or model calls.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import time

from qwen_neural_curriculum import curriculum, controls, digest, grade, formatted, token_example

MODEL = "Qwen/Qwen3-1.7B"
REVISION = "8d4744f9e13072f4920c326350fa81eedb74eae9"
SEED = 712829
MAX_LENGTH = 256
GEN_CAP = 64
LAYERS = [24,25,26,27]

def file_digest(path):
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda:stream.read(1024*1024),b""):
            h.update(block)
    return h.hexdigest()

def paired_report(before,after):
    if not before or set(before)!=set(after):
        raise ValueError("Evaluation must preserve the same fixed cases")
    results={}
    for domain in sorted({r["domain"] for r in before.values()}):
        ids=[k for k,r in before.items() if r["domain"]==domain]
        if any(after[k]["domain"]!=domain for k in ids):
            raise ValueError("Case domain mismatch")
        results[domain]={"attempted":len(ids),
                         "baseline_correct":sum(before[k]["correct"] for k in ids),
                         "adapted_correct":sum(after[k]["correct"] for k in ids),
                         "baseline_format":sum(before[k]["formatted"] for k in ids),
                         "adapted_format":sum(after[k]["formatted"] for k in ids)}
    return {"by_domain":results,
            "attempted":len(before),
            "baseline_correct":sum(r["correct"] for r in before.values()),
            "adapted_correct":sum(r["correct"] for r in after.values()),
            "improved":sum(not before[k]["correct"] and after[k]["correct"] for k in before),
            "regressed":sum(before[k]["correct"] and not after[k]["correct"] for k in before),
            "both_correct":sum(before[k]["correct"] and after[k]["correct"] for k in before)}

def run(out, steps=32):
    if type(steps) is not int or not 4<=steps<=64 or steps%4:
        raise ValueError("Use 4..64 balanced steps divisible by four")
    # Keep dependency imports out of lightweight evaluator tests.
    import torch
    import transformers
    import peft
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer, AutoModelForCausalLM
    from peft import LoraConfig, get_peft_model

    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    out=Path(out)
    out.mkdir(parents=True,exist_ok=False)
    train=curriculum(True,steps)
    dev=curriculum(False,16)+controls()
    if {e.question for e in train}&{e.question for e in dev}:
        raise RuntimeError("Train/development overlap")
    snapshot=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    if snapshot.name!=REVISION:
        raise RuntimeError("Unexpected model revision")
    weights=sorted(snapshot.glob("*.safetensors"))
    if len(weights)!=2:
        raise RuntimeError("Expected pinned two-shard model")
    weight_hashes={p.name:file_digest(p) for p in weights}
    tokenizer=AutoTokenizer.from_pretrained(snapshot,trust_remote_code=False,local_files_only=True)
    base=AutoModelForCausalLM.from_pretrained(snapshot,torch_dtype=torch.bfloat16,
        trust_remote_code=False,local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if (base.config.model_type!="qwen3" or base.config.num_hidden_layers!=28 or
            base.config.hidden_size!=2048 or tokenizer.eos_token_id!=151645):
        raise RuntimeError("Unexpected base architecture/tokenizer")
    model=get_peft_model(base,LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,
        lora_dropout=0.0,bias="none",target_modules=["q_proj","v_proj"],
        layers_to_transform=LAYERS,layers_pattern="layers"))
    params=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
    if not params or any("lora_" not in n or not any(f".layers.{i}." in n for i in LAYERS)
                         for n,_ in params):
        raise RuntimeError("Unexpected trainable parameters")
    param_count=sum(p.numel() for _,p in params)
    if param_count>1_000_000:
        raise RuntimeError("Unexpected adapter size")

    def parameter_digest():
        h=hashlib.sha256()
        for name,p in params:
            h.update(name.encode())
            h.update(p.detach().float().cpu().contiguous().numpy().tobytes())
        return h.hexdigest()

    def evaluate():
        model.eval()
        rows={}
        for index,e in enumerate(dev):
            _,_,prefix=token_example(tokenizer,e,MAX_LENGTH)
            # Only the generation prefix reaches inference; targets never do.
            tokens=torch.tensor([prefix],dtype=torch.long)
            start=time.monotonic()
            with torch.inference_mode():
                output=model.generate(input_ids=tokens,attention_mask=torch.ones_like(tokens),
                    do_sample=False,temperature=None,top_p=None,top_k=None,
                    max_new_tokens=GEN_CAP,pad_token_id=tokenizer.eos_token_id,
                    eos_token_id=tokenizer.eos_token_id,use_cache=True)
            generated=output[0,len(prefix):].tolist()
            complete=bool(generated and generated[-1]==tokenizer.eos_token_id)
            visible=tokenizer.decode(generated,skip_special_tokens=True).strip()
            rows[str(index)]={"domain":e.domain,"complete":complete,
                "correct":complete and grade(visible,e.answer),
                "formatted":complete and formatted(visible),"tokens":len(generated),
                "elapsed_ms":round((time.monotonic()-start)*1000),
                "output_sha256":hashlib.sha256(visible.encode()).hexdigest()}
        return rows

    # Compare the exact same base instance with adapter disabled/enabled.
    with model.disable_adapter():
        before=evaluate()
    initial=parameter_digest()
    encoded=[token_example(tokenizer,e,MAX_LENGTH) for e in train]
    optimizer=torch.optim.AdamW([p for _,p in params],lr=2e-4,weight_decay=0.0)
    losses=[]
    model.train()
    start=time.monotonic()
    for step,(ids,labels,_) in enumerate(encoded):
        optimizer.zero_grad(set_to_none=True)
        x=torch.tensor([ids],dtype=torch.long)
        y=torch.tensor([labels],dtype=torch.long)
        result=model(input_ids=x,attention_mask=torch.ones_like(x),labels=y,use_cache=False)
        loss=result.loss
        if not torch.isfinite(loss).item():
            raise RuntimeError("Nonfinite training loss")
        loss.backward()
        norm=torch.nn.utils.clip_grad_norm_([p for _,p in params],1.0)
        if not torch.isfinite(norm).item():
            raise RuntimeError("Nonfinite adapter gradient")
        optimizer.step()
        losses.append(float(loss.detach()))
        del result,loss
        if any(not torch.isfinite(p).all().item() for _,p in params):
            raise RuntimeError("Nonfinite adapter weights")
        print(f"adapter step {step+1}/{len(encoded)} completed",flush=True)
    train_ms=round((time.monotonic()-start)*1000)
    final=parameter_digest()
    if initial==final:
        raise RuntimeError("No neural parameter update")
    after=evaluate()
    # Verify frozen baseline reproducibility after training, not just
    # requires_grad flags. Any output change fails the experiment closed.
    with model.disable_adapter():
        repeated=evaluate()
    if any(before[k]["output_sha256"]!=repeated[k]["output_sha256"] or
           before[k]["complete"]!=repeated[k]["complete"] for k in before):
        raise RuntimeError("Frozen baseline changed after adapter training")
    model.save_pretrained(out/"adapter",safe_serialization=True)
    report={"status":"COMPLETE_NEURAL_ADAPTATION_PILOT","model":MODEL,"revision":REVISION,
        "base_weight_sha256":weight_hashes,"train_sha256":digest(train),"development_sha256":digest(dev),
        "train_examples":len(train),"train_steps":len(losses),"losses":losses,"training_ms":train_ms,
        "trainable_parameters":param_count,"target_layers":LAYERS,"rank":8,"seed":SEED,
        "initial_adapter_sha256":initial,"final_adapter_sha256":final,"frozen_baseline_reproduced":True,
        "versions":{"torch":torch.__version__,"transformers":transformers.__version__,"peft":peft.__version__},
        "paired":paired_report(before,after),"baseline":before,"adapted":after,"official_ai_rating":None,
        "scope":"Public synthetic developmental transfer, BF16 CPU, last four attention layers only. "
                "Not an independent score, production GGUF test or proof of broad intelligence. "
                "Final-answer grading does not certify the rationale."}
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    return report

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output",type=Path)
    parser.add_argument("--steps",type=int,default=32)
    args=parser.parse_args()
    run(args.output,args.steps)
