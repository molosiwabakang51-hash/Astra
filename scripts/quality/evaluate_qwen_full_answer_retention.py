"""Compare frozen Qwen3 and the two completed #544 adapters on new public prompts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time

import qwen_full_answer_retention as suite
from train_qwen_retention_anchored import MODEL, REVISION, EXPECTED_BASE, LAYERS, state_digest
from evaluate_qwen_transfer_logits import sha

ARMS=("ordinary","anchored")
PINS={
 "ordinary":"9825f030cc8a41d50b21f6d4c7f24e49e9824aa4f0143bb27fccdefddcec855e",
 "anchored":"5e883cdf295018717b809269f74f00dc0a72f7a8660bc58e29c277e33b77f636",
}
CAP=24
BATCH=4

def padded(prefixes,pad):
    if not prefixes or any(not p for p in prefixes):
        raise ValueError("Empty prompt")
    n=max(map(len,prefixes))
    return ([[pad]*(n-len(p))+p for p in prefixes],
            [[0]*(n-len(p))+[1]*len(p) for p in prefixes])

def completion(tokens,eos):
    if not tokens or len(tokens)>CAP:
        raise ValueError("Invalid generated length")
    if eos not in tokens:
        return tokens,False
    end=tokens.index(eos)+1
    if any(t!=eos for t in tokens[end:]):
        raise ValueError("Nonpadding token after EOS")
    return tokens[:end],True

def reference_nll(model,prefix,suffix):
    import torch
    import torch.nn.functional as F
    if not prefix or not suffix:
        raise ValueError("Empty prefix or reference")
    ids=torch.tensor([prefix+suffix],dtype=torch.long)
    logits=model(input_ids=ids,attention_mask=torch.ones_like(ids),use_cache=False,
        logits_to_keep=len(suffix)+1).logits
    if logits.shape[:2]!=(1,len(suffix)+1):
        raise RuntimeError("Unexpected reference projection")
    loss=F.cross_entropy(logits[0,:-1].float(),torch.tensor(suffix,dtype=torch.long))
    if not torch.isfinite(loss).item():
        raise ValueError("Nonfinite reference loss")
    return float(loss)

def validate_adapter(root,arm):
    import torch
    from safetensors.torch import load_file
    cfg=json.loads((root/"adapter_config.json").read_text())
    if (cfg["peft_type"]!="LORA" or cfg["task_type"]!="CAUSAL_LM" or
        cfg["r"]!=8 or cfg["lora_alpha"]!=16 or cfg["layers_to_transform"]!=LAYERS or
        sorted(cfg["target_modules"])!=["q_proj","v_proj"] or cfg["bias"]!="none"):
        raise ValueError("Unexpected adapter configuration")
    path=root/"adapter_model.safetensors"
    if not 0<path.stat().st_size<=16*1024*1024:
        raise ValueError("Oversized adapter")
    state=load_file(str(path),device="cpu")
    if (len(state)!=16 or sum(t.numel() for t in state.values())!=229376 or
        any(t.dtype!=torch.float32 or not torch.isfinite(t).all().item() for t in state.values())
        or state_digest(state)!=PINS[arm]):
        raise ValueError("Unpinned adapter tensors")

def run(out,artifact):
    import torch
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import PeftModel,get_peft_model_state_dict
    torch.manual_seed(734009)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    original=json.loads((artifact/"report.json").read_text())
    if (original["status"]!="COMPLETE_RETENTION_ANCHORED_REASONING" or
        original["model"]!=MODEL or original["revision"]!=REVISION or
        original["base_weight_sha256"]!=EXPECTED_BASE or not original["frozen_baseline_reproduced"] or
        any(original["adapters"][a]["final_adapter_sha256"]!=PINS[a] for a in ARMS)):
        raise ValueError("Wrong source report")
    for arm in ARMS:
        validate_adapter(artifact/arm/arm,arm)
    root=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    hashes={p.name:sha(p) for p in sorted(root.glob("*.safetensors"))}
    if root.name!=REVISION or hashes!=EXPECTED_BASE:
        raise RuntimeError("Unexpected base weights")
    tok=AutoTokenizer.from_pretrained(root,trust_remote_code=False,local_files_only=True)
    base=AutoModelForCausalLM.from_pretrained(root,torch_dtype=torch.float32,
        trust_remote_code=False,local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if base.config.model_type!="qwen3" or base.config.num_hidden_layers!=28 or base.config.hidden_size!=2048 or tok.eos_token_id!=151645:
        raise RuntimeError("Unexpected architecture")
    model=PeftModel.from_pretrained(base,artifact/"ordinary"/"ordinary",adapter_name="ordinary",is_trainable=False)
    model.load_adapter(artifact/"anchored"/"anchored",adapter_name="anchored",is_trainable=False)
    for arm in ARMS:
        if state_digest(get_peft_model_state_dict(model,adapter_name=arm))!=PINS[arm]:
            raise RuntimeError("Loaded adapter differs from pin")
    cases=suite.cases()
    prefixes=[suite.prefix(tok,c) for c in cases]
    references=[tok.encode(c.aliases[0],add_special_tokens=False)+[tok.eos_token_id] for c in cases]
    if any(len(s)<2 or len(s)>32 for s in references):
        raise RuntimeError("Invalid reference length")
    def evaluate():
        model.eval()
        rows=[]
        with torch.inference_mode():
            for start in range(0,len(cases),BATCH):
                p=prefixes[start:start+BATCH]
                ids,mask=padded(p,tok.eos_token_id)
                x=torch.tensor(ids,dtype=torch.long)
                t=time.monotonic()
                result=model.generate(input_ids=x,attention_mask=torch.tensor(mask,dtype=torch.long),
                    do_sample=False,temperature=None,top_p=None,top_k=None,max_new_tokens=CAP,
                    pad_token_id=tok.eos_token_id,eos_token_id=tok.eos_token_id,use_cache=True)
                elapsed=round((time.monotonic()-t)*1000)
                for j,prefix in enumerate(p):
                    i=start+j
                    c=cases[i]
                    tokens,done=completion(result[j,x.shape[1]:].tolist(),tok.eos_token_id)
                    content=tok.decode(tokens,skip_special_tokens=True).strip()
                    rows.append(dict(ident=c.ident,domain=c.domain,complete=done,
                        correct=done and suite.grade(content,c),visible_output=content[:1000],
                        expected=list(c.aliases),tokens=len(tokens),batch_elapsed_ms=elapsed,
                        reference_nll=reference_nll(model,prefix,references[i]),
                        prompt_sha256=suite.suite_digest((c,))))
                print(f"evaluated {len(rows)}/{len(cases)}",flush=True)
        return rows
    with model.disable_adapter():
        baseline=evaluate()
    results={}
    for arm in ARMS:
        model.set_adapter(arm)
        rows=evaluate()
        results[arm]=dict(rows=rows,summary=suite.summarize(rows),paired=suite.paired(baseline,rows))
    with model.disable_adapter():
        repeat=evaluate()
    def stable(rows):
        return [{k:v for k,v in r.items() if k!="batch_elapsed_ms"} for r in rows]
    if stable(baseline)!=stable(repeat):
        raise RuntimeError("Frozen baseline changed")
    report=dict(status="COMPLETE_PUBLIC_FULL_ANSWER_RETENTION",source_run=46052741186,
        base_weight_sha256=hashes,suite_sha256=suite.suite_digest(cases),adapter_sha256=PINS,
        baseline=baseline,baseline_summary=suite.summarize(baseline),adapters=results,
        anchored_vs_ordinary=suite.paired(results["ordinary"]["rows"],results["anchored"]["rows"]),
        frozen_baseline_reproduced=True,generation_cap=CAP,official_ai_rating=None,
        scope="32 public short-answer questions, conservative exact aliases; teacher-forced canonical reference likelihood includes EOS. Not comprehensive retention, independent evaluation or deployment approval.")
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,indent=2,ensure_ascii=False,allow_nan=False)+"\n",encoding="utf-8")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("artifact",type=Path)
    a=p.parse_args()
    run(a.output,a.artifact)
