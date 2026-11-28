"""Export exact recovered reasoning LoRA and evaluate it on Astra's pinned Q4 GGUF.
Host CPU diagnostic; neither Android integration nor independent rating.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request
from train_qwen_rationale_transfer import MODEL,REVISION,BASE_HASHES,LAYERS,SEED,state_digest,visible,summarize,paired
from train_qwen_neural_pilot import file_digest
from qwen_rationale_curriculum import curriculum,controls,digest,encode,grade
from evaluate_qwen_rationale_checkpoints import PINS,recover
import run_qwen_profile_comparison as native

CAP=256
SCALE=1.0

def shapes():
    result={}
    for layer in LAYERS:
        for proj,dim in (("q_proj",2048),("v_proj",1024)):
            p=f"base_model.model.model.layers.{layer}.self_attn.{proj}"
            result[p+".lora_A.weight"]=(8,2048)
            result[p+".lora_B.weight"]=(dim,8)
    return result

def check_adapters(rows,path,scale):
    if not isinstance(rows,list) or len(rows)!=1 or not isinstance(rows[0],dict):
        raise ValueError("Expected precisely one loaded adapter")
    row=rows[0]
    if type(row.get("id")) is not int or row["id"]!=0 or not isinstance(row.get("path"),str) or Path(row["path"]).resolve()!=path.resolve() or type(row.get("scale")) not in (int,float) or row["scale"]!=scale:
        raise ValueError("Native adapter identity or scale mismatch")

def score_reply(reply,expected):
    if not isinstance(reply,dict) or not isinstance(reply.get("content"),str):
        raise ValueError("Invalid completion")
    settings=reply.get("generation_settings",{})
    for key,value in (("temperature",0.),("top_k",1),("top_p",1.),("min_p",0.),("n_predict",CAP)):
        if type(settings.get(key)) not in (int,float) or not math.isclose(settings[key],value,rel_tol=1e-6,abs_tol=1e-8):
            raise ValueError("Unverified native sampling")
    stop,truncated,count=reply.get("stop_type"),reply.get("truncated"),reply.get("tokens_predicted")
    if stop not in ("eos","word","limit") or type(truncated) is not bool or type(count) is not int or not 0<=count<=CAP+8:
        raise ValueError("Unverified completion boundary")
    done=stop=="eos" and not truncated
    text=visible(reply["content"])
    correct,terminal=grade(text,expected)
    return dict(complete=done,correct=done and correct,terminal_format=done and terminal,
        tokens=count,stop_type=stop,truncated=truncated,visible_output=text[:4000],
        output_sha256=hashlib.sha256(text.encode()).hexdigest())

def prepare(out,checkpoints,upstream):
    import torch
    from safetensors.torch import save_file,load_file
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer,AutoConfig
    root=checkpoints/"rationale"/"step-096"
    manifest=root/"manifest.json"
    if not 0<manifest.stat().st_size<=65536 or file_digest(manifest)!=PINS["rationale"][0]:
        raise RuntimeError("Unpinned checkpoint")
    identity=json.loads(manifest.read_text())["identity"]
    train=curriculum(True,16); dev=curriculum(False,8)+controls()
    if identity["model"]!=MODEL or identity["revision"]!=REVISION or identity["base_weight_sha256"]!=BASE_HASHES or identity["train_sha256"]!=digest(train) or identity["development_sha256"]!=digest(dev) or identity["arm"]!="rationale":
        raise RuntimeError("Wrong training identity")
    if any(file_digest(Path(__file__).resolve().parent/n)!=h for n,h in identity["source_sha256"].items()):
        raise RuntimeError("Training source drift")
    local=Path(snapshot_download(repo_id=MODEL,revision=REVISION,allow_patterns=["*.json","*.txt","*.model"]))
    if local.name!=REVISION: raise RuntimeError("Wrong base config revision")
    cfg=AutoConfig.from_pretrained(local,trust_remote_code=False,local_files_only=True)
    if (cfg.model_type,cfg.hidden_size,cfg.num_hidden_layers,cfg.num_attention_heads,cfg.num_key_value_heads,cfg.head_dim)!=("qwen3",2048,28,16,8,128):
        raise RuntimeError("Unexpected base architecture")
    tok=AutoTokenizer.from_pretrained(local,trust_remote_code=False,local_files_only=True)
    ordered=hashlib.sha256(json.dumps([encode(tok,e,True) for e in train],separators=(",",":")).encode()).hexdigest()
    if tok.eos_token_id!=151645 or ordered!=identity["ordered_tokens_sha256"]:
        raise RuntimeError("Tokenizer or ordered training tokens changed")
    initial={n:torch.empty(s,dtype=torch.float32) for n,s in shapes().items()}
    state,m=recover(root,"rationale",identity,initial)
    if sum(t.numel() for t in state.values())!=229376: raise RuntimeError("Wrong tensor size")
    peft=out/"peft";peft.mkdir()
    raw=peft/"adapter_model.safetensors"
    save_file({n:t.detach().contiguous() for n,t in state.items()},str(raw))
    if state_digest(load_file(str(raw)))!=PINS["rationale"][1]: raise RuntimeError("Export changed tensors")
    (peft/"adapter_config.json").write_text(json.dumps(dict(peft_type="LORA",task_type="CAUSAL_LM",
        base_model_name_or_path=MODEL,r=8,lora_alpha=16,lora_dropout=0.,bias="none",
        target_modules=["q_proj","v_proj"],layers_to_transform=LAYERS,layers_pattern="layers",
        fan_in_fan_out=False,use_dora=False,use_rslora=False,rank_pattern={},alpha_pattern={}),indent=2))
    gguf=out/"rationale.f32.gguf"
    subprocess.run([__import__("sys").executable,str(upstream/"convert_lora_to_gguf.py"),
        "--base",str(local),"--outfile",str(gguf),"--outtype","f32",str(peft)],check=True,timeout=300)
    if not 0<gguf.stat().st_size<=8*1024*1024: raise RuntimeError("Invalid GGUF size")
    with gguf.open("rb") as f:
        if f.read(4)!=b"GGUF": raise RuntimeError("Invalid GGUF header")
    evidence=dict(status="EXPORTED_UNEVALUATED_GGUF_ADAPTER",source_manifest_sha256=PINS["rationale"][0],
        source_parameter_sha256=PINS["rationale"][1],source_state_sha256=m["state_sha256"],
        peft_file_sha256=file_digest(raw),gguf_sha256=file_digest(gguf),gguf_bytes=gguf.stat().st_size,
        model=MODEL,revision=REVISION,quantized_base_sha256=native.MODEL_SHA256)
    (out/"export.json").write_text(json.dumps(evidence,indent=2,allow_nan=False)+"\n")
    selected=[(i,e) for i,e in enumerate(dev) if e.domain in ("quantity","abstraction","control")]
    prompts=[(i,e,encode(tok,e,False)[2]) for i,e in selected]
    return gguf,evidence,prompts

def run(out,checkpoints,upstream,binary):
    out.mkdir(parents=True,exist_ok=False)
    if not native.check_model(native.MODEL_PATH): raise RuntimeError("Wrong quantized base")
    gguf,evidence,prompts=prepare(out,checkpoints,upstream)
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(path,payload=None,timeout=180):
        if path not in ("/health","/lora-adapters","/completion"): raise ValueError("Unsupported endpoint")
        body=None if payload is None else json.dumps(payload).encode()
        req=urllib.request.Request(f"http://127.0.0.1:{native.PORT}"+path,data=body,
            headers={"Content-Type":"application/json"})
        with opener.open(req,timeout=timeout) as res: raw=res.read(1_000_001)
        if len(raw)>1_000_000: raise RuntimeError("Oversized response")
        return json.loads(raw)
    command=[str(binary.resolve()),"-m",str(native.MODEL_PATH.resolve()),"--host","127.0.0.1",
        "--port",str(native.PORT),"--ctx-size","4096","--parallel","1","--threads","4",
        "--batch-size","256","--ubatch-size","256","--n-gpu-layers","0","--no-context-shift",
        "--lora",str(gguf.resolve()),"--lora-init-without-apply"]
    with (out/"native.log").open("w+") as log:
        process=subprocess.Popen(command,stdout=log,stderr=log)
        try:
            deadline=time.monotonic()+180
            while time.monotonic()<deadline:
                if process.poll() is not None: raise RuntimeError("Native server exited")
                try:
                    if request("/health",timeout=3).get("status")=="ok": break
                except (OSError,ValueError): pass
                time.sleep(1)
            else: raise RuntimeError("Native startup timeout")
            check_adapters(request("/lora-adapters"),gguf,0.)
            def evaluate(scale):
                request("/lora-adapters",[{"id":0,"scale":scale}])
                check_adapters(request("/lora-adapters"),gguf,scale)
                rows=[]
                for i,e,prefix in prompts:
                    payload=dict(prompt=prefix,n_predict=CAP,temperature=0.,top_k=1,top_p=1.,min_p=0.,
                        repeat_penalty=1.,presence_penalty=0.,frequency_penalty=0.,seed=SEED+i,
                        cache_prompt=False,stream=False,ignore_eos=False)
                    start=time.monotonic()
                    reply=request("/completion",payload)
                    rows.append(dict(ident=str(i),domain=e.domain,expected=e.answer,
                        prompt_sha256=hashlib.sha256(json.dumps(prefix,separators=(",",":")).encode()).hexdigest(),
                        elapsed_ms=round((time.monotonic()-start)*1000),**score_reply(reply,e.answer)))
                    print(f"scale={scale}: {len(rows)}/{len(prompts)}",flush=True)
                check_adapters(request("/lora-adapters"),gguf,scale)
                return rows
            baseline=evaluate(0.)
            candidate=evaluate(SCALE)
            repeat=evaluate(0.)
            stable=lambda rr:[{k:v for k,v in x.items() if k!="elapsed_ms"} for x in rr]
            if stable(baseline)!=stable(repeat): raise RuntimeError("Frozen native baseline changed")
        finally:
            if process.poll() is None:
                process.terminate()
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: process.kill();process.wait()
    if not native.check_model(native.MODEL_PATH) or file_digest(gguf)!=evidence["gguf_sha256"]:
        raise RuntimeError("Model or adapter changed")
    report=dict(status="COMPLETE_NATIVE_GGUF_RATIONALE_TRANSFER",export=evidence,cap=CAP,
        baseline=baseline,candidate=candidate,baseline_summary=summarize(baseline),
        candidate_summary=summarize(candidate),paired=paired(baseline,candidate),
        frozen_native_baseline_reproduced=True,official_ai_rating=None,
        scope="Pinned Q4_K_M base and f32 LoRA, host CPU llama-server; 20 reused public development questions. Native engine/quantization differs from FP32 evaluation; no direct causal quantization-only comparison, handset integration or independent rating.")
    (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    for name in ("output","checkpoints","upstream","binary"):p.add_argument(name,type=Path)
    a=p.parse_args()
    run(a.output,a.checkpoints,a.upstream,a.binary)
