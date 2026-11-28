"""Fixed half-strength LoRA delta intervention; no model-based routing.

Scale B only: (alpha/r) (B/2) A = 0.5 (alpha/r) B A.
Original tensors are never modified. Restrict to finite FP32 rank-8 factors.
"""
def attenuate(state):
    import torch
    if not state:
        raise ValueError("Empty state")
    a_keys={k for k in state if k.endswith(".lora_A.weight")}
    b_keys={k for k in state if k.endswith(".lora_B.weight")}
    if not a_keys or a_keys|b_keys!=set(state) or len(a_keys)!=len(b_keys):
        raise ValueError("Malformed adapter factors")
    result={}
    for ak in sorted(a_keys):
        bk=ak.removesuffix(".lora_A.weight")+".lora_B.weight"
        if bk not in b_keys:
            raise ValueError("Missing matching factor")
        a,b=state[ak],state[bk]
        if any(t.ndim!=2 or t.dtype!=torch.float32 or not torch.isfinite(t).all().item() for t in (a,b)):
            raise ValueError("Invalid factor")
        if a.shape[0]!=8 or b.shape[1]!=8 or not a.shape[1] or not b.shape[0]:
            raise ValueError("Unexpected rank/dimensions")
        result[ak]=a.detach().clone()
        result[bk]=b.detach().mul(.5)
    return result

def verify(original,candidate,chunk=128):
    import torch
    if not isinstance(chunk,int) or not 1<=chunk<=256:
        raise ValueError("Invalid chunk")
    expected=attenuate(original)
    if expected.keys()!=candidate.keys() or any(
        not torch.equal(expected[k],candidate[k]) for k in expected):
        raise ValueError("Incorrect scaled factors")
    peak=0.
    nonzero=0
    with torch.no_grad():
        for ak in expected:
            if not ak.endswith(".lora_A.weight"):
                continue
            bk=ak.removesuffix(".lora_A.weight")+".lora_B.weight"
            a=original[ak].double()
            b=original[bk].double()
            for i in range(0,b.shape[0],chunk):
                orig=b[i:i+chunk]@a
                got=candidate[bk][i:i+chunk].double()@candidate[ak].double()
                if not torch.isfinite(orig).all().item() or not torch.isfinite(got).all().item():
                    raise ValueError("Nonfinite effective delta")
                peak=max(peak,float((got-orig*.5).abs().max()))
                nonzero+=int(torch.count_nonzero(orig))
                if not torch.allclose(got,orig*.5,rtol=1e-10,atol=1e-12):
                    raise ValueError("Effective delta is not half strength")
    if nonzero==0:
        raise ValueError("All source deltas are zero; no intervention")
    return dict(max_dense_delta_error=peak,nonzero_source_delta_elements=nonzero)
