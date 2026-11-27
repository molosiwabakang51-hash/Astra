"""CPU BF16 neural recognition diagnostic; no deterministic assistance or live routing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from qwen_transfer_choices import cases, prompt, score, summarize, suite_digest, LABELS

MODEL="Qwen/Qwen3-1.7B"
REVISION="8d4744f9e13072f4920c326350fa81eedb74eae9"

def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""):
            h.update(b)
    return h.hexdigest()

def paired(before, after):
    a={r["ident"]:r for r in before}
    b={r["ident"]:r for r in after}
    if len(a)!=len(before) or len(b)!=len(after) or not a or a.keys()!=b.keys():
        raise ValueError("Unmatched cases")
    if any(a[k]["domain"]!=b[k]["domain"] or a[k]["prompt_sha256"]!=b[k]["prompt_sha256"] for k in a):
        raise ValueError("Case or prompt changed")
    return dict(improved=sum(not a[k]["correct"] and b[k]["correct"] for k in a),
                regressed=sum(a[k]["correct"] and not b[k]["correct"] for k in a),
                both_correct=sum(a[k]["correct"] and b[k]["correct"] for k in a),
                neither=sum(not a[k]["correct"] and not b[k]["correct"] for k in a))

def adapter_manifest(root, weights):
    """Require exact pilot identity and immutable base checkpoint hashes."""
    report=json.loads((root/"report.json").read_text())
    config=json.loads((root/"adapter"/"adapter_config.json").read_text())
    adapter=root/"adapter"/"adapter_model.safetensors"
    if (report.get("status")!="COMPLETE_NEURAL_ADAPTATION_PILOT" or
        report.get("model")!=MODEL or report.get("revision")!=REVISION or
        report.get("base_weight_sha256")!=weights or
        report.get("frozen_baseline_reproduced") is not True or
        not report.get("initial_adapter_sha256") or
        report.get("initial_adapter_sha256")==report.get("final_adapter_sha256") or
        config.get("peft_type")!="LORA" or config.get("task_type")!="CAUSAL_LM" or
        config.get("r")!=8 or sorted(config.get("target_modules",[]))!=["q_proj","v_proj"] or
        config.get("layers_to_transform")!=[24,25,26,27] or
        not adapter.is_file() or not 0<adapter.stat().st_size<=16*1024*1024):
        raise ValueError("Unverified or incompatible adapter pilot")
    return sha(adapter)

def verify_saved_parameters(pilot):
    """Recompute the pilot's actual final parameter digest from exported tensors."""
    import torch
    from safetensors.torch import load_file
    report=json.loads((pilot/"report.json").read_text())
    data=load_file(str(pilot/"adapter"/"adapter_model.safetensors"),device="cpu")
    expected={}
    for layer in (24,25,26,27):
        for projection,width in (("q_proj",2048),("v_proj",1024)):
            stem=f"base_model.model.model.layers.{layer}.self_attn.{projection}"
            expected[stem+".lora_A.weight"]=(8,2048)
            expected[stem+".lora_B.weight"]=(width,8)
    if set(data)!=set(expected):
        raise ValueError("Unexpected saved adapter tensors")
    h=hashlib.sha256()
    for name,tensor in sorted(data.items()):
        if tuple(tensor.shape)!=expected[name] or tensor.dtype!=torch.float32 or not torch.isfinite(tensor).all().item():
            raise ValueError("Unexpected adapter tensor shape, dtype or value")
        # Pilot digests the live PEFT default-adapter parameter names.
        h.update(name.removesuffix(".weight").encode()+b".default.weight")
        h.update(tensor.contiguous().numpy().tobytes())
    digest=h.hexdigest()
    if digest!=report.get("final_adapter_sha256"):
        raise ValueError("Saved adapter parameters do not match pilot report")
    return digest


def run(out, pilot=None, dtype="bfloat16"):
    import torch
    if dtype not in ("float32","bfloat16"):
        raise ValueError("Unsupported precision")
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoModelForCausalLM
    torch.manual_seed(91502047)
    torch.set_num_threads(min(4,os.cpu_count() or 1))
    root=Path(snapshot_download(repo_id=MODEL,revision=REVISION,
        allow_patterns=["*.json","*.safetensors","*.txt","*.model"]))
    if root.name!=REVISION:
        raise RuntimeError("Unexpected model revision")
    shards=sorted(root.glob("*.safetensors"))
    if len(shards)!=2:
        raise RuntimeError("Unexpected weight shard count")
    hashes={p.name:sha(p) for p in shards}
    adapter_hash=adapter_manifest(pilot,hashes) if pilot is not None else None
    parameter_hash=verify_saved_parameters(pilot) if pilot is not None else None
    tokenizer=AutoTokenizer.from_pretrained(root,trust_remote_code=False,local_files_only=True)
    model=AutoModelForCausalLM.from_pretrained(root,torch_dtype=getattr(torch,dtype),
        trust_remote_code=False,local_files_only=True,low_cpu_mem_usage=True,attn_implementation="sdpa")
    if model.config.model_type!="qwen3" or model.config.num_hidden_layers!=28 or model.config.hidden_size!=2048:
        raise RuntimeError("Unexpected model architecture")
    suite=cases()
    labels=[tokenizer.encode(l,add_special_tokens=False) for l in LABELS]
    if any(len(t)!=1 for t in labels) or len({t[0] for t in labels})!=4:
        raise RuntimeError("Choice labels are not distinct single tokens")
    ids=[t[0] for t in labels]
    prepared=[]
    for case in suite:
        messages=[{"role":"system","content":"Answer accurately using the given information. Choose the best option."},
                  {"role":"user","content":prompt(case)}]
        prefix=tokenizer.apply_chat_template(messages,tokenize=True,add_generation_prompt=True,enable_thinking=False)
        if not prefix or len(prefix)>512:
            raise RuntimeError("Prompt exceeds bounded evaluation")
        prepared.append((case,prefix))
    def evaluate(current):
        current.eval()
        rows=[]
        for case,prefix in prepared:
            x=torch.tensor([prefix],dtype=torch.long)
            start=time.monotonic()
            with torch.inference_mode():
                logits=current(input_ids=x,attention_mask=torch.ones_like(x),use_cache=False).logits[0,-1,ids].float().tolist()
            rows.append(dict(ident=case.ident,domain=case.domain,
                prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest(),
                elapsed_ms=round((time.monotonic()-start)*1000),**score(logits,case.correct)))
        return rows
    baseline=evaluate(model)
    report=dict(status="COMPLETE_NEURAL_TRANSFER_LOGITS",model=MODEL,revision=REVISION,
        base_weight_sha256=hashes,inference_dtype=dtype,suite_sha256=suite_digest(suite),label_token_ids=ids,
        baseline=baseline,baseline_summary=summarize(baseline),adapter_sha256=adapter_hash,adapter_parameter_sha256=parameter_hash,
        adapted=None,adapted_summary=None,paired=None,official_ai_rating=None,
        scope="Public 24-case recognition-only diagnostic. Four-option conditional probabilities, not free-generation accuracy or calibrated real-world confidence. CPU, no tools, not independent or production GGUF assessment.")
    if pilot is not None:
        from peft import PeftModel
        adapted=PeftModel.from_pretrained(model,pilot/"adapter",is_trainable=False)
        rows=evaluate(adapted)
        with adapted.disable_adapter():
            repeated=evaluate(adapted)
        for a,b in zip(baseline,repeated):
            if {k:v for k,v in a.items() if k!="elapsed_ms"}!={k:v for k,v in b.items() if k!="elapsed_ms"}:
                raise RuntimeError("Disabled-adapter baseline changed")
        report.update(adapted=rows,adapted_summary=summarize(rows),paired=paired(baseline,rows),
                      disabled_adapter_baseline_reproduced=True)
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")
    return report

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("--pilot",type=Path)
    p.add_argument("--dtype",choices=("float32","bfloat16"),default="bfloat16")
    args=p.parse_args()
    run(args.output,args.pilot,args.dtype)
