"""Exact additive composition of two rank-r LoRA updates.

Given source scale s, target scale t and coefficient w_i,
A_cat = [A_1; A_2] and B_cat = [w_1*s/t*B_1, w_2*s/t*B_2].
Then t*B_cat@A_cat = sum_i w_i*s*B_i@A_i, without cross terms.
No learned gating or test-driven coefficient selection.
"""
import math

def compose(left,right,weights=(.5,.5),source_scale=2.,target_scale=2.):
    import torch
    if (not isinstance(left,dict) or not isinstance(right,dict) or not left or
        set(left)!=set(right) or len(weights)!=2 or
        any(type(w) not in (float,int) or not math.isfinite(w) or w<0 for w in weights) or
        sum(weights)!=1. or not math.isfinite(source_scale) or source_scale<=0 or
        not math.isfinite(target_scale) or target_scale<=0):
        raise ValueError("Invalid composition contract")
    akeys={k for k in left if k.endswith(".lora_A.weight")}
    bkeys={k.replace(".lora_A.weight",".lora_B.weight") for k in akeys}
    if not akeys or set(left)!=akeys|bkeys:
        raise ValueError("Unpaired LoRA tensor set")
    result={}
    for akey in sorted(akeys):
        bkey=akey.replace(".lora_A.weight",".lora_B.weight")
        a1,b1,a2,b2=left[akey],left[bkey],right[akey],right[bkey]
        if (any(t.ndim!=2 or t.dtype!=torch.float32 or t.device.type!="cpu" or
                not torch.isfinite(t).all().item() or t.numel()==0 for t in (a1,b1,a2,b2)) or
            a1.shape!=a2.shape or b1.shape!=b2.shape or a1.shape[0]!=b1.shape[1]):
            raise ValueError("Incompatible LoRA factors")
        result[akey]=torch.cat((a1,a2),dim=0).contiguous()
        result[bkey]=torch.cat((b1*(weights[0]*source_scale/target_scale),
                               b2*(weights[1]*source_scale/target_scale)),dim=1).contiguous()
    return result

def verify(left,right,combined,weights=(.5,.5),source_scale=2.,target_scale=2.):
    """Verify all actual dense deltas in small row chunks.

    The dense matrices are temporary verification data, not saved or merged
    into the frozen base. Bounds reflect Qwen3 projections.
    """
    import torch
    expected=compose(left,right,weights,source_scale,target_scale)
    if set(combined)!=set(expected) or any(not torch.equal(combined[k],v) for k,v in expected.items()):
        raise ValueError("Loaded factors differ from exact composition")
    checked=0
    maximum=0.
    with torch.inference_mode():
        for akey in sorted(k for k in combined if k.endswith(".lora_A.weight")):
            bkey=akey.replace(".lora_A.weight",".lora_B.weight")
            a,b=combined[akey],combined[bkey]
            if a.shape[1]>4096 or b.shape[0]>4096 or a.shape[0]>64:
                raise ValueError("Composition exceeds verification bounds")
            for i in range(0,b.shape[0],128):
                sl=slice(i,i+128)
                actual=target_scale*(b[sl]@a)
                want=source_scale*(weights[0]*(left[bkey][sl]@left[akey])
                                   +weights[1]*(right[bkey][sl]@right[akey]))
                torch.testing.assert_close(actual,want,rtol=2e-5,atol=2e-7)
                maximum=max(maximum,float((actual-want).abs().max()))
                checked+=actual.numel()
    return dict(elements_checked=checked,max_absolute_delta_error=maximum,
                factors_exact=True)
