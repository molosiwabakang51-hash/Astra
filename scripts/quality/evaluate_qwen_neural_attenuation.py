"""Actual Qwen3 fixed half-delta reasoning transfer experiment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from train_qwen_rationale_transfer import MODEL, REVISION, BASE_HASHES, LAYERS, SEED, ARMS, state_digest, visible, summarize, paired
from qwen_rationale_curriculum import curriculum, controls, digest, encode, grade
from train_qwen_neural_pilot import file_digest
from evaluate_qwen_rationale_checkpoints import PINS, recover, padded_batch, completion_tokens
from qwen_neural_attenuation import attenuate, verify

CAP=128
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
    # Fixed, preregistered 50% strength; no per-case or outcome-based selection.
    full={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="rationale").items()}
    candidate=attenuate(full)
    model.add_adapter("attenuated",config())
    set_peft_model_state_dict(model,candidate,adapter_name="attenuated")
    loaded=get_peft_model_state_dict(model,adapter_name="attenuated")
    verification=verify(full,loaded)
    count=0
    for module in model.modules():
        if hasattr(module,"lora_A") and "attenuated" in module.lora_A:
            if module.scaling.get("attenuated")!=2. or module.scaling.get("rationale")!=2.:
                raise RuntimeError("Actual PEFT scaling mismatch")
            count+=1
    if count!=8 or sum(t.numel() for t in loaded.values())!=229376:
        raise RuntimeError("Unexpected candidate size")
    half_hash=state_digest(loaded)
    if half_hash==PINS["rationale"][1]:
        raise RuntimeError("Candidate equals source")
    model.save_pretrained(out/"candidate",selected_adapters=["attenuated"],safe_serialization=True)
    path=out/"candidate"/"attenuated"/"adapter_model.safetensors"
    from safetensors.torch import load_file
    if state_digest(load_file(str(path),device="cpu"))!=half_hash:
        raise RuntimeError("Saved weights differ")
    (out/"intervention.json").write_text(json.dumps(dict(status="UNEVALUATED_HALF_DELTA_ADAPTER",
        coefficient=.5,source_parameter_sha256=PINS["rationale"][1],
        candidate_parameter_sha256=half_hash,candidate_file_sha256=file_digest(path),**verification),
        indent=2,allow_nan=False)+"\n")
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
                tokens,complete=completion_tokens(output[j,x.shape[1]:].tolist(),tok.eos_token_id,CAP)
                content=visible(tok.decode(tokens,skip_special_tokens=True))
                e=dev[start+j]
                correct,terminal=grade(content,e.answer)
                rows.append(dict(ident=str(start+j),domain=e.domain,complete=complete,
                    correct=complete and correct,terminal_format=complete and terminal,
                    candidate_correct=correct,tokens=len(tokens),batch_elapsed_ms=elapsed,
                    expected=e.answer,visible_output=content[:3000],
                    output_sha256=hashlib.sha256(content.encode()).hexdigest(),
                    prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest()))
            print(f"evaluated {len(rows)}/{len(dev)}",flush=True)
        return rows

    with model.disable_adapter():
        baseline=evaluate()
    results={}
    for arm in ("rationale","attenuated"):
        model.set_adapter(arm)
        rows=evaluate()
        results[arm]=dict(rows=rows,summary=summarize(rows),paired_vs_base=paired(baseline,rows),
            parameter_sha256=half_hash if arm=="attenuated" else PINS[arm][1])
    for arm in (*ARMS,"attenuated"):
        expected=half_hash if arm=="attenuated" else PINS[arm][1]
        if state_digest(get_peft_model_state_dict(model,adapter_name=arm))!=expected:
            raise RuntimeError("Evaluation altered factors")
    with model.disable_adapter():
        repeat=evaluate()
    def stable(rows):
        return [{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(baseline)!=stable(repeat):
        raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_FIXED_NEURAL_DELTA_ATTENUATION",source_run=46049366258,
        model=MODEL,revision=REVISION,base_weight_sha256=hashes,development_sha256=digest(dev),
        generation_cap=CAP,batch_size=BATCH,baseline=baseline,baseline_summary=summarize(baseline),
        adapters=results,paired_half_vs_full=paired(results["rationale"]["rows"],results["attenuated"]["rows"]),
        intervention=json.loads((out/"intervention.json").read_text()),frozen_baseline_reproduced=True,
        official_ai_rating=None,scope="Fixed 50% rank-8 weight-delta attenuation, reused public 52-case suite, same 128-token cap across arms. No retraining, held-out score, general retention or deployment qualification.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("checkpoints",type=Path)
    a=p.parse_args()
    run(a.output,a.checkpoints)
