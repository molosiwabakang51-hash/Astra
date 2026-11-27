"""Public next-token retention anchors; no private data or external teacher.

KL measures distribution preservation, not factual correctness. Retention
development prompts are distinct from the training anchors and never used
to compute gradients.
"""
import hashlib
import json

TRAIN=(
    "The largest ocean on Earth is the", "Water freezes at zero degrees",
    "The plural of child is", "A triangle has three", "The opposite of early is",
    "The chemical symbol for sodium is", "The capital of Japan is",
    "The planet closest to the Sun is", "A mammal feeds its young with",
    "The past tense of write is", "In French, the word for book is",
    "The square root of sixteen is", "A week consists of seven",
    "The currency of the United Kingdom is the", "A telescope is used to observe",
    "The primary function of the lungs is",
    "A synonym for rapid is", "The number following ninety-nine is",
    "The first month of the calendar year is", "A kilogram contains one thousand",
    "The chemical formula for water is", "The capital of Italy is",
    "A rectangle has four right", "The opposite of generous is",
    "The comparative form of good is", "The freezing point of water in Fahrenheit is",
    "A bee produces", "The main source of energy for Earth is the",
    "The past tense of swim is", "The instrument used to measure temperature is a",
    "The Roman numeral X means", "In Spanish, the word for red is",
)
DEV=(
    "The largest planet in the Solar System is", "The capital of Canada is",
    "The chemical symbol for iron is", "The opposite of shallow is",
    "The plural of mouse is", "The past tense of teach is",
    "A hexagon has six", "The number of minutes in an hour is",
    "In French, the word for water is", "The instrument used to measure atmospheric pressure is a",
    "The main function of roots in a plant is", "A synonym for cautious is",
    "The square root of eighty-one is", "The month following September is",
    "The currency of Japan is the", "The gas that plants absorb during photosynthesis is",
)
SYSTEM="Complete the following statement accurately. Give the missing word or short phrase."

def validate_splits():
    if len(TRAIN)!=32 or len(DEV)!=16 or len(set(TRAIN+DEV))!=48:
        raise ValueError("Unexpected anchor split")

def encode(tokenizer, prompts):
    result=[]
    for prompt in prompts:
        ids=tokenizer.apply_chat_template(
            [{"role":"system","content":SYSTEM},{"role":"user","content":prompt}],
            tokenize=True,add_generation_prompt=True,enable_thinking=False)
        if not ids or len(ids)>256:
            raise ValueError("Retention prompt exceeds bounded context")
        result.append(ids)
    return result

def digest(encoded):
    return hashlib.sha256(json.dumps(encoded,separators=(",",":")).encode()).hexdigest()

def retention_loss(student, teacher_log_probs):
    """Exact full-vocabulary KL(teacher || student), averaged over rows.

    The detached teacher is normalized once from the disabled-adapter base.
    The teacher must not be attached to an autograd graph.
    """
    import torch
    import torch.nn.functional as F
    if (student.ndim!=2 or student.shape!=teacher_log_probs.shape or
        student.shape[0]==0 or student.shape[1]<2 or
        student.device!=teacher_log_probs.device or teacher_log_probs.requires_grad):
        raise ValueError("Invalid or attached teacher distribution")
    s=student.float()
    t=teacher_log_probs.float()
    if not torch.isfinite(s).all().item() or not torch.isfinite(t).all().item():
        raise ValueError("Nonfinite retention distribution")
    if not torch.allclose(torch.logsumexp(t,dim=-1),torch.zeros(t.shape[0],device=t.device),atol=1e-4,rtol=0):
        raise ValueError("Teacher distribution not normalized")
    return F.kl_div(F.log_softmax(s,dim=-1),t,log_target=True,reduction="batchmean")

def summarize(rows):
    import math
    if not rows or any(not math.isfinite(r["kl"]) or r["kl"] < -1e-5 for r in rows):
        raise ValueError("Invalid retention rows")
    return dict(attempted=len(rows),mean_kl=sum(r["kl"] for r in rows)/len(rows),
                same_top_token=sum(r["same_top_token"] for r in rows))
