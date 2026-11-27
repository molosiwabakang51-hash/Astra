"""Public developmental multiple-choice probes; no training or private items.

Choice likelihoods isolate the next-token decision from answer formatting.
This is recognition, not free generation or an independent intelligence score.
"""
from dataclasses import dataclass
import hashlib
import json
import math
import random

SEED = 91502047
LABELS = ("A","B","C","D")
DOMAINS = ("causal","logic","abstraction","grounding","quantitative","retention")

@dataclass(frozen=True)
class Case:
    ident: str
    domain: str
    question: str
    options: tuple
    correct: int

def raw_cases():
    # All questions specify the necessary assumptions; the fourth option is
    # not a universal abstention cue. No examples from the training pilot.
    for i,(a,b) in enumerate(((0,0),(0,1),(1,0),(1,1))):
        # X is OR(A,B), Y is XOR(X,B); force X to the opposite of its original value.
        x=int(bool(a or b)); forced=1-x; y=forced^b
        options=("X=0, Y=0","X=0, Y=1","X=1, Y=0","X=1, Y=1")
        q=(f"Two fixed inputs are A={a} and B={b}. X is their logical OR. "
           f"Y is the exclusive OR of X and B. Override only X, forcing X={forced}, "
           "and recompute Y while keeping both original inputs. What are X and Y?")
        yield ("causal",q,options,2*forced+y)
    yield ("logic","Every copper token is marked. Some marked tokens are round. Which claim must follow?",
           ("Some copper tokens are round","Every round token is copper","Every copper token is marked","No copper token is round"),2)
    yield ("logic","If P holds, Q holds. If Q holds, R holds. R is false. Which conclusion is forced?",
           ("P is false","P is true","Q is true","P may be true"),0)
    yield ("logic","Exactly one of J and K is true. K and L have the same truth value. L is false. Which statement follows?",
           ("J is false","J is true","L is true","J and K are both true"),1)
    yield ("logic","All zibs are wugs. No wugs are nals. There exists a zib. Which claim must hold?",
           ("Some nals are zibs","All wugs are zibs","No zibs are nals, but no zibs exist","Some wugs are not nals"),3)
    for i,(a,b) in enumerate(((2,5),(3,7),(4,9),(5,8))):
        q=(f"A transformation maps an ordered pair (x,y) to (y,x+y). "
           f"Starting from ({a},{b}), apply it twice. What pair results?")
        yield ("abstraction",q,(f"({a+b},{a+2*b})",f"({b},{a+b})",f"({a+2*b},{a+b})",f"({a},{a+2*b})"),0)
    yield ("grounding","A note says: 'On Monday, Nara lent the blue key to Ivo. On Tuesday, Ivo returned it to Nara, then Nara lent it to Pia.' Who has the blue key after Tuesday?",
           ("Ivo","Pia","Nara","Not stated"),1)
    yield ("grounding","The report says the east bridge opened, the west bridge was proposed but not approved, and the north bridge was closed. Which bridge is confirmed open?",
           ("North","West","All three","East"),3)
    yield ("grounding","A record assigns ticket 51 to room K. A later notice changes ticket 15 to room M. What is the last stated room for ticket 51?",
           ("K","M","Both K and M","Unknown"),0)
    yield ("grounding","A short passage reports that a sample turned green when heated. It does not mention a second sample. What can be concluded about the second sample?",
           ("It turned green","It stayed unchanged","Its response is unspecified","It was heated longer"),2)
    for i,(a,b,c) in enumerate(((17,4,5),(29,3,7),(41,2,9),(53,5,8))):
        # inverse problem not present in the initial curriculum
        total=a*b+c
        q=(f"A total of {total} objects consists of {b} equal groups plus {c} separate objects. "
           "How many objects are in each equal group?")
        yield ("quantitative",q,(str(a),str(a+c),str(a-1),str(a*b)),0)
    yield ("retention","Which gas is most abundant in Earth's atmosphere?",("Oxygen","Nitrogen","Carbon dioxide","Argon"),1)
    yield ("retention","Which organ pumps blood through the human body?",("Lungs","Liver","Heart","Kidney"),2)
    yield ("retention","Which sentence is grammatically correct in standard English?",("She go to school.","They goes to school.","He going school.","She goes to school."),3)
    yield ("retention","Which word is closest in meaning to 'scarce'?",("Limited","Abundant","Identical","Rapid"),0)

def cases(seed=SEED):
    rng=random.Random(seed)
    result=[]
    for d in DOMAINS:
        rows=[r for r in raw_cases() if r[0]==d]
        if len(rows)!=4:
            raise ValueError("Unbalanced domain")
        positions=list(range(4)); rng.shuffle(positions)
        for i,((domain,q,opts,correct),position) in enumerate(zip(rows,positions)):
            if len(set(opts))!=4 or not 0<=correct<4:
                raise ValueError("Invalid answer options")
            wrong=[v for j,v in enumerate(opts) if j!=correct]
            rng.shuffle(wrong)
            wrong.insert(position,opts[correct])
            result.append(Case(f"{domain}_{i}",domain,q,tuple(wrong),position))
    rng.shuffle(result)
    return tuple(result)

def prompt(case):
    return case.question+"\n"+"\n".join(f"{label}. {text}" for label,text in zip(LABELS,case.options))+"\nChoose the best answer. Reply with only A, B, C or D."

def suite_digest(suite):
    return hashlib.sha256(json.dumps([c.__dict__ for c in suite],sort_keys=True,
                                    separators=(",",":"),ensure_ascii=True).encode()).hexdigest()

def score(logits,correct):
    if len(logits)!=4 or type(correct) is not int or not 0<=correct<4:
        raise ValueError("Invalid choice scores")
    if any(not math.isfinite(x) for x in logits):
        raise ValueError("Nonfinite logits")
    m=max(logits)
    weights=[math.exp(x-m) for x in logits]
    probs=[x/sum(weights) for x in weights]
    # Ties count as wrong rather than rewarding positional tie-break bias.
    winners=[i for i,x in enumerate(logits) if x==m]
    return dict(correct=winners==[correct],tie=len(winners)>1,
                correct_probability=probs[correct],confidence=max(probs),
                negative_log_likelihood=(m-logits[correct])+math.log(sum(weights)),
                brier=sum((p-(i==correct))**2 for i,p in enumerate(probs)),
                predicted=LABELS[winners[0]] if len(winners)==1 else None)

def summarize(rows):
    if not rows or len({r["ident"] for r in rows})!=len(rows):
        raise ValueError("Missing or duplicate evaluation rows")
    groups={}
    for domain in ("all",)+DOMAINS:
        rr=[r for r in rows if domain=="all" or r["domain"]==domain]
        if not rr:
            continue
        groups[domain]=dict(attempted=len(rr),correct=sum(r["correct"] for r in rr),
            ties=sum(r["tie"] for r in rr),mean_nll=sum(r["negative_log_likelihood"] for r in rr)/len(rr),
            mean_brier=sum(r["brier"] for r in rr)/len(rr),
            mean_confidence=sum(r["confidence"] for r in rr)/len(rr))
    return groups
