"""Conservative task classification and independent-seed routing experiment.

Labels describe observable request form, not intelligence or difficulty.
The quality gate uses synthetic generated families only; it never authorizes
deployment or interprets a user request as a benchmark observation.
"""
from collections import Counter
import hashlib
import math
import re
import secrets
import statistics

MAX_BYTES=4096
FAMILIES=("conditional_probability","present_value","simple_interest","weighted_return")
SEED=re.compile(r"[0-9a-f]{32}\Z")
PATTERNS=(
    ("comparison",r"^(?:compare|contrast|distinguish|differentiate)\b|^what (?:is|are) the differences? between\b"),
    ("quantitative",r"^(?:calculate|compute|solve|derive|estimate)\b"),
    ("procedure",r"^(?:how (?:do|can|should) i|how to|give (?:me )?steps|outline (?:the )?procedure)\b"),
    ("evaluation",r"^(?:evaluate|assess|critique|analyse|analyze|verify|justify)\b"),
    ("explanation",r"^(?:explain|define|describe|what (?:is|are|does|do)|why|how does)\b"),
)


def classify(question):
    if not isinstance(question,str):
        raise ValueError("Expected a question.")
    raw=question.encode("utf-8")
    if not raw or len(raw)>MAX_BYTES or "\x00" in question:
        raise ValueError("Question outside classification budget.")
    text=" ".join(question.casefold().split())
    if not text:
        raise ValueError("Empty question.")
    from astra_knowledge_session import FOLLOWUP, AMBIGUOUS, RESET
    if RESET.fullmatch(text):
        label="context_reset"
    elif FOLLOWUP.fullmatch(text) or AMBIGUOUS.search(text):
        label="contextual"
    else:
        matches=[name for name,pattern in PATTERNS if re.search(pattern,text)]
        label=matches[0] if len(matches)==1 else "unclassified"
    return {"task_form":label,
            "review_candidate":label in ("comparison","quantitative","evaluation"),
            "scope":"Heuristic request-form label; not measured difficulty, quality or authorization."}


def _family_metrics(rows):
    result={}
    for family in FAMILIES:
        group=[row for row in rows if row["family"]==family]
        if len(group)!=6:
            raise ValueError("Expected six cases per family per split.")
        self_correct=sum(row["self_review"]["final_correct"] for row in group)
        coop_correct=sum(row["cooperation"]["final_correct"] for row in group)
        wins=sum(row["cooperation"]["final_correct"] and not row["self_review"]["final_correct"] for row in group)
        losses=sum(row["self_review"]["final_correct"] and not row["cooperation"]["final_correct"] for row in group)
        baseline=sum(row["self_review"]["seconds"] for row in group)
        cooperative=sum(row["cooperation"]["seconds"] for row in group)
        ratio=cooperative/baseline if baseline>0 else None
        # Small descriptive gate, not a statistical significance threshold.
        passes=(wins-losses>=2 and losses<=1 and ratio is not None and ratio<=1.5)
        result[family]={"cases":len(group),"self_review_correct":self_correct,
                        "cooperation_correct":coop_correct,"wins":wins,"losses":losses,
                        "latency_ratio":round(ratio,3) if ratio is not None else None,
                        "passes":passes}
    return result


def validate_transfer_reports(seeds,reports):
    """Recompute from complete paired observations, not supplied summaries."""
    from astra_generated_validation import VERSION,generate
    if type(seeds) not in (tuple,list) or len(seeds)!=4 or any(
        not isinstance(seed,str) or not SEED.fullmatch(seed) for seed in seeds
    ) or len(set(seeds))!=4 or type(reports) not in (tuple,list) or len(reports)!=4:
        raise ValueError("Four distinct, preselected transfer seeds and reports required.")
    normalized=[]
    # Reject duplicate rendered questions across splits. Public templates
    # remain related and semantic duplication is still possible.
    seen_questions=set()
    for seed,report in zip(seeds,reports):
        cases=generate(seed)
        if any(case.text in seen_questions for case in cases):
            raise ValueError("Repeated generated question; choose fresh seeds.")
        seen_questions.update(case.text for case in cases)
        expected={case.id:case.family for case in cases}
        if type(report) is not dict or report.get("suite")!=VERSION or report.get("seed_commitment")!=hashlib.sha256(bytes.fromhex(seed)).hexdigest():
            raise ValueError("Report does not match generated suite.")
        rows=report.get("cases")
        if type(rows) is not list or len(rows)!=len(expected):
            raise ValueError("Incomplete transfer report.")
        seen=set()
        for row in rows:
            if type(row) is not dict or set(row)!={"id","family","self_review","cooperation"}:
                raise ValueError("Unexpected paired case fields.")
            ident=row["id"]
            if not isinstance(ident,str) or ident not in expected or ident in seen or row["family"]!=expected[ident]:
                raise ValueError("Invalid generated case identity.")
            seen.add(ident)
            for name in ("self_review","cooperation"):
                arm=row[name]
                if type(arm) is not dict or set(arm)!={"first_correct","final_correct","seconds","calls"}:
                    raise ValueError("Unexpected arm fields.")
                if type(arm["first_correct"]) is not bool or type(arm["final_correct"]) is not bool or type(arm["calls"]) is not int or arm["calls"]!=3:
                    raise ValueError("Invalid correctness or call count.")
                seconds=arm["seconds"]
                if type(seconds) not in (int,float) or not math.isfinite(seconds) or not 0<seconds<=1200:
                    raise ValueError("Invalid measured arm duration.")
        normalized.append(rows)
    calibration=_family_metrics(normalized[0]+normalized[1])
    validation=_family_metrics(normalized[2]+normalized[3])
    candidates=[family for family in FAMILIES if calibration[family]["passes"] and validation[family]["passes"]]
    return {"format":"astra-routing-validation-v1","calibration":calibration,
            "validation":validation,"candidate_families":candidates,
            "model_calls":288,"deployment_allowed":False,
            "scope":"Caller-supplied synthetic observations; distinct seeds, public related templates, six cases per family per split. No automatic routing, statistical significance or general intelligence claim."}
