"""Two matched supervised objectives: full-token CE vs choice-only label smoothing."""

def choice_loss(logits, label_ids, correct, smoothing=0.0):
    import torch
    import torch.nn.functional as F
    if logits.ndim!=2 or len(label_ids)!=4 or len(set(label_ids))!=4:
        raise ValueError("Expected batch of full-vocabulary logits and four distinct labels")
    if any(type(i) is not int or not 0<=i<logits.shape[1] for i in label_ids):
        raise ValueError("Invalid label token IDs")
    if correct.ndim!=1 or correct.shape[0]!=logits.shape[0] or correct.dtype!=torch.long:
        raise ValueError("Invalid target shape or dtype")
    if not torch.all((correct>=0)&(correct<4)).item():
        raise ValueError("Invalid target label")
    if type(smoothing) not in (int,float) or not 0<=smoothing<0.75:
        raise ValueError("Smoothing must be in [0, 0.75)")
    z=logits.float()
    if not torch.isfinite(z).all().item():
        raise ValueError("Nonfinite logits")
    chosen=z[:,label_ids]
    if smoothing==0:
        # Exact ordinary full-vocabulary answer-token CE.
        targets=torch.tensor(label_ids,device=z.device)[correct]
        return F.cross_entropy(z,targets)
    # The mass of the four allowed tokens remains rewarded. Only their
    # conditional distribution is smoothed; non-answer tokens get no target.
    mass_penalty=torch.logsumexp(z,dim=-1)-torch.logsumexp(chosen,dim=-1)
    log_probs=F.log_softmax(chosen,dim=-1)
    target=torch.full_like(log_probs,smoothing/3)
    target.scatter_(1,correct[:,None],1-smoothing)
    return (mass_penalty-(target*log_probs).sum(dim=-1)).mean()
