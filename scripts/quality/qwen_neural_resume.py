"""Exact CPU AdamW resume for frozen-base LoRA training.

No pickle, no base weights, no automatic deployment. A caller must supply the
same independently constructed run identity, ordered curriculum and optimizer
configuration. Checkpoints are immutable and only represent completed updates.
"""
import hashlib
import json
import math
import platform
from pathlib import Path

FORMAT = "ASTRA_CPU_ADAMW_RESUME_V1"
MAX_BYTES = 64 * 1024 * 1024
MAX_STEPS = 4096

def canonical(value):
    return json.dumps(value,sort_keys=True,separators=(",",":"),allow_nan=False)

def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda:stream.read(1024*1024),b""):
            h.update(block)
    return h.hexdigest()

def contract(named_parameters, optimizer):
    import torch
    named=list(named_parameters)
    if not named or len(named)>256 or len({name for name,_ in named})!=len(named):
        raise ValueError("Invalid named trainable parameters")
    params=[p for _,p in named]
    if any(not isinstance(n,str) or not n or len(n)>512 for n,_ in named):
        raise ValueError("Invalid parameter name")
    if (len({id(p) for p in params})!=len(params) or
        any(p.device.type!="cpu" or not p.requires_grad or p.dtype!=torch.float32 for p in params)):
        raise ValueError("Resume supports distinct CPU FP32 trainable parameters only")
    if type(optimizer) is not torch.optim.AdamW or len(optimizer.param_groups)!=1:
        raise ValueError("Resume requires one AdamW parameter group")
    group=optimizer.param_groups[0]
    if [id(p) for p in group["params"]]!=[id(p) for p in params]:
        raise ValueError("Parameter order differs from optimizer")
    if any(group.get(k) for k in ("amsgrad","capturable","differentiable","fused","maximize")):
        raise ValueError("Unsupported AdamW configuration")
    if group.get("foreach") is not False:
        raise ValueError("Explicit foreach=False required for reproducibility")
    settings=json.loads(canonical({k:v for k,v in group.items() if k!="params"}))
    return named,settings

def runtime_contract():
    import torch
    return dict(torch_version=str(torch.__version__),machine=platform.machine(),
        cpu_capability=torch.backends.cpu.get_cpu_capability(),
        threads=torch.get_num_threads(),interop_threads=torch.get_num_interop_threads(),
        mkldnn_enabled=torch.backends.mkldnn.enabled,
        deterministic=torch.are_deterministic_algorithms_enabled())


def reject_duplicate_fields(pairs):
    result={}
    for key,value in pairs:
        if key in result:
            raise ValueError("Duplicate checkpoint field")
        result[key]=value
    return result


def cursor_check(cursor, losses):
    if (type(cursor) is not int or not 1<=cursor<=MAX_STEPS or
            not isinstance(losses,list) or len(losses)!=cursor or
            any(type(x) not in (int,float) or not math.isfinite(x) for x in losses)):
        raise ValueError("Invalid completed-step cursor or loss history")

def validate_tensor(tensor, shape, dtype):
    import torch
    if tensor.device.type!="cpu" or tensor.dtype!=dtype or tuple(tensor.shape)!=tuple(shape):
        raise ValueError("Checkpoint tensor shape, dtype or device mismatch")
    if not torch.isfinite(tensor).all().item():
        raise ValueError("Nonfinite checkpoint tensor")

def save(destination, named_parameters, optimizer, identity, cursor, losses):
    import torch
    from safetensors.torch import save_file
    cursor_check(cursor,losses)
    named,settings=contract(named_parameters,optimizer)
    identity=json.loads(canonical(identity))
    if not isinstance(identity,dict) or not identity:
        raise ValueError("Missing run identity")
    dest=Path(destination)
    stage=dest.with_name("."+dest.name+".tmp")
    if dest.exists() or stage.exists():
        raise ValueError("Immutable checkpoint destination already exists")
    tensors={"cpu_rng":torch.get_rng_state().clone()}
    for i,(_,p) in enumerate(named):
        state=optimizer.state.get(p,{})
        if set(state)!={"step","exp_avg","exp_avg_sq"}:
            raise ValueError("Incomplete or unsupported optimizer state")
        for key,value in (("weight",p.detach()),("exp_avg",state["exp_avg"]),("exp_avg_sq",state["exp_avg_sq"])):
            validate_tensor(value,p.shape,p.dtype)
            if key=="exp_avg_sq" and (value<0).any().item():
                raise ValueError("Negative second moment")
            tensors[f"{i}.{key}"]=value.contiguous().clone()
        step=state["step"]
        validate_tensor(step,(),torch.float32)
        if step.item()!=cursor:
            raise ValueError("AdamW step does not match completed cursor")
        tensors[f"{i}.step"]=step.clone()
    if sum(t.numel()*t.element_size() for t in tensors.values())>MAX_BYTES-65536:
        raise ValueError("Checkpoint exceeds bounded size")
    stage.mkdir(parents=True)
    path=stage/"state.safetensors"
    save_file(tensors,str(path))
    manifest=dict(format=FORMAT,status="UNEVALUATED_NEURAL_CHECKPOINT",identity=identity,
        runtime=runtime_contract(),optimizer=settings,parameter_names=[name for name,_ in named],
        completed_steps=cursor,losses=losses,state_sha256=digest(path))
    encoded=canonical(manifest)+"\n"
    if len(encoded.encode("utf-8"))>65536 or path.stat().st_size>MAX_BYTES:
        raise ValueError("Checkpoint exceeds bounded size")
    (stage/"manifest.json").write_text(encoded,encoding="utf-8")
    stage.rename(dest)
    return manifest

def restore(source, named_parameters, optimizer, expected_identity):
    """Validate every field before mutating a fresh optimizer or parameters."""
    import torch
    from safetensors.torch import load_file
    named,settings=contract(named_parameters,optimizer)
    if optimizer.state:
        raise ValueError("Restore requires a fresh optimizer")
    root=Path(source)
    mf=root/"manifest.json"
    sf=root/"state.safetensors"
    if not mf.is_file() or not sf.is_file() or mf.stat().st_size>65536 or not 0<sf.stat().st_size<=MAX_BYTES:
        raise ValueError("Missing or oversized checkpoint")
    m=json.loads(mf.read_text(encoding="utf-8"),object_pairs_hook=reject_duplicate_fields)
    fields={"format","status","identity","runtime","optimizer","parameter_names",
            "completed_steps","losses","state_sha256"}
    if (set(m)!=fields or m["format"]!=FORMAT or m["status"]!="UNEVALUATED_NEURAL_CHECKPOINT" or
        canonical(m["identity"])!=canonical(expected_identity) or
        m["runtime"]!=runtime_contract() or
        canonical(m["optimizer"])!=canonical(settings) or
        m["parameter_names"]!=[name for name,_ in named] or m["state_sha256"]!=digest(sf)):
        raise ValueError("Checkpoint identity, runtime, optimizer or digest mismatch")
    cursor=m["completed_steps"]
    cursor_check(cursor,m["losses"])
    data=load_file(str(sf),device="cpu")
    keys={"cpu_rng"}|{f"{i}.{k}" for i in range(len(named)) for k in ("weight","step","exp_avg","exp_avg_sq")}
    if set(data)!=keys:
        raise ValueError("Unexpected checkpoint tensor set")
    rng=data["cpu_rng"]
    if rng.dtype!=torch.uint8 or rng.ndim!=1 or rng.numel()!=torch.get_rng_state().numel():
        raise ValueError("Invalid CPU RNG state")
    # Validate RNG without changing the global generator.
    torch.Generator(device="cpu").set_state(rng)
    for i,(_,p) in enumerate(named):
        for k in ("weight","exp_avg","exp_avg_sq"):
            validate_tensor(data[f"{i}.{k}"],p.shape,p.dtype)
        step=data[f"{i}.step"]
        validate_tensor(step,(),torch.float32)
        if step.item()!=cursor or (data[f"{i}.exp_avg_sq"]<0).any().item():
            raise ValueError("Invalid AdamW moments or cursor")
    with torch.no_grad():
        for i,(_,p) in enumerate(named):
            p.copy_(data[f"{i}.weight"])
            optimizer.state[p]={k:data[f"{i}.{k}"].clone() for k in ("step","exp_avg","exp_avg_sq")}
    torch.set_rng_state(rng)
    return cursor,list(m["losses"])
