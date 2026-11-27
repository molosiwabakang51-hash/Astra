"""Matched supervised reasoning versus frozen-teacher retention regularization.
No external teacher, answer tools, independent score or deployment.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time

from qwen_calibration_loss import choice_loss
import qwen_neural_resume as resume
import qwen_retention_anchor as anchor
from qwen_contrastive_curriculum import training_cases
from qwen_transfer_choices import cases, prompt, score, summarize, suite_digest, LABELS
from evaluate_qwen_transfer_logits import MODEL, REVISION, sha, paired

SEED = 41087253
EXPECTED_BASE = {
    "model-00001-of-00002.safetensors": "169ad53ec313c3a34b06c0809216e4fc072cce444a5d4ff2b59690d064130ed5",
    "model-00002-of-00002.safetensors": "912becff8d60672aa8628ef08c05898d9adf17c2ad4ae3caf99b065622fdeff9",
}
LAYERS=[24,25,26,27]
OBJECTIVES={"ordinary":0.0,"anchored":2.0}
SYSTEM="Answer accurately using the given information. Choose the best option."

def state_digest(state):
    h=hashlib.sha256()
    for name,p in sorted(state.items()):
        h.update(name.encode())
        h.update(p.detach().float().cpu().contiguous().numpy().tobytes())
    return h.hexdigest()

def run(out, resume_ordinary=None, resume_anchored=None):
    recovery={"ordinary":resume_ordinary,"anchored":resume_anchored}
    import torch
    import transformers
    import peft
    from transformers import AutoTokenizer, AutoModelForCausalLM
    from huggingface_hub import snapshot_download
    from peft import LoraConfig, get_peft_model, get_peft_model_state_dict, set_peft_model_state_dict

    torch.manual_seed(SEED)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    out.mkdir(parents=True,exist_ok=False)
    root=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    hashes={p.name:sha(p) for p in sorted(root.glob("*.safetensors"))}
    if root.name!=REVISION or hashes!=EXPECTED_BASE:
        raise RuntimeError("Base checkpoint differs from evaluated #537")
    tok=AutoTokenizer.from_pretrained(root,trust_remote_code=False,local_files_only=True)
    base=AutoModelForCausalLM.from_pretrained(root,torch_dtype=torch.float32,
        trust_remote_code=False,local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if (base.config.model_type!="qwen3" or base.config.num_hidden_layers!=28 or
            base.config.hidden_size!=2048):
        raise RuntimeError("Unexpected base architecture")
    labels=[tok.encode(l,add_special_tokens=False) for l in LABELS]
    if any(len(t)!=1 for t in labels) or len({t[0] for t in labels})!=4:
        raise RuntimeError("Distinct single-token labels required")
    label_ids=[t[0] for t in labels]
    train=training_cases()
    dev=cases()
    if {c.question for c in train}&{c.question for c in dev}:
        raise RuntimeError("Train/evaluation overlap")

    def prepare(suite):
        rows=[]
        for case in suite:
            messages=[{"role":"system","content":SYSTEM},{"role":"user","content":prompt(case)}]
            ids=tok.apply_chat_template(messages,tokenize=True,add_generation_prompt=True,enable_thinking=False)
            if not ids or len(ids)>512:
                raise RuntimeError("Prompt exceeds bounded context")
            rows.append((case,ids))
        return rows
    train_rows=prepare(train)
    dev_rows=prepare(dev)
    anchor.validate_splits()
    anchor_train=anchor.encode(tok,anchor.TRAIN)
    anchor_dev=anchor.encode(tok,anchor.DEV)
    if len(anchor_train)!=len(train_rows) or {tuple(x) for x in anchor_train}&{tuple(x) for x in anchor_dev}:
        raise RuntimeError("Invalid anchor split")
    token_identity=hashlib.sha256(json.dumps(
        [(case.ident,case.correct,ids) for case,ids in train_rows],
        separators=(",",":")).encode()).hexdigest()
    code_root=Path(__file__).resolve().parent
    source_identity={name:sha(code_root/name) for name in (
        "train_qwen_retention_anchored.py","qwen_retention_anchor.py","qwen_calibration_loss.py",
        "qwen_contrastive_curriculum.py","qwen_transfer_choices.py",
        "evaluate_qwen_transfer_logits.py","qwen_neural_resume.py")}
    def config():
        return LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,lora_dropout=0.0,bias="none",
            target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers")

    model=get_peft_model(base,config(),adapter_name="ordinary")
    model.add_adapter("anchored",config())
    initial={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="ordinary").items()}
    set_peft_model_state_dict(model,initial,adapter_name="anchored")
    initial_hash=state_digest(initial)
    if state_digest(get_peft_model_state_dict(model,adapter_name="anchored"))!=initial_hash:
        raise RuntimeError("Adapter initializations differ")

    def logits(ids):
        x=torch.tensor([ids],dtype=torch.long)
        # Use one output position; pinned Qwen3 supports logits_to_keep.
        return model(input_ids=x,attention_mask=torch.ones_like(x),
                     use_cache=False,logits_to_keep=1).logits[:,-1,:]

    def evaluate():
        model.eval()
        rows=[]
        with torch.inference_mode():
            for case,ids in dev_rows:
                z=logits(ids)[0,label_ids].float().tolist()
                rows.append(dict(ident=case.ident,domain=case.domain,
                    prompt_sha256=hashlib.sha256(json.dumps(ids,separators=(",",":")).encode()).hexdigest(),
                    **score(z,case.correct)))
        return rows

    with model.disable_adapter():
        baseline=evaluate()
        with torch.no_grad():
            teacher_train=[torch.log_softmax(logits(ids).float(),dim=-1).detach().clone() for ids in anchor_train]
            teacher_dev=[torch.log_softmax(logits(ids).float(),dim=-1).detach().clone() for ids in anchor_dev]

    def retention_evaluate():
        model.eval()
        rows=[]
        with torch.inference_mode():
            for i,(ids,teacher) in enumerate(zip(anchor_dev,teacher_dev)):
                z=logits(ids)
                rows.append(dict(ident=i,kl=float(anchor.retention_loss(z,teacher)),
                    same_top_token=int(z.argmax(dim=-1).item()==teacher.argmax(dim=-1).item())))
        return rows

    with model.disable_adapter():
        base_retention=retention_evaluate()
    outcomes={}
    for name,weight in OBJECTIVES.items():
        model.set_adapter(name)
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=initial_hash:
            raise RuntimeError("Adapter changed before training")
        params=[(n,p) for n,p in model.named_parameters() if p.requires_grad]
        if not params or any(f".{name}." not in n or "lora_" not in n or
                             not any(f".layers.{i}." in n for i in LAYERS) for n,p in params):
            raise RuntimeError("Unexpected trainable parameters")
        count=sum(p.numel() for _,p in params)
        if count>1_000_000:
            raise RuntimeError("Unexpected adapter size")
        optimizer=torch.optim.AdamW([p for _,p in params],lr=2e-4,weight_decay=0.0,foreach=False)
        torch.manual_seed(SEED)
        model.train()
        identity=dict(model=MODEL,revision=REVISION,base_weight_sha256=hashes,
            dtype="float32",arm=name,retention_weight=weight,initial_adapter_sha256=initial_hash,
            anchor_train_sha256=anchor.digest(anchor_train),anchor_dev_sha256=anchor.digest(anchor_dev),
            train_sha256=suite_digest(train),ordered_tokens_sha256=token_identity,
            development_sha256=suite_digest(dev),source_sha256=source_identity,
            seed=SEED,rank=8,target_layers=LAYERS,
            versions=dict(transformers=transformers.__version__,peft=peft.__version__))
        losses=[]
        cursor=0
        if recovery[name] is not None:
            cursor,losses=resume.restore(recovery[name],params,optimizer,identity)
            if cursor>len(train_rows):
                raise RuntimeError("Checkpoint cursor exceeds fixed curriculum")
        recovered_steps=cursor
        start=time.monotonic()
        for step,(case,ids) in enumerate(train_rows[cursor:],start=cursor):
            optimizer.zero_grad(set_to_none=True)
            loss=choice_loss(logits(ids),label_ids,torch.tensor([case.correct],dtype=torch.long),0.0)
            # Separate backward calls reduce activation memory. Clip once after summing gradients.
            if not torch.isfinite(loss).item():
                raise RuntimeError("Nonfinite reasoning loss")
            loss.backward()
            total=float(loss.detach())
            del loss
            if weight:
                penalty=weight*anchor.retention_loss(logits(anchor_train[step]),teacher_train[step])
                if not torch.isfinite(penalty).item():
                    raise RuntimeError("Nonfinite retention loss")
                penalty.backward()
                total+=float(penalty.detach())
                del penalty
            norm=torch.nn.utils.clip_grad_norm_([p for _,p in params],1.0)
            if not torch.isfinite(norm).item():
                raise RuntimeError("Nonfinite gradient")
            optimizer.step()
            if any(not torch.isfinite(p).all().item() for _,p in params):
                raise RuntimeError("Nonfinite trainable weights")
            losses.append(total)
            if (step+1)%8==0 or step+1==len(train_rows):
                resume.save(out/"resume"/name/f"step-{step+1:03d}",params,optimizer,
                            identity,step+1,losses)
            print(f"{name}: step {step+1}/{len(train_rows)} completed",flush=True)
        elapsed=round((time.monotonic()-start)*1000)
        final_hash=state_digest(get_peft_model_state_dict(model,adapter_name=name))
        if initial_hash==final_hash:
            raise RuntimeError("No neural parameter update")
        rows=evaluate()
        model.save_pretrained(out/name,selected_adapters=[name],safe_serialization=True)
        retention_rows=retention_evaluate()
        outcomes[name]=dict(retention_weight=weight,retention_rows=retention_rows,
            retention_summary=anchor.summarize(retention_rows),train_steps=len(losses),losses=losses,
            training_ms_this_session=elapsed,resumed_steps=recovered_steps,trainable_parameters=count,initial_adapter_sha256=initial_hash,
            final_adapter_sha256=final_hash,rows=rows,summary=summarize(rows),
            paired_vs_base=paired(baseline,rows))
        del optimizer
    # Verify both trained adapters stayed separate and the base is frozen.
    for name,record in outcomes.items():
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=record["final_adapter_sha256"]:
            raise RuntimeError("Training changed another adapter")
    with model.disable_adapter():
        repeated=evaluate()
    if baseline!=repeated:
        raise RuntimeError("Disabled-adapter baseline changed")
    report=dict(status="COMPLETE_RETENTION_ANCHORED_REASONING",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,training_dtype="float32",seed=SEED,train_sha256=suite_digest(train),train_cases=len(train),
        development_sha256=suite_digest(dev),label_token_ids=label_ids,target_layers=LAYERS,
        baseline=baseline,baseline_summary=summarize(baseline),adapters=outcomes,
        anchor_train_sha256=anchor.digest(anchor_train),anchor_dev_sha256=anchor.digest(anchor_dev),
        baseline_retention=base_retention,baseline_retention_summary=anchor.summarize(base_retention),
        paired_anchored_vs_ordinary=paired(outcomes["ordinary"]["rows"],outcomes["anchored"]["rows"]),
        frozen_baseline_reproduced=True,
        versions=dict(torch=torch.__version__,transformers=transformers.__version__,peft=peft.__version__),
        official_ai_rating=None,
        scope="Public matched reasoning adaptation with frozen-base next-token retention regularization. "
        "Teacher KL/top-token agreement measure distribution preservation, not factual accuracy. "
        "Anchored arm requires additional forward/backward computation. No independent score or deployment.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    return report

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("--resume-ordinary",type=Path)
    p.add_argument("--resume-anchored",type=Path)
    args=p.parse_args()
    run(args.output,args.resume_ordinary,args.resume_anchored)
