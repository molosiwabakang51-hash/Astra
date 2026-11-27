"""Matched semantic answer-text supervision with optional contrastive ranking.
Both arms see identical questions, correct completions and distractors.
Restricted sequence-likelihood recognition; no independent score or deployment.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time

import qwen_semantic_answer as semantic
import qwen_neural_resume as resume
from qwen_contrastive_curriculum import training_cases
from qwen_transfer_choices import cases, suite_digest
from evaluate_qwen_transfer_logits import MODEL, REVISION, sha

SEED = 41087253
EXPECTED_BASE = {
    "model-00001-of-00002.safetensors": "169ad53ec313c3a34b06c0809216e4fc072cce444a5d4ff2b59690d064130ed5",
    "model-00002-of-00002.safetensors": "912becff8d60672aa8628ef08c05898d9adf17c2ad4ae3caf99b065622fdeff9",
}
LAYERS=[24,25,26,27]
OBJECTIVES={"ordinary":0.0,"contrastive":1.0}
LR=5e-5

def state_digest(state):
    h=hashlib.sha256()
    for name,p in sorted(state.items()):
        h.update(name.encode())
        h.update(p.detach().float().cpu().contiguous().numpy().tobytes())
    return h.hexdigest()

def run(out, resume_ordinary=None, resume_contrastive=None):
    recovery={"ordinary":resume_ordinary,"contrastive":resume_contrastive}
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
    if tok.eos_token_id!=151645:
        raise RuntimeError("Unexpected end token")
    train=training_cases()
    dev=cases()
    if {c.question for c in train}&{c.question for c in dev}:
        raise RuntimeError("Train/evaluation overlap")

    wrong=semantic.distractors(train)
    train_rows=[semantic.prepare(tok,c) for c in train]
    dev_rows=[(case,order,semantic.prepare(tok,case,reverse=order=="reversed"))
              for case in dev for order in ("original","reversed")]
    token_identity=semantic.digest([(c.ident,row,w) for c,row,w in zip(train,train_rows,wrong)])
    code_root=Path(__file__).resolve().parent
    source_identity={name:sha(code_root/name) for name in (
        "train_qwen_semantic_answer.py","qwen_semantic_answer.py",
        "qwen_contrastive_curriculum.py","qwen_transfer_choices.py",
        "evaluate_qwen_transfer_logits.py","qwen_neural_resume.py")}
    def config():
        return LoraConfig(task_type="CAUSAL_LM",r=8,lora_alpha=16,lora_dropout=0.0,bias="none",
            target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers")

    model=get_peft_model(base,config(),adapter_name="ordinary")
    model.add_adapter("contrastive",config())
    initial={k:v.detach().clone() for k,v in get_peft_model_state_dict(model,adapter_name="ordinary").items()}
    set_peft_model_state_dict(model,initial,adapter_name="contrastive")
    initial_hash=state_digest(initial)
    if state_digest(get_peft_model_state_dict(model,adapter_name="contrastive"))!=initial_hash:
        raise RuntimeError("Adapter initializations differ")

    def evaluate():
        model.eval()
        rows=[]
        with torch.inference_mode():
            for case,order,(prefix,suffixes,correct) in dev_rows:
                values=semantic.sequence_scores(model,prefix,suffixes,tok.eos_token_id).tolist()
                winners=[i for i,v in enumerate(values) if v==max(values)]
                chosen=winners[0] if len(winners)==1 else None
                rows.append(dict(ident=case.ident,domain=case.domain,order=order,
                    prompt_sha256=semantic.digest(prefix),scores=values,
                    selected_semantic=(3-chosen if order=="reversed" else chosen) if chosen is not None else None,
                    correct=chosen==correct))
        return rows

    with model.disable_adapter():
        baseline=evaluate()
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
        if count!=229376:
            raise RuntimeError("Unexpected adapter size")
        optimizer=torch.optim.AdamW([p for _,p in params],lr=LR,weight_decay=0.0,foreach=False)
        torch.manual_seed(SEED)
        model.train()
        identity=dict(model=MODEL,revision=REVISION,base_weight_sha256=hashes,
            dtype="float32",arm=name,ranking_weight=weight,learning_rate=LR,initial_adapter_sha256=initial_hash,
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
        for step,(prefix,suffixes,correct) in enumerate(train_rows[cursor:],start=cursor):
            optimizer.zero_grad(set_to_none=True)
            scores=semantic.sequence_scores(model,prefix,[suffixes[correct],suffixes[wrong[step]]],tok.eos_token_id)
            loss=semantic.objective(scores,weight)
            if not torch.isfinite(loss).item():
                raise RuntimeError("Nonfinite training loss")
            loss.backward()
            norm=torch.nn.utils.clip_grad_norm_([p for _,p in params],1.0)
            if not torch.isfinite(norm).item():
                raise RuntimeError("Nonfinite gradient")
            optimizer.step()
            if any(not torch.isfinite(p).all().item() for _,p in params):
                raise RuntimeError("Nonfinite trainable weights")
            losses.append(float(loss.detach()))
            del loss,scores
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
        outcomes[name]=dict(ranking_weight=weight,train_steps=len(losses),losses=losses,
            training_ms_this_session=elapsed,resumed_steps=recovered_steps,trainable_parameters=count,initial_adapter_sha256=initial_hash,
            final_adapter_sha256=final_hash,rows=rows,summary=semantic.summarize(rows),
            paired_vs_base=semantic.paired(semantic.summarize(baseline),semantic.summarize(rows)))
        del optimizer
    # Verify both trained adapters stayed separate and the base is frozen.
    for name,record in outcomes.items():
        if state_digest(get_peft_model_state_dict(model,adapter_name=name))!=record["final_adapter_sha256"]:
            raise RuntimeError("Training changed another adapter")
    with model.disable_adapter():
        repeated=evaluate()
    if baseline!=repeated:
        raise RuntimeError("Disabled-adapter baseline changed")
    report=dict(status="COMPLETE_SEMANTIC_ANSWER_RANKING",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,training_dtype="float32",seed=SEED,train_sha256=suite_digest(train),train_cases=len(train),
        development_sha256=suite_digest(dev),learning_rate=LR,target_layers=LAYERS,
        baseline=baseline,baseline_summary=semantic.summarize(baseline),adapters=outcomes,
        paired_contrastive_vs_ordinary=semantic.paired(outcomes["ordinary"]["summary"],outcomes["contrastive"]["summary"]),
        frozen_baseline_reproduced=True,
        versions=dict(torch=torch.__version__,transformers=transformers.__version__,peft=peft.__version__),
        official_ai_rating=None,
        scope="Public restricted answer-text likelihood recognition, 24 questions in two dependent orders. "
        "Length-normalized completion scores are not calibrated probabilities. No independent or free-generation score, no deployment.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    return report

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("--resume-ordinary",type=Path)
    p.add_argument("--resume-contrastive",type=Path)
    args=p.parse_args()
    run(args.output,args.resume_ordinary,args.resume_contrastive)
