"""Cross-domain transfer of the exact #541 recovered neural weights.

Reuse the pinned checkpoint identity checks. No retraining, answer tools,
or claim of independent assessment. Public paired paraphrases are dependent.
"""
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
import qwen_cross_domain_transfer as probe

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
    probes=probe.suite()
    training_questions={e.question for e in train}
    if any(turn[1] in training_questions for p in probes for turn in p.turns):
        raise RuntimeError("Training prompt overlap")
    development=[probe.prompt(tok,p) for p in probes]
    if len({tuple(p) for p in development})!=len(probes):
        raise RuntimeError("Duplicate encoded prompt")

    def evaluate():
        model.eval()
        rows=[]
        for start in range(0,len(probes),BATCH):
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
                tokens,done=completion_tokens(output[j,x.shape[1]:].tolist(),tok.eos_token_id,CAP)
                # Keep generated special markers visible to reject malformed turns.
                raw=tok.decode(tokens[:-1] if done else tokens,skip_special_tokens=False)
                content=visible(raw)
                if "<|im_start|>" in content or "<|im_end|>" in content:
                    content=""
                p=probes[start+j]
                correct,terminal=grade(content,p.answer)
                rows.append(dict(ident=p.ident,form=p.form,domain=p.domain,expected=p.answer,
                    complete=done,correct=done and correct,terminal_format=done and terminal,
                    answer_candidate_correct=correct,tokens=len(tokens),batch_elapsed_ms=elapsed,
                    visible_output=content[:3000],output_sha256=hashlib.sha256(content.encode()).hexdigest(),
                    prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest()))
            print(f"evaluated {len(rows)}/{len(probes)}",flush=True)
        return rows

    with model.disable_adapter():
        baseline=evaluate()
    arms={}
    for name in ARMS:
        model.set_adapter(name)
        rows=evaluate()
        arms[name]=dict(rows=rows,summary=probe.summarize(rows,probes),
            paired_vs_base=probe.paired(baseline,rows),parameter_sha256=PINS[name][1],
            checkpoint_sha256=manifests[name]["state_sha256"])
    with model.disable_adapter():
        repeat=evaluate()
    def stable(rows):
        return [{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(baseline)!=stable(repeat):
        raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_CROSS_DOMAIN_TRANSFER",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,source_run=46049366258,suite_sha256=probe.digest(probes),
        generation_cap=CAP,batch_size=BATCH,baseline=baseline,baseline_summary=probe.summarize(baseline,probes),
        adapters=arms,paired_rationale_vs_answer_only=probe.paired(arms["answer_only"]["rows"],arms["rationale"]["rows"]),
        frozen_baseline_reproduced=True,official_ai_rating=None,
        scope="Public 16-question diagnostic, 32 dependent prompts. Multiturn history includes scripted prior acknowledgments, not interactive memory validation. Strict terminal grading; candidate correctness without EOS is diagnostic only. No external tools or deployment approval.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n",encoding="utf-8")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("checkpoints",type=Path)
    a=p.parse_args()
    run(a.output,a.checkpoints)
