"""Public contrastive training data, disjoint from the transfer evaluation.

Each pair keeps answer options identical and alters a single premise or
requested operation. The two correct labels differ. The label distribution
is balanced per domain. No private data or answers supplied in the question.
"""
import random
from qwen_transfer_choices import Case, LABELS, cases as transfer_cases

SEED = 41087253
DOMAINS = ("causal", "logic", "abstraction", "quantitative")

def raw_pairs():
    for i,(a,b) in enumerate(((0,0),(0,1),(1,0),(1,1))):
        options=tuple(f"R={r}, S={s}" for r in range(2) for s in range(2))
        questions=tuple(
            f"Input U is {a} and V is {b}. R normally equals U AND V. "
            f"S equals R XOR U. In a modified machine force R={r}, overriding its rule, "
            "while all other rules and inputs stay fixed. What is the resulting pair R,S?"
            for r in (0,1))
        yield "causal",questions,options,(a,2+(1^a))
    for i,(p,q) in enumerate((("a vault is sealed","its signal is blue"),
                             ("a turbine is running","its status lamp is lit"),
                             ("a parcel is certified","it has a label"),
                             ("a door is locked","its sensor is active"))):
        options=("The first condition must be false.","The first condition may be true or false.",
                 "The first condition must be true.","The observations contradict the rule.")
        questions=tuple(
            f"Whenever {p}, {q}. The second condition is observed to be {v}. "
            "This is the only implication given. What follows about the first condition?"
            for v in ("false","true"))
        yield "logic",questions,options,(0,1)
    for i,(a,b) in enumerate(((3,8),(6,11),(9,14),(12,17))):
        # Repeated transformation differs from the public transfer transformation.
        first=(a+b,b-a)
        second=(first[0]+first[1],first[1]-first[0])
        values=(first,second,(a,b),(b,a))
        options=tuple(f"({x},{y})" for x,y in values)
        questions=tuple(
            f"The transformation maps (x,y) to (x+y,y-x), using both old "
            f"coordinates simultaneously. Start at ({a},{b}). Apply it exactly {n} time(s). "
            "Which pair results?" for n in (1,2))
        yield "abstraction",questions,options,(0,1)
    for i,(g,a,b,extra) in enumerate(((3,12,15,2),(4,17,21,3),(5,19,23,4),(6,24,29,5))):
        options=tuple(str(x) for x in (a,b,a-1,b+1))
        questions=tuple(
            f"There are {g} identical sealed boxes plus {extra} loose items contain "
            f"{g*x+extra} items in total. Each box contains the same number. "
            "How many items are inside one box?" for x in (a,b))
        yield "quantitative",questions,options,(0,1)

def training_cases(seed=SEED):
    rng=random.Random(seed)
    indices={d:0 for d in DOMAINS}
    positions_by_domain={d:rng.sample(range(4),4) for d in DOMAINS}
    result=[]
    for domain,questions,options,answers in raw_pairs():
        i=indices[domain]; indices[domain]+=1
        if len(set(options))!=4 or answers[0]==answers[1] or questions[0]==questions[1]:
            raise ValueError("Invalid contrastive pair")
        # Each of four positions occurs exactly twice as a correct answer
        # across the eight cases in each domain.
        permutation=positions_by_domain[domain]
        positions=(permutation[i],permutation[(i+1)%4])
        order=[None]*4
        order[positions[0]]=options[answers[0]]
        order[positions[1]]=options[answers[1]]
        wrong=[v for j,v in enumerate(options) if j not in answers]
        rng.shuffle(wrong)
        for j in range(4):
            if order[j] is None:
                order[j]=wrong.pop()
        for k,q in enumerate(questions):
            result.append(Case(f"train_{domain}_{i}_{k}",domain,q,tuple(order),positions[k]))
    if indices!={d:4 for d in DOMAINS}:
        raise RuntimeError("Incomplete training domains")
    if len({c.question for c in result})!=32 or ({c.question for c in result}&
            {c.question for c in transfer_cases()}):
        raise RuntimeError("Training/evaluation overlap")
    rng.shuffle(result)
    return tuple(result)
