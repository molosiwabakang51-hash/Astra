"""Evaluate the two final checkpoints of run 46049366258 without retraining.

Read-only inference recovery: do not restore optimizer or require identical
CPU hardware. Verify the pinned manifest and tensor digests, sources and
ordered training inputs. All inference arms use identical padded batches.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from train_qwen_rationale_transfer import (MODEL, REVISION, BASE_HASHES, LAYERS,
    SEED, ARMS, CAP, state_digest, visible, summarize, paired)
from qwen_rationale_curriculum import curriculum, controls, digest, encode, grade
from train_qwen_neural_pilot import file_digest

PINS={
    "answer_only":("c4609967e6bc78f7c5ff8437ed19b9e996ce4d9d0846243b4d92297a47398083",
                   "929d1de7b17b13c3195894b3b93e21435a8b24dea4f364622304a7d0aa34528a"),
    "rationale":("a73a61c960b71980e580956ce82360ba68f7eeed0945456ea673a26b11d7954d",
                 "9f9ad1f59514acbf329b40c68fa1dbb864f8ba12edfc978f04953000a9637e40"),
}
BATCH=4

def padded_batch(prefixes,pad):
    if not prefixes or any(not p for p in prefixes):
        raise ValueError("Empty batch or prompt")
    n=max(map(len,prefixes))
    return ([[pad]*(n-len(p))+p for p in prefixes],
            [[0]*(n-len(p))+[1]*len(p) for p in prefixes])

def completion_tokens(tokens,eos,cap=CAP):
    if not tokens or len(tokens)>cap:
        raise ValueError("Invalid generated token count")
    if eos not in tokens:
        return tokens,False
    stop=tokens.index(eos)+1
    if any(t!=eos for t in tokens[stop:]):
        raise ValueError("Unexpected token after EOS padding")
    return tokens[:stop],True

def recover(root,arm,expected_identity,initial):
    import torch
    from safetensors.torch import load_file
    manifest=root/"manifest.json"
    path=root/"state.safetensors"
    if (not manifest.is_file() or not path.is_file() or manifest.stat().st_size>65536
            or not 0<path.stat().st_size<=64*1024*1024):
        raise ValueError("Missing/oversized checkpoint")
    if file_digest(manifest)!=PINS[arm][0]:
        raise ValueError("Unpinned checkpoint manifest")
    m=json.loads(manifest.read_text())
    if (m["identity"]!=expected_identity or m["completed_steps"]!=96
            or m["format"]!="ASTRA_CPU_ADAMW_RESUME_V1"
            or m["status"]!="UNEVALUATED_NEURAL_CHECKPOINT"
            or file_digest(path)!=m["state_sha256"]):
        raise ValueError("Checkpoint identity mismatch")
    names=m["parameter_names"]
    if len(names)!=16 or len(set(names))!=16 or any(not n.endswith("."+arm+".weight") for n in names):
        raise ValueError("Invalid parameter names")
    data=load_file(str(path),device="cpu")
    expected={"cpu_rng"}|{f"{i}.{k}" for i in range(len(names)) for k in ("weight","step","exp_avg","exp_avg_sq")}
    if set(data)!=expected:
        raise ValueError("Unexpected checkpoint tensor set")
    state={}
    for i,n in enumerate(names):
        key=n.removesuffix("."+arm+".weight")+".weight"
        if key not in initial:
            raise ValueError("Unexpected adapter parameter")
        t=data[f"{i}.weight"]
        if t.shape!=initial[key].shape or t.dtype!=torch.float32 or not torch.isfinite(t).all().item():
            raise ValueError("Invalid saved parameter")
        state[key]=t
    if set(state)!=set(initial) or state_digest(state)!=PINS[arm][1]:
        raise ValueError("Saved adapter digest mismatch")
    return state,m

def run(out,checkpoints):
    import torch
    import transformers
    import peft
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import LoraConfig,get_peft_model,get_peft_model_state_dict,set_peft_model_state_dict
    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    out.mkdir(parents=True,exist_ok=False)
    train=curriculum(True,16)
    dev=curriculum(False,8)+controls()
    root=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    hashes={p.name:file_digest(p) for p in sorted(root.glob("*.safetensors"))}
    if root.name!=REVISION or hashes!=BASE_HASHES:
        raise RuntimeError("Unexpected base checkpoint")
    tok=AutoTokenizer.from_pretrained(root,trust_remote_code=False,local_files_only=True)
    base=AutoModelForCausalLM.from_pretrained(root,torch_dtype=torch.float32,trust_remote_code=False,
        local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if (base.config.model_type!="qwen3" or base.config.num_hidden_layers!=28
        or base.config.hidden_size!=2048 or tok.eos_token_id!=151645):
        raise RuntimeError("Unexpected architecture/tokenizer")
    def config():
        return LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,lora_dropout=0.0,bias="none",
            target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers")
    model=get_peft_model(base,config(),adapter_name="answer_only")
    model.add_adapter("rationale",config())
    initial={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="answer_only").items()}
    initial_hash=state_digest(initial)
    sources={name:file_digest(Path(__file__).resolve().parent/name) for name in (
        "train_qwen_rationale_transfer.py","qwen_rationale_curriculum.py",
        "qwen_neural_curriculum.py","train_qwen_neural_pilot.py","qwen_neural_resume.py")}
    manifests={}
    for arm,flag in ARMS.items():
        ordered=hashlib.sha256(json.dumps([encode(tok,e,flag) for e in train],
                                         separators=(",",":")).encode()).hexdigest()
        identity=dict(model=MODEL,revision=REVISION,base_weight_sha256=hashes,dtype="float32",
            arm=arm,train_sha256=digest(train),development_sha256=digest(dev),
            ordered_tokens_sha256=ordered,initial_adapter_sha256=initial_hash,source_sha256=sources,
            seed=SEED,versions=dict(transformers=transformers.__version__,peft=peft.__version__))
        state,manifest=recover(checkpoints/arm/"step-096",arm,identity,initial)
        set_peft_model_state_dict(model,state,adapter_name=arm)
        if state_digest(get_peft_model_state_dict(model,adapter_name=arm))!=PINS[arm][1]:
            raise RuntimeError("Loaded weights do not match saved weights")
        manifests[arm]=manifest
    development=[encode(tok,e,False)[2] for e in dev]

    def evaluate():
        model.eval()
        rows=[]
        for start in range(0,len(dev),BATCH):
            prefixes=development[start:start+BATCH]
            ids,mask=padded_batch(prefixes,tok.eos_token_id)
            x=torch.tensor(ids,dtype=torch.long)
            attn=torch.tensor(mask,dtype=torch.long)
            before=time.monotonic()
            with torch.inference_mode():
                output=model.generate(input_ids=x,attention_mask=attn,
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=CAP,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
            elapsed=round((time.monotonic()-before)*1000)
            for j,prefix in enumerate(prefixes):
                tokens,complete=completion_tokens(output[j,x.shape[1]:].tolist(),tok.eos_token_id)
                content=visible(tok.decode(tokens,skip_special_tokens=True))
                e=dev[start+j]
                correct,terminal=grade(content,e.answer)
                rows.append(dict(ident=str(start+j),domain=e.domain,complete=complete,
                    correct=complete and correct,terminal_format=complete and terminal,
                    tokens=len(tokens),batch_elapsed_ms=elapsed,expected=e.answer,visible_output=content[:2000],
                    output_sha256=hashlib.sha256(content.encode()).hexdigest(),
                    prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest()))
            print(f"evaluated {len(rows)}/{len(dev)}",flush=True)
        return rows
    with model.disable_adapter():
        baseline=evaluate()
    results={}
    for arm in ARMS:
        model.set_adapter(arm)
        rows=evaluate()
        results[arm]=dict(rows=rows,summary=summarize(rows),paired_vs_base=paired(baseline,rows),
                          checkpoint_sha256=manifests[arm]["state_sha256"],parameter_sha256=PINS[arm][1])
    with model.disable_adapter():
        repeated=evaluate()
    def stable(rows):
        return [{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(baseline)!=stable(repeated):
        raise RuntimeError("Frozen batched baseline changed")
    report=dict(status="COMPLETE_RECOVERED_RATIONALE_TRANSFER",source_run=46049366258,
        model=MODEL,revision=REVISION,base_weight_sha256=hashes,development_sha256=digest(dev),
        batch_size=BATCH,generation_cap=CAP,baseline=baseline,baseline_summary=summarize(baseline),
        adapters=results,paired_rationale_vs_answer_only=paired(results["answer_only"]["rows"],results["rationale"]["rows"]),
        frozen_baseline_reproduced=True,official_ai_rating=None,
        scope="Recovered completed weights; no retraining. Matched batched FP32 generation may differ numerically from prior serial generation. Public development cases only; answer grading does not certify rationales.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("checkpoints",type=Path)
    a=p.parse_args()
    run(a.output,a.checkpoints)
