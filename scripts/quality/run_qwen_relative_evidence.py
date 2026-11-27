"""Matched relative-only evidence instruction on scoped thinking-mode extraction."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import re
import subprocess
import tempfile
import time
import urllib.request

import run_qwen_profile_comparison as native
import run_qwen_contract_scoped as prior
from run_qwen_scoped_uncertainty import scalar

SEED = 19462837
CAP = prior.CAP
RECOVERY_COMMIT = prior.RECOVERY_COMMIT
EVIDENCE_RULE = (
    "\nFor a scoped question about a remembered quantity, answer within the user's stated scenario. "
    "An explicitly fictional premise is still a usable premise of that scenario, not an unsupported "
    "real-world claim. Read the relevant Previous user excerpts in chronological order. Apply only "
    "corrections belonging to that quantity. An explicit replacement supersedes the earlier amount; "
    "a relative adjustment requires its preceding established amount. A later statement that the "
    "current amount is unknown supersedes earlier numerical amounts. Never take an unrelated amount "
    "or an assistant guess as the owner's current value. Use UNKNOWN if the required value is not "
    "established or necessary evidence is missing, not merely because the scenario is fictional "
    "or cannot be externally verified. Follow the requested scalar answer format."
)



# The routing decision inspects only the serialized recovered evidence.
# This is a narrow lexical admission test, not proof that a correction
# belongs to a topic. Do not activate for assistant quotes, a superseded
# adjustment, missing evidence, ordinary requests or unsupported units.
RELATIVE = re.compile(r'Previous user: "Actually, (?:increase|decrease) it by [0-9]{1,9} coins\."')
CURRENT = re.compile(r"What is the current [a-z][a-z -]{2,90} in coins\?"+re.escape(prior.SUFFIX))


def relative_evidence(prompt):
    if not isinstance(prompt,str) or prompt.count(prior.BOUNDARY)!=1:
        return False
    head,question=prompt.split(prior.BOUNDARY)
    if not CURRENT.fullmatch(question) or "[Some linked corrections are omitted" in head:
        return False
    owners=[line for line in head.splitlines() if line.startswith('Previous user: "')]
    return bool(owners and RELATIVE.fullmatch(owners[-1]))



def cases(seed):
    rng=random.Random(seed)
    values=rng.sample(range(100,800),16)
    topics=[("wetland restoration budget","tram ticket"),
            ("technical workshop allocation","museum admission"),
            ("village archive reserve","parking fee"),
            ("riverside research fund","locker rental")]
    suite=[]
    for i,(a,b) in enumerate(topics):
        x,y,z,w=values[i*4:i*4+4]
        delta=13+i*9
        decrease=5+i
        first=f"My fictional {a} is {x} coins."
        second=f"My fictional {b} is {z} coins."
        fa,fb=f"Actually, make it {y} coins.",f"No, make it {w} coins."
        add=f"Actually, increase it by {delta} coins."
        subtract=f"Actually, decrease it by {decrease} coins."
        unknown="Actually, I do not know the current amount."
        tail=[("user","Dinner needs vegetables."),
              ("user","Actually, use spicy rice.")]+[
                  ("assistant","Unrelated weather discussion. "+"Rain and clouds. "*12)
                  for _ in range(12)]
        op="SUM" if i%2==0 else "DIFF"
        q=(f"Add my current {a} and my current {b}, both in coins." if op=="SUM"
           else f"Find the absolute difference between my current {a} and my current {b}, both in coins.")+prior.SUFFIX
        def history(ca,cb):
            return [("user",first),("assistant","Noted.")]+[("user",v) for v in ca]+[
                ("user",second),("assistant","Noted.")]+[("user",v) for v in cb]+tail
        def add_case(domain,ca,cb,operands,required):
            suite.append(dict(case=f"{domain}_{i}",question=q,history=history(ca,cb),
                              expected=prior.compose(op,operands),operands=operands,
                              required=required))
        add_case("absolute",(fa,),(fb,),(y,w),((first,fa),(second,fb)))
        # Keep three chain members per relevant topic at most. Both absolute
        # replacement and relative updates must survive actual serialization.
        add_case("relative",(fa,add),(subtract,),(y+delta,z-decrease),
                 ((first,fa,add),(second,subtract)))
        if i%2==0:
            add_case("missing",(fa,unknown),(fb,),("UNKNOWN",w),
                     ((first,fa,unknown),(second,fb)))
        else:
            add_case("missing",(fa,),(fb,unknown),(y,"UNKNOWN"),
                     ((first,fa),(second,fb,unknown)))
        # Ordinary single-topic control gets precisely the old system in both arms.
        suite.append(dict(case=f"control_{i}",
                          question=f"What is the current {a} in coins?"+prior.SUFFIX,
                          history=history((fa,),(fb,)),expected=y,
                          operands=(y,),required=((first,fa),)))
    # Each domain has both arm orders, with order swapped every topic pair.
    return [item for i in range(4) for item in
            (suite[4*i:4*i+4] if i%2==0 else [suite[4*i+1],suite[4*i],suite[4*i+3],suite[4*i+2]])]


def prepare(build,item):
    p=prior.prepare(build,item)
    expected_route="ordinary" if item["case"].startswith("control_") else "scoped"
    if p["route"]!=expected_route:
        raise RuntimeError("Fixture does not exercise expected extraction route")
    for j,prompt in enumerate(p["candidate"]):
        head=prompt.rsplit(prior.BOUNDARY,1)[0]
        if not all(f in head for f in item["required"][j]):
            raise RuntimeError("Required source premise or correction missing")
        if "Actually, use spicy rice." in head or "[Some linked corrections are omitted" in head:
            raise RuntimeError("Unrelated or incomplete evidence")
    # Check the diagnostic actually exercises the intended gate; labels
    # are used only for fixture validation, never for admission.
    actual=[relative_evidence(prompt) for prompt in p["candidate"]]
    if item["case"].startswith("relative_"):
        if not all(actual):
            raise RuntimeError("Relative fixture did not trigger evidence gate")
    elif any(actual):
        raise RuntimeError("Nonrelative control triggered evidence gate")
    return p


def evaluate(seed,infer,build,suite=None):
    suite=cases(seed) if suite is None else suite
    if len({i["case"] for i in suite})!=len(suite):
        raise ValueError("Duplicate cases")
    prepared={i["case"]:prepare(build,i) for i in suite}
    system=native.system_prompt()
    rows=[]
    def call(prompt,system_text,call_seed):
        settings=native.PROFILES["qwen_think"]
        payload=dict(prompt=native.raw_prompt(system_text,prompt,"think"),n_predict=CAP,
                     seed=call_seed,cache_prompt=False,stream=False,ignore_eos=False,
                     repeat_penalty=1.0,presence_penalty=0.0,frequency_penalty=0.0,**settings)
        started=time.monotonic()
        reply=infer(payload)
        elapsed=round((time.monotonic()-started)*1000)
        for k,v in dict(settings,seed=call_seed,n_predict=CAP).items():
            found=reply.get("generation_settings",{}).get(k)
            if type(found) not in (int,float) or not math.isclose(found,v,rel_tol=1e-6,abs_tol=1e-8):
                raise RuntimeError("Unverified native settings")
        stop,truncated,tokens=reply.get("stop_type"),reply.get("truncated"),reply.get("tokens_predicted")
        if stop not in ("eos","word","limit") or type(truncated) is not bool:
            raise RuntimeError("Unverified native completion")
        if type(tokens) is not int or not 0<=tokens<=CAP+8:
            raise RuntimeError("Unverified native token count")
        complete=stop in ("eos","word") and not truncated
        value=scalar(native.visible_answer(reply.get("content"))) if complete else None
        return value,dict(complete=complete,canonical=value is not None,
                          tokens=tokens,elapsed_ms=elapsed,
                          prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest())
    for i,item in enumerate(suite):
        p=prepared[item["case"]]
        scoped=p["route"]=="scoped"
        seed0=(seed+i*13)%(2**31-1)
        for arm in (("baseline","evidence") if i%2==0 else ("evidence","baseline")):
            values,records=[],[]
            # Both arms get exactly the same recovered evidence and subquestions.
            # Only the scoped extraction system instruction changes.
            for j,prompt in enumerate(p["candidate"]):
                applied=scoped and arm=="evidence" and relative_evidence(prompt)
                system_text=system+EVIDENCE_RULE if applied else system
                value,record=call(prompt,system_text,(seed0+j)%(2**31-1))
                record["rule_applied"]=applied
                expected=item["operands"][j]
                record.update(operand_correct=value==expected,
                              false_unknown=value=="UNKNOWN" and type(expected) is int,
                              unsupported_numeric=type(value) is int and expected=="UNKNOWN")
                values.append(value)
                records.append(record)
            result=prior.compose(p["operation"],values) if scoped else values[0]
            count=len(item["operands"])
            correct_operands=sum(r["operand_correct"] for r in records)
            rows.append(dict(case=item["case"],arm=arm,route=p["route"],
                             correct=result is not None and result==item["expected"],
                             complete=all(r["complete"] for r in records),
                             operand_count=count,correct_operands=correct_operands,
                             false_unknown=result=="UNKNOWN" and type(item["expected"]) is int,
                             unsupported_numeric=type(result) is int and item["expected"]=="UNKNOWN",
                             correct_result_wrong_operand=result==item["expected"] and correct_operands!=count,
                             calls=records,tokens=sum(r["tokens"] for r in records),
                             elapsed_ms=sum(r["elapsed_ms"] for r in records)))
    def summary(group):
        return {arm:dict(attempted=len(rr),correct=sum(r["correct"] for r in rr),
                         complete=sum(r["complete"] for r in rr),
                         calls=sum(len(r["calls"]) for r in rr),
                         operand_count=sum(r["operand_count"] for r in rr),
                         correct_operands=sum(r["correct_operands"] for r in rr),
                         false_unknown=sum(r["false_unknown"] for r in rr),
                         false_unknown_operands=sum(c["false_unknown"] for r in rr for c in r["calls"]),
                         rule_applied_calls=sum(c["rule_applied"] for r in rr for c in r["calls"]),
                         unsupported_numeric=sum(r["unsupported_numeric"] for r in rr),
                         unsupported_numeric_operands=sum(c["unsupported_numeric"] for r in rr for c in r["calls"]),
                         correct_result_wrong_operand=sum(r["correct_result_wrong_operand"] for r in rr),
                         tokens=sum(r["tokens"] for r in rr),elapsed_ms=sum(r["elapsed_ms"] for r in rr))
                for arm in ("baseline","evidence") for rr in [[r for r in group if r["arm"]==arm]]}
    matched=dict(both_correct=0,baseline_only=0,candidate_only=0,neither=0)
    for item in suite:
        scores={r["arm"]:r["correct"] for r in rows if r["case"]==item["case"]}
        a,b=scores["baseline"],scores["evidence"]
        matched["both_correct" if a and b else "baseline_only" if a else "candidate_only" if b else "neither"]+=1
    return dict(status="COMPLETE_RELATIVE_EVIDENCE_DIAGNOSTIC",seed=seed,cap=CAP,
                model_sha256=native.MODEL_SHA256,recovery_commit=RECOVERY_COMMIT,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                evidence_rule_sha256=hashlib.sha256(EVIDENCE_RULE.encode()).hexdigest(),
                totals=summary(rows),matched=matched,
                by_domain={d:summary([r for r in rows if r["case"].startswith(d+"_")])
                           for d in ("absolute","relative","missing","control")},
                cases=rows,official_ai_rating=None,
                scope="Public synthetic fresh topic/value templates, exact contract-derived subquestions and pinned production Kotlin retrieval, same thinking profile, paired seeds and cap. Apply #507 evidence rule only to a scoped prompt whose latest serialized owner line is a narrow relative adjustment in coins. Nonrelative prompts byte-identical. Gate does not establish semantic relevance. Measure accuracy, operand errors, abstention and cost. No live activation or independent assessment; no raw replies retained.")


def run(binary, seed, build):
    if not native.check_model(native.MODEL_PATH):
        raise RuntimeError("Unverified model")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    command = [str(binary.resolve()), "-m", str(native.MODEL_PATH), "--host", "127.0.0.1",
               "--port", str(native.PORT), "--ctx-size", str(native.CONTEXT), "--parallel", "1",
               "--threads", "4", "--batch-size", "256", "--ubatch-size", "256",
               "--n-gpu-layers", "0", "--no-context-shift"]
    with tempfile.TemporaryFile() as log:
        process = subprocess.Popen(command, stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Native server exited")
                try:
                    if native.request(opener, "/health", timeout=3).get("status") == "ok":
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("Native server startup timed out")
            report = evaluate(seed, lambda payload: native.request(opener, "/completion", payload), build)
            if not native.check_model(native.MODEL_PATH):
                raise RuntimeError("Model identity changed")
            return report
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--candidate-context-jar", type=Path, required=True)
    args = parser.parse_args()
    if not 0 <= args.seed < 2**31 - 1:
        parser.error("Invalid seed")
    build = lambda q, h: native.recovery_prompt(args.candidate_context_jar, q, h)
    print(json.dumps(run(args.binary, args.seed, build), indent=2, allow_nan=False))
