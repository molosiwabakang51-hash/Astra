"""First-order retention constraint on actual optimizer displacement.

For retention gradient g and proposed displacement d, remove the component
parallel to g when g·d>0. Unlike gradient surgery before AdamW, this measures
the actual preconditioned displacement. Optimizer moments still track the
unprojected gradient; the constraint is local, not a global retention guarantee.
"""
import math

def project(parameters, before, gradients, apply=True):
    import torch
    if type(apply) is not bool:
        raise ValueError("Invalid projection mode")
    p=list(parameters)
    if not p or len(p)!=len(before) or len(p)!=len(gradients):
        raise ValueError("Invalid projection operands")
    if len({id(x) for x in p})!=len(p):
        raise ValueError("Duplicate parameter")
    with torch.no_grad():
        deltas=[]
        for x,b,g in zip(p,before,gradients):
            if any(t.shape!=x.shape or t.device!=x.device or t.dtype!=torch.float32
                   or not torch.isfinite(t).all().item() for t in (x,b,g)):
                raise ValueError("Invalid projection tensor")
            deltas.append(x.detach().double()-b.double())
        dot=sum(float((d*g.double()).sum()) for d,g in zip(deltas,gradients))
        norm=sum(float(g.double().square().sum()) for g in gradients)
        if not math.isfinite(dot) or not math.isfinite(norm):
            raise ValueError("Invalid projection geometry")
        coefficient=dot/norm if apply and dot>0 and norm>0 else 0.
        candidates=[(b.double()+d-coefficient*g.double()).float()
                    for b,d,g in zip(before,deltas,gradients)]
        if any(not torch.isfinite(t).all().item() for t in candidates):
            raise ValueError("Nonfinite projected parameter")
        # Roundoff can reintroduce positive displacement; revert to exact old
        # weights if residual exceeds a small absolute floating-point tolerance.
        residual=sum(float(((c.double()-b.double())*g.double()).sum())
                     for c,b,g in zip(candidates,before,gradients))
        tolerance=1e-8+1e-5*abs(dot)
        reverted=coefficient>0 and residual>tolerance
        if reverted:
            candidates=[b.detach().clone() for b in before]
            residual=0.
        if coefficient>0:
            for x,c in zip(p,candidates):
                x.copy_(c)
        return dict(proposed_dot=dot,retention_gradient_norm_squared=norm,
                    projected_dot=residual if coefficient>0 else dot,
                    coefficient=coefficient,projected=coefficient>0,reverted=reverted)
