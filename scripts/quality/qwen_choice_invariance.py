"""Public rotation diagnostic and matched training schedule.

Rotation is a controlled perturbation of answer order, not a new independent
question. Report semantic consistency per underlying question, not a
sample size inflated by four near-identical variants.
"""
import math
from qwen_transfer_choices import Case, LABELS
from qwen_contrastive_curriculum import training_cases
from qwen_transfer_choices import cases

def rotate(case, offset):
    if type(offset) is not int or not 0<=offset<4:
        raise ValueError("Invalid rotation")
    if len(case.options)!=4 or len(set(case.options))!=4 or not 0<=case.correct<4:
        raise ValueError("Invalid original choices")
    options=case.options[offset:]+case.options[:offset]
    return Case(f"{case.ident}__r{offset}",case.domain,case.question,options,(case.correct-offset)%4)

def training_schedules():
    """Same 32 questions, four passes, same ordering and update counts.

    Fixed repeats the original order. Rotated visits each correct label
    exactly once per question. Balanced positions already exist in the
    fixed arm; rotation changes per-question label association.
    """
    base=training_cases()
    if len(base)!=32 or len({c.ident for c in base})!=32:
        raise ValueError("Unexpected training suite")
    return {name:tuple(rotate(c,offset if name=="rotated" else 0)
                       for offset in range(4) for c in base)
            for name in ("fixed","rotated")}

def development():
    return tuple(rotate(c,offset) for c in cases() for offset in range(4))

def summarize(rows):
    groups={}
    for row in rows:
        ident=row["ident"]
        if "__r" not in ident:
            raise ValueError("Missing rotation identity")
        original,suffix=ident.rsplit("__r",1)
        if suffix not in ("0","1","2","3") or not original:
            raise ValueError("Invalid rotation identity")
        offset=int(suffix)
        if offset in groups.setdefault(original,{}):
            raise ValueError("Duplicate rotation")
        if row["predicted"] not in (*LABELS,None):
            raise ValueError("Invalid predicted label")
        # Convert displayed answer letter back to original semantic option.
        selected=(LABELS.index(row["predicted"])+offset)%4 if row["predicted"] is not None else None
        if type(row["correct"]) is not bool or (selected is None and row["correct"]):
            raise ValueError("Invalid correctness record")
        groups[original][offset]=(row,selected)
    if not groups or any(set(g)!=set(range(4)) for g in groups.values()):
        raise ValueError("Incomplete rotation set")
    by_question=[]
    for ident,g in sorted(groups.items()):
        if len({r["domain"] for r,_ in g.values()})!=1:
            raise ValueError("Domain changed across rotations")
        # A semantic selection cannot be both correct and wrong across rotations.
        for selected in range(4):
            vals={r["correct"] for r,s in g.values() if s==selected}
            if len(vals)>1:
                raise ValueError("Semantic scoring mismatch")
        count=sum(r["correct"] for r,_ in g.values())
        selections=[s for _,s in g.values()]
        by_question.append(dict(ident=ident,domain=g[0][0]["domain"],correct_rotations=count,
            all_correct=count==4,any_correct=count>0,
            consistent=None not in selections and len(set(selections))==1,
            original_correct=g[0][0]["correct"]))
    def aggregate(qs):
        count=len(qs)
        return dict(questions=count,rotations=4*count,
            correct_rotations=sum(q["correct_rotations"] for q in qs),
            all_correct_questions=sum(q["all_correct"] for q in qs),
            any_correct_questions=sum(q["any_correct"] for q in qs),
            consistent_questions=sum(q["consistent"] for q in qs),
            original_correct=sum(q["original_correct"] for q in qs))
    return dict(all=aggregate(by_question),by_domain={d:aggregate([q for q in by_question if q["domain"]==d])
        for d in sorted({q["domain"] for q in by_question})},by_question=by_question)

def paired_questions(before,after):
    a={q["ident"]:q for q in before["by_question"]}
    b={q["ident"]:q for q in after["by_question"]}
    if not a or a.keys()!=b.keys() or any(a[k]["domain"]!=b[k]["domain"] for k in a):
        raise ValueError("Question set changed")
    return dict(more_correct_rotations=sum(b[k]["correct_rotations"]>a[k]["correct_rotations"] for k in a),
                fewer_correct_rotations=sum(b[k]["correct_rotations"]<a[k]["correct_rotations"] for k in a),
                unchanged=sum(b[k]["correct_rotations"]==a[k]["correct_rotations"] for k in a),
                newly_all_correct=sum(not a[k]["all_correct"] and b[k]["all_correct"] for k in a),
                lost_all_correct=sum(a[k]["all_correct"] and not b[k]["all_correct"] for k in a))
