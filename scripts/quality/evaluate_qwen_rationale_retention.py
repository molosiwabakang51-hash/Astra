"""Retention transfer of the exact #541 recovered reasoning weights.
Compare strict instruction compliance and terminal answer content separately.
No retraining, candidate selection, or independent rating.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from train_qwen_rationale_transfer import MODEL, REVISION, BASE_HASHES, LAYERS, SEED, ARMS, state_digest
from qwen_rationale_curriculum import curriculum, controls, digest, encode
from train_qwen_neural_pilot import file_digest
from evaluate_qwen_rationale_checkpoints import PINS, recover, padded_batch, completion_tokens
from evaluate_qwen_full_answer_retention import reference_nll
import qwen_full_answer_retention as suite
from qwen_rationale_retention import score, summarize, paired

CAP=96
BATCH=4

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
    cases=suite.cases()
    if {c.question for c in cases} & {e.question for e in train}:
        raise RuntimeError("Training prompt overlap")
    prefixes=[suite.prefix(tok,c) for c in cases]
    if len({tuple(p) for p in prefixes})!=len(cases):
        raise RuntimeError("Duplicate tokenized prompts")
    references=[tok.encode(c.aliases[0],add_special_tokens=False)+[tok.eos_token_id] for c in cases]
    if any(not 2<=len(s)<=32 for s in references):
        raise RuntimeError("Invalid reference length")

    def evaluate():
        model.eval()
        rows=[]
        with torch.inference_mode():
            for start in range(0,len(cases),BATCH):
                p=prefixes[start:start+BATCH]
                ids,mask=padded_batch(p,tok.eos_token_id)
                x=torch.tensor(ids,dtype=torch.long)
                before=time.monotonic()
                output=model.generate(input_ids=x,attention_mask=torch.tensor(mask,dtype=torch.long),
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=CAP,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
                elapsed=round((time.monotonic()-before)*1000)
                for j,pr in enumerate(p):
                    i=start+j
                    c=cases[i]
                    tokens,done=completion_tokens(output[j,x.shape[1]:].tolist(),tok.eos_token_id,CAP)
                    raw=tok.decode(tokens[:-1] if done else tokens,skip_special_tokens=False)
                    strict,content=score(raw,c,done)
                    rows.append(dict(ident=c.ident,domain=c.domain,complete=done,correct=strict,
                        content_correct=content,visible_output=raw[:4000],expected=list(c.aliases),
                        tokens=len(tokens),batch_elapsed_ms=elapsed,
                        reference_nll=reference_nll(model,pr,references[i]),
                        prompt_sha256=hashlib.sha256(json.dumps(pr,separators=(",",":")).encode()).hexdigest()))
                print(f"evaluated {len(rows)}/{len(cases)}",flush=True)
        return rows

    with model.disable_adapter():
        baseline=evaluate()
    results={}
    for arm in ARMS:
        model.set_adapter(arm)
        rows=evaluate()
        results[arm]=dict(rows=rows,summary=summarize(rows),paired_vs_base=paired(baseline,rows),
            parameter_sha256=PINS[arm][1],checkpoint_sha256=manifests[arm]["state_sha256"])
    for arm in ARMS:
        if state_digest(get_peft_model_state_dict(model,adapter_name=arm))!=PINS[arm][1]:
            raise RuntimeError("Evaluation altered adapter")
    with model.disable_adapter():
        repeat=evaluate()
    def stable(rows):
        return [{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(baseline)!=stable(repeat):
        raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_RATIONALE_KNOWLEDGE_RETENTION",source_run=46049366258,
        model=MODEL,revision=REVISION,base_weight_sha256=hashes,
        suite_sha256=suite.suite_digest(cases),generation_cap=CAP,batch_size=BATCH,
        baseline=baseline,baseline_summary=summarize(baseline),adapters=results,
        paired_rationale_vs_answer_only=paired(results["answer_only"]["rows"],results["rationale"]["rows"]),
        frozen_baseline_reproduced=True,official_ai_rating=None,
        scope="32 previously published retention probes, new to this checkpoint evaluation; strict bare-answer and terminal-content scores separate. Reference NLL measures canonical short answer including EOS, not truth. Matched FP32 CPU only.")
    (out/"report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False,allow_nan=False)+"\n",encoding="utf-8")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("checkpoints",type=Path)
    a=p.parse_args()
    run(a.output,a.checkpoints)
