"""Evaluate exact exported #550 adapters with 256 tokens, without retraining.
The derived 128-token prefix is compared with the archived actual 128-token
run. A mismatch is disclosed, never treated as a reproduced causal cap effect.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from train_qwen_answer_weighted import MODEL,REVISION,BASE_HASHES,LAYERS,SEED,ARMS,state_digest,visible,summarize,paired
from train_qwen_neural_pilot import file_digest
from qwen_answer_weighted import suites,digest,encode
from qwen_rationale_curriculum import grade
from evaluate_qwen_rationale_checkpoints import padded_batch,completion_tokens
from qwen_weighted_budget import PINS,TRAINING_SHA,REPORT_SHA,SHORT,LONG,pinned_json,projected_short,compare_archive

BATCH=4

def run(out,root,archive):
    import torch
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import LoraConfig,get_peft_model,get_peft_model_state_dict,set_peft_model_state_dict
    from safetensors.torch import load_file
    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    out.mkdir(parents=True,exist_ok=False)
    train,dev=suites()
    meta=pinned_json(root/"training.json",TRAINING_SHA)
    old=pinned_json(archive/"report.json",REPORT_SHA)
    for record in (meta,old):
        if record["model"]!=MODEL or record["revision"]!=REVISION or record["base_weight_sha256"]!=BASE_HASHES or record["train_sha256"]!=digest(train) or record["development_sha256"]!=digest(dev):
            raise RuntimeError("Wrong source model or suite")
        if any(file_digest(Path(__file__).resolve().parent/n)!=h for n,h in record["source_sha256"].items()):
            raise RuntimeError("Source changed since training")
    if old["status"]!="COMPLETE_ANSWER_WEIGHTED_TRANSFER" or not old["frozen_baseline_reproduced"] or meta["status"]!="TRAINED_UNEVALUATED":
        raise RuntimeError("Unexpected training evidence")
    local=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    hashes={p.name:file_digest(p) for p in sorted(local.glob("*.safetensors"))}
    if local.name!=REVISION or hashes!=BASE_HASHES: raise RuntimeError("Wrong base weights")
    tok=AutoTokenizer.from_pretrained(local,trust_remote_code=False,local_files_only=True)
    base=AutoModelForCausalLM.from_pretrained(local,torch_dtype=torch.float32,
        trust_remote_code=False,local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if base.config.model_type!="qwen3" or base.config.num_hidden_layers!=28 or base.config.hidden_size!=2048 or tok.eos_token_id!=151645:
        raise RuntimeError("Unexpected architecture")
    def config():
        return LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,lora_dropout=0.,bias="none",
            target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers")
    model=get_peft_model(base,config(),adapter_name="ordinary")
    model.add_adapter("weighted",config())
    for name,weight in ARMS.items():
        path=root/name/name/"adapter_model.safetensors"
        pin,raw_pin=PINS[name]
        m=meta["adapters"][name]
        if m["answer_weight"]!=weight or m["train_steps"]!=96 or m["final_adapter_sha256"]!=pin or m["adapter_file_sha256"]!=raw_pin or not 0<path.stat().st_size<=16*1024*1024 or file_digest(path)!=raw_pin:
            raise RuntimeError("Wrong adapter identity")
        state=load_file(str(path),device="cpu")
        initial=get_peft_model_state_dict(model,adapter_name=name)
        if set(state)!=set(initial) or len(state)!=16 or sum(t.numel() for t in state.values())!=229376 or any(t.shape!=initial[k].shape or t.dtype!=torch.float32 or not torch.isfinite(t).all().item() for k,t in state.items()) or state_digest(state)!=pin:
            raise RuntimeError("Invalid tensor state")
        set_peft_model_state_dict(model,state,adapter_name=name)
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=pin:
            raise RuntimeError("Adapter load differs")
    prompts=[encode(tok,e)[1] for e in dev]
    def row(i,prefix,tokens,done):
        content=visible(tok.decode(tokens,skip_special_tokens=True))
        e=dev[i]
        correct,terminal=grade(content,e.answer)
        return dict(ident=str(i),domain=e.domain,complete=done,correct=done and correct,
            terminal_format=done and terminal,tokens=len(tokens),expected=e.answer,
            visible_output=content[:4000],output_sha256=hashlib.sha256(content.encode()).hexdigest(),
            prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest())
    def evaluate():
        model.eval()
        long=[]; short=[]
        for start in range(0,len(dev),BATCH):
            p=prompts[start:start+BATCH]
            ids,mask=padded_batch(p,tok.eos_token_id)
            x=torch.tensor(ids,dtype=torch.long)
            before=time.monotonic()
            with torch.inference_mode():
                outputs=model.generate(input_ids=x,attention_mask=torch.tensor(mask,dtype=torch.long),
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=LONG,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
            elapsed=round((time.monotonic()-before)*1000)
            for j,pr in enumerate(p):
                tokens,done=completion_tokens(outputs[j,x.shape[1]:].tolist(),tok.eos_token_id,LONG)
                st,sd=projected_short(tokens,done,tok.eos_token_id)
                long.append(dict(row(start+j,pr,tokens,done),batch_elapsed_ms=elapsed))
                short.append(row(start+j,pr,st,sd))
            print(f"evaluated {len(long)}/{len(dev)}",flush=True)
        return long,short
    def result(long,short,archived):
        return dict(long=long,projected_short=short,long_summary=summarize(long),
            archived_short_summary=summarize(archived),projected_short_summary=summarize(short),
            archived_prefix_reproduction=compare_archive(short,archived),
            projected_budget_delta=paired(short,long),archived_budget_delta=paired(archived,long))
    with model.disable_adapter(): long,short=evaluate()
    baseline=result(long,short,old["baseline"])
    arms={}
    for name in ARMS:
        model.set_adapter(name)
        long,short=evaluate()
        arms[name]=result(long,short,old["adapters"][name]["rows"])
        arms[name]["parameter_sha256"]=PINS[name][0]
        arms[name]["paired_vs_long_base"]=paired(baseline["long"],long)
    for name in ARMS:
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=PINS[name][0]:
            raise RuntimeError("Evaluation changed parameters")
    with model.disable_adapter(): repeat,_=evaluate()
    stable=lambda rows:[{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(repeat)!=stable(baseline["long"]): raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_WEIGHTED_BUDGET_RECOVERY",source_run=46144851739,model=MODEL,
        revision=REVISION,base_weight_sha256=hashes,development_sha256=digest(dev),caps=[SHORT,LONG],
        baseline=baseline,adapters=arms,paired_weighted_vs_ordinary=paired(arms["ordinary"]["long"],arms["weighted"]["long"]),
        frozen_baseline_reproduced=True,official_ai_rating=None,
        scope="Actual 256-token generations; 128-token prefixes compared against archived actual outputs and metrics. No assertion of exact token-prefix reproduction because source did not retain token IDs. All 44 reused public questions; no retraining or deployment.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("output","trained","archive"):p.add_argument(name,type=Path)
    a=p.parse_args()
    run(a.output,a.trained,a.archive)
