"""End-to-end scoped contextual reasoning for a narrow explicit request contract."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import subprocess
import tempfile
import time
import urllib.request

import run_qwen_profile_comparison as native
import run_qwen_topic_corroboration as corroboration
from run_qwen_scoped_uncertainty import scalar

SEED = 89413652
CAP = 768
RECOVERY_COMMIT = "e6288481a958131d11e62146f8768900106825e4"
BOUNDARY = "\nCURRENT USER MESSAGE (answer this message, not the excerpts):\n"
SUFFIX = " Reply with only the integer, or UNKNOWN if not established."


def decompose(question):
    """Only an exact, explicitly supported request is decomposed.

    No expected answers, history labels or oracle topics enter this function.
    """
    if not isinstance(question,str) or not question.endswith(SUFFIX):
        return None
    core=question.removesuffix(SUFFIX)
    plan=corroboration.contract(core)
    if plan is None:
        return None
    unit=next((m.group("unit").casefold() for _,pattern in corroboration.GRAMMARS
               if (m:=pattern.fullmatch(core))),None)
    if unit is None:
        return None
    op,a,b=plan
    return op,tuple(f"What is the current {topic} in {unit}?"+SUFFIX for topic in (a,b))


def compose(operation,values):
    if operation not in ("SUM","DIFF") or len(values)!=2 or any(
            type(v) is not int and v!="UNKNOWN" for v in values):
        return None
    if "UNKNOWN" in values:
        return "UNKNOWN"
    a,b=values
    return a+b if operation=="SUM" else abs(a-b)


def cases(seed):
    rng=random.Random(seed)
    values=rng.sample(range(101,999),16)
    topics=[
        ("orchard irrigation budget","tram ticket"),
        ("campus restoration reserve","gallery admission"),
        ("coastal research allocation","storage rental"),
        ("neighbourhood equipment fund","coach fare"),
    ]
    suite=[]
    for i,(a,b) in enumerate(topics):
        x,y,z,w=values[i*4:i*4+4]
        fa,fb=f"Actually, make it {y} coins.",f"No, make it {w} coins."
        missing="Actually, I do not know the current amount."
        prefix=[("user",f"My fictional {a} is {x} coins."),
                ("assistant","Noted."),("user",fa),
                ("user",f"My fictional {b} is {z} coins."),
                ("assistant","Noted.")]
        tail=[("user","Dinner needs vegetables."),
              ("user","Actually, use spicy rice.")]+[
                ("assistant","Unrelated weather discussion. "+"Rain and clouds. "*12)
                for _ in range(12)]
        op="SUM" if i%2==0 else "DIFF"
        q=(f"Add my current {a} and my current {b}, both in coins." if op=="SUM"
           else f"Find the absolute difference between my current {a} and my current {b}, both in coins.")+SUFFIX
        complete=prefix+[("user",fb)]+tail
        suite.append(dict(case=f"complete_{i}",question=q,history=complete,
                          expected=compose(op,(y,w)),operands=(y,w),required=(fa,fb)))
        suite.append(dict(case=f"missing_{i}",question=q,
                          history=prefix+[("user",missing)]+tail,
                          expected="UNKNOWN",operands=(y,"UNKNOWN"),required=(fa,missing)))
        # Unsupported but legitimate single-topic questions must retain the
        # original reasoning route, not be refused by the experimental gate.
        suite.append(dict(case=f"control_{i}",question=f"What is the current {a} in coins?"+SUFFIX,
                          history=complete,expected=y,operands=(y,),required=(fa,)))
    return suite


def recovered(build,question,history):
    prompt=build(question,history)
    if not isinstance(prompt,str) or len(prompt)>3000 or not (
            prompt==question or prompt.endswith(BOUNDARY+question)):
        raise RuntimeError("Recovery changed question or exceeded production context bound")
    return prompt


def prepare(build,item):
    q=item["question"]
    baseline=recovered(build,q,item["history"])
    plan=decompose(q)
    if plan is None:
        return dict(baseline=[baseline],candidate=[baseline],operation=None,route="ordinary")
    op,questions=plan
    prompts=[recovered(build,sub,item["history"]) for sub in questions]
    # Known omitted linked evidence or absent historical excerpts is not
    # sufficient to attempt separate extraction. Preserve normal routing.
    if any(BOUNDARY not in p or "[Some linked corrections are omitted" in p for p in prompts):
        return dict(baseline=[baseline],candidate=[baseline],operation=None,route="fallback")
    return dict(baseline=[baseline],candidate=prompts,operation=op,route="scoped")


def evaluate(seed,infer,build,suite=None):
    suite=cases(seed) if suite is None else suite
    if len({item["case"] for item in suite})!=len(suite):
        raise ValueError("Duplicate cases")
    prepared={item["case"]:prepare(build,item) for item in suite}
    system=native.system_prompt()
    settings=native.PROFILES["qwen_think"]
    rows=[]
    def call(prompt, call_seed):
        payload = dict(prompt=native.raw_prompt(system, prompt, "think"), n_predict=CAP,
                       seed=call_seed, cache_prompt=False, stream=False, ignore_eos=False,
                       repeat_penalty=1.0, presence_penalty=0.0, frequency_penalty=0.0, **settings)
        started = time.monotonic()
        reply = infer(payload)
        elapsed = round((time.monotonic() - started) * 1000)
        for key, value in dict(settings, seed=call_seed, n_predict=CAP).items():
            found = reply.get("generation_settings", {}).get(key)
            if type(found) not in (int,float) or not math.isclose(found, value, rel_tol=1e-6, abs_tol=1e-8):
                raise RuntimeError("Unverified native settings")
        stop, truncated, tokens = reply.get("stop_type"), reply.get("truncated"), reply.get("tokens_predicted")
        if stop not in ("eos","word","limit") or type(truncated) is not bool:
            raise RuntimeError("Unverified native completion")
        if type(tokens) is not int or not 0 <= tokens <= CAP + 8:
            raise RuntimeError("Unverified native token count")
        complete = stop in ("eos","word") and not truncated
        value = scalar(native.visible_answer(reply.get("content"))) if complete else None
        return value, dict(complete=complete, tokens=tokens, elapsed_ms=elapsed,
                           canonical=value is not None,
                           prompt_sha256=hashlib.sha256(payload["prompt"].encode()).hexdigest())
    for i,item in enumerate(suite):
        p=prepared[item["case"]]
        seed0=(seed+i*13)%(2**31-1)
        for arm in (("baseline","candidate") if i%2==0 else ("candidate","baseline")):
            values,records=[],[]
            for j,prompt in enumerate(p[arm]):
                value,record=call(prompt,(seed0+j)%(2**31-1))
                head=prompt.rsplit(BOUNDARY,1)[0] if BOUNDARY in prompt else ""
                record["required_fragments_retained"]=sum(f in head for f in item["required"])
                values.append(value)
                records.append(record)
            scoped=arm=="candidate" and p["route"]=="scoped"
            result=compose(p["operation"],values) if scoped else values[0]
            count=len(item["operands"]) if scoped else 0
            correct_operands=sum(a==b for a,b in zip(values,item["operands"])) if scoped else 0
            rows.append(dict(case=item["case"],arm=arm,route=p["route"],
                             complete=all(r["complete"] for r in records),
                             correct=result is not None and result==item["expected"],
                             explicit_unknown=result=="UNKNOWN",
                             unsupported_numeric=item["expected"]=="UNKNOWN" and type(result) is int,
                             operand_count=count,correct_operands=correct_operands,
                             wrong_operand_correct_result=scoped and result==item["expected"] and correct_operands!=count,
                             calls=records,tokens=sum(r["tokens"] for r in records),
                             elapsed_ms=sum(r["elapsed_ms"] for r in records)))
    def summary(group):
        return {arm:dict(attempted=len(rr),correct=sum(r["correct"] for r in rr),
                         complete=sum(r["complete"] for r in rr),
                         calls=sum(len(r["calls"]) for r in rr),
                         explicit_unknown=sum(r["explicit_unknown"] for r in rr),
                         unsupported_numeric=sum(r["unsupported_numeric"] for r in rr),
                         correct_operands=sum(r["correct_operands"] for r in rr),
                         operand_count=sum(r["operand_count"] for r in rr),
                         wrong_operand_correct_result=sum(r["wrong_operand_correct_result"] for r in rr),
                         tokens=sum(r["tokens"] for r in rr),elapsed_ms=sum(r["elapsed_ms"] for r in rr))
                for arm in ("baseline","candidate") for rr in [[r for r in group if r["arm"]==arm]]}
    matched=dict(both_correct=0,baseline_only=0,candidate_only=0,neither=0)
    for item in suite:
        scores={r["arm"]:r["correct"] for r in rows if r["case"]==item["case"]}
        a,b=scores["baseline"],scores["candidate"]
        matched["both_correct" if a and b else "baseline_only" if a else "candidate_only" if b else "neither"]+=1
    return dict(status="COMPLETE_CONTRACT_SCOPED_DIAGNOSTIC",seed=seed,cap=CAP,
                model_sha256=native.MODEL_SHA256,recovery_commit=RECOVERY_COMMIT,
                system_prompt_sha256=hashlib.sha256(system.encode()).hexdigest(),
                totals=summary(rows),matched=matched,
                by_domain={d:summary([r for r in rows if r["case"].startswith(d+"_")])
                           for d in ("complete","missing","control")},
                routes={route:sum(p["route"]==route for p in prepared.values())
                        for route in ("ordinary","fallback","scoped")},
                cases=rows,official_ai_rating=None,
                scope="Public synthetic end-to-end host diagnostic. Deterministic full-match request decomposition, exact production Kotlin recovery, model-extracted operands and bounded integer computation; one ordinary call versus two scoped calls, or identical fallback. No oracle enters routing. Source relevance is not semantic verification; correct UNKNOWN may conceal incorrect known operand. Not general comprehension or independent assessment; no raw replies retained.")


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
