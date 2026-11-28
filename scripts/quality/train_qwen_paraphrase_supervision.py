"""Matched continued Qwen3 neural supervision on canonical or paraphrased inputs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from train_qwen_rationale_transfer import MODEL,REVISION,BASE_HASHES,LAYERS,SEED,ARMS,state_digest,visible
from qwen_rationale_curriculum import curriculum,controls,digest,encode,grade
from train_qwen_neural_pilot import file_digest
from evaluate_qwen_rationale_checkpoints import PINS,recover,padded_batch,completion_tokens
from qwen_answer_weighted import assistant_loss
import qwen_cross_domain_transfer as probe
import qwen_paraphrase_supervision as data
import qwen_neural_resume as resume

CAP=256
BATCH=4
CANDIDATES={"canonical_only":False,"paraphrase_mix":True}

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
    source={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="rationale").items()}
    source_hash=state_digest(source)
    if source_hash!=PINS["rationale"][1]:
        raise RuntimeError("Unexpected initial reasoning adapter")
    train_tasks,dev_tasks=data.suite(True),data.suite(False)
    train_turns={p.turns for t in train_tasks for p in t.probes()}
    probes=tuple(p for t in dev_tasks for p in t.probes())
    if train_turns & {p.turns for p in probes}:
        raise RuntimeError("Train/development prompt overlap")
    encoded={name:[data.encode_task(tok,t,f) for t,f in data.schedule(train_tasks,varied)] for name,varied in CANDIDATES.items()}
    c,p=encoded.values()
    if len(c)!=64 or len(p)!=64 or any(a[0][len(a[1]):]!=b[0][len(b[1]):] or a[2]!=b[2] for a,b in zip(c,p)):
        raise RuntimeError("Unequal target supervision")
    for name in CANDIDATES:
        model.add_adapter(name,config())
        set_peft_model_state_dict(model,source,adapter_name=name)
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=source_hash:
            raise RuntimeError("Unequal initial adapter")
    training={}
    for name in CANDIDATES:
        model.set_adapter(name)
        params=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
        if sum(p.numel() for _,p in params)!=229376 or any(f".{name}." not in n or "lora_" not in n for n,p in params):
            raise RuntimeError("Unexpected trainable parameters")
        optimizer=torch.optim.AdamW([p for _,p in params],lr=5e-5,weight_decay=0.,foreach=False)
        torch.manual_seed(data.SEED)
        model.train()
        losses=[]
        started=time.monotonic()
        identity=dict(model=MODEL,revision=REVISION,base_weight_sha256=hashes,
            source_parameter_sha256=source_hash,train_sha256=data.digest(train_tasks),
            development_sha256=data.digest(dev_tasks),arm=name,
            ordered_tokens_sha256=hashlib.sha256(json.dumps(encoded[name],separators=(",",":")).encode()).hexdigest(),
            seed=data.SEED,answer_weight=4.,learning_rate=5e-5,
            versions=dict(transformers=transformers.__version__,peft=peft.__version__),
            source_sha256={n:file_digest(Path(__file__).resolve().parent/n) for n in
                ("train_qwen_paraphrase_supervision.py","qwen_paraphrase_supervision.py",
                 "qwen_cross_domain_transfer.py","qwen_answer_weighted.py","qwen_neural_resume.py")})
        for step,(ids,prefix,mask) in enumerate(encoded[name],1):
            optimizer.zero_grad(set_to_none=True)
            loss=assistant_loss(model,ids,prefix,mask,4.)
            if not torch.isfinite(loss).item(): raise RuntimeError("Nonfinite loss")
            loss.backward()
            norm=torch.nn.utils.clip_grad_norm_([p for _,p in params],1.)
            if not torch.isfinite(norm).item(): raise RuntimeError("Nonfinite gradient")
            optimizer.step()
            losses.append(float(loss.detach()))
            del loss
            if any(not torch.isfinite(p).all().item() for _,p in params): raise RuntimeError("Nonfinite weights")
            if step%16==0:
                resume.save(out/"resume"/name/f"step-{step:03d}",params,optimizer,identity,step,losses)
                print(f"{name}: {step}/64",flush=True)
        final=state_digest(get_peft_model_state_dict(model,adapter_name=name))
        if final==source_hash: raise RuntimeError("No neural update")
        model.save_pretrained(out/"trained",selected_adapters=[name],safe_serialization=True)
        path=out/"trained"/name/"adapter_model.safetensors"
        from safetensors.torch import load_file
        if state_digest(load_file(str(path),device="cpu"))!=final: raise RuntimeError("Export mismatch")
        training[name]=dict(steps=len(losses),losses=losses,training_ms=round((time.monotonic()-started)*1000),
            source_tokens=sum(len(pr) for _,pr,_ in encoded[name]),
            supervised_tokens=sum(len(m) for _,_,m in encoded[name]),
            final_parameter_sha256=final,adapter_file_sha256=file_digest(path),identity=identity)
        del optimizer
    if state_digest(get_peft_model_state_dict(model,adapter_name="rationale"))!=source_hash:
        raise RuntimeError("Starting adapter changed")
    (out/"training.json").write_text(json.dumps(dict(status="TRAINED_UNEVALUATED",training=training),indent=2,allow_nan=False)+"\n")
    prefixes=[probe.prompt(tok,p) for p in probes]
    if len(set(map(tuple,prefixes)))!=len(prefixes): raise RuntimeError("Duplicate encoded prompts")
    def evaluate():
        rows=[]
        model.eval()
        for start in range(0,len(probes),BATCH):
            prs=prefixes[start:start+BATCH]
            ids,mask=padded_batch(prs,tok.eos_token_id)
            x=torch.tensor(ids,dtype=torch.long)
            before=time.monotonic()
            with torch.inference_mode():
                output=model.generate(input_ids=x,attention_mask=torch.tensor(mask,dtype=torch.long),
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=CAP,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
            elapsed=round((time.monotonic()-before)*1000)
            for j,pr in enumerate(prs):
                p=probes[start+j]
                tokens,done=completion_tokens(output[j,x.shape[1]:].tolist(),tok.eos_token_id,CAP)
                raw=tok.decode(tokens[:-1] if done else tokens,skip_special_tokens=False)
                text=visible(raw)
                if "<|im_start|>" in text or "<|im_end|>" in text:text=""
                correct,terminal=grade(text,p.answer)
                rows.append(dict(ident=p.ident,domain=p.domain,form=p.form,expected=p.answer,
                    complete=done,correct=done and correct,terminal_format=done and terminal,
                    tokens=len(tokens),batch_elapsed_ms=elapsed,visible_output=text[:3000],
                    output_sha256=hashlib.sha256(text.encode()).hexdigest(),
                    prompt_sha256=hashlib.sha256(json.dumps(pr,separators=(",",":")).encode()).hexdigest()))
        print(f"evaluated {len(rows)}",flush=True)
        return rows
    with model.disable_adapter(): baseline=evaluate()
    model.set_adapter("rationale")
    original=evaluate()
    results={}
    for name in CANDIDATES:
        model.set_adapter(name)
        rows=evaluate()
        results[name]=dict(rows=rows,summary=probe.summarize(rows,probes),
            paired_vs_source=probe.paired(original,rows),paired_vs_base=probe.paired(baseline,rows),training=training[name])
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=training[name]["final_parameter_sha256"]:
            raise RuntimeError("Evaluation changed weights")
    model.set_adapter("rationale")
    repeat=evaluate()
    stable=lambda rows:[{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(original)!=stable(repeat) or state_digest(get_peft_model_state_dict(model,adapter_name="rationale"))!=source_hash:
        raise RuntimeError("Starting adapter not reproduced")
    report=dict(status="COMPLETE_NEURAL_PARAPHRASE_SUPERVISION",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,source_parameter_sha256=source_hash,
        training_suite_sha256=data.digest(train_tasks),development_suite_sha256=data.digest(dev_tasks),
        cap=CAP,baseline=baseline,baseline_summary=probe.summarize(baseline,probes),
        source=original,source_summary=probe.summarize(original,probes),adapters=results,
        paired_varied_vs_canonical=probe.paired(results["canonical_only"]["rows"],results["paraphrase_mix"]["rows"]),
        source_reproduced=True,official_ai_rating=None,
        scope="Matched continued neural training from exact rationale checkpoint. 32 training problems, 64 updates per arm; same target sequences, canonical repetition vs varied wording. 16 fresh public underlying questions with two dependent forms each. Input lengths differ; no broad retention, independent score, or production activation.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("checkpoints",type=Path)
    a=p.parse_args()
    run(a.output,a.checkpoints)
