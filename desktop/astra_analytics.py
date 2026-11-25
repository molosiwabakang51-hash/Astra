"""Single local entry point for the desktop financial analysis modules.

The registry is fixed, not supplied by a model or user script. Every engine
retains its own validation and conventions. File identities describe input
bytes, not independent source verification or live execution attestation.
"""
import argparse
import hashlib
import importlib
import importlib.util
import json
from pathlib import Path
import sys

from astra_analytics_csv import ingest
from astra_verified_finance import unique_object, invalid_constant

MAX_JSON_BYTES=524288
REGISTRY={
    "portfolio":("astra_portfolio_analytics","astra_portfolio_report"),
    "cashflow":("astra_cashflow_performance","astra_cashflow_report"),
    "bond":("astra_fixed_income","astra_bond_report"),
    "curve":("astra_yield_curve","astra_yield_curve_report"),
    "optimization":("astra_portfolio_optimize","astra_optimization_report"),
    "historical-risk":("astra_historical_risk","astra_historical_risk_report"),
    "simulation":("astra_bootstrap_simulation","astra_simulation_report"),
    "backtest":("astra_strategy_backtest","astra_backtest_report"),
}


def available():
    return {name:all(importlib.util.find_spec(module) is not None for module in modules)
            for name,modules in REGISTRY.items()}


def read_json(path):
    with Path(path).open("rb") as source:
        raw=source.read(MAX_JSON_BYTES+1)
    if len(raw)>MAX_JSON_BYTES:
        raise ValueError("JSON exceeds 512 KiB.")
    value=json.loads(raw.decode("utf-8-sig"),object_pairs_hook=unique_object,
                     parse_constant=invalid_constant)
    if type(value) is not dict:
        raise ValueError("Expected JSON object.")
    return value,hashlib.sha256(raw).hexdigest()


def canonical_digest(request):
    serialized=json.dumps(request,ensure_ascii=True,sort_keys=True,separators=(",",":"),
                          allow_nan=False)
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()


def run_analysis(mode,request,*,receipt=None):
    if mode not in REGISTRY:
        raise ValueError("Unsupported analysis.")
    try:
        engine,reporter=(importlib.import_module(name) for name in REGISTRY[mode])
    except ImportError:
        raise RuntimeError("Selected analytics module is unavailable in this build.") from None
    request_digest=canonical_digest(request)
    # Each analysis applies its own numerical validation to a frozen copy.
    frozen=json.loads(json.dumps(request,allow_nan=False))
    result=engine.analyze(frozen)
    provenance={"analysis":mode,"request_sha256":request_digest,
                "scope":"Input fingerprints establish byte/request identity only; data not independently verified."}
    if receipt is not None:
        provenance["input"]=receipt
    return {"provenance":provenance,"result":result},reporter.report(result)


def render(envelope,markdown,format):
    if format=="json":
        return json.dumps(envelope,ensure_ascii=False,indent=2,allow_nan=False)+"\n"
    if format!="markdown":
        raise ValueError("Unsupported report format.")
    provenance=envelope["provenance"]
    lines=["","## Input traceability","",f"Analysis: {provenance['analysis']}.",
           f"Canonical request SHA-256: {provenance['request_sha256']}.",
           provenance["scope"]]
    receipt=provenance.get("input",{})
    for key in ("source_sha256","metadata_sha256","rows","first_date","last_date","alignment"):
        if key in receipt:
            lines.append(f"- {key.replace('_',' ')}: {receipt[key]}")
    return markdown.rstrip()+"\n"+"\n".join(lines)+"\n"



class ReportState:
    """Associate a completed report with the current input revision."""
    def __init__(self):
        self.revision=0
        self.report=None

    def invalidate(self):
        self.revision+=1
        self.report=None
        return self.revision

    def publish(self,revision,envelope,markdown):
        if revision!=self.revision:
            return False
        self.report=(envelope,markdown)
        return True


def professional_available():
    return importlib.util.find_spec("astra_professional_report") is not None


def export_content(envelope,markdown,format):
    """Export the completed result without recomputation or rereading input."""
    if format=="html":
        try:
            from astra_professional_report import render_html
        except ImportError:
            raise RuntimeError("Professional renderer is not installed.") from None
        return render_html(render(envelope,markdown,"markdown"),
                           envelope["provenance"]["analysis"],envelope["result"])
    return render(envelope,markdown,format)


def from_files(mode,input_path,*,metadata_path=None):
    if metadata_path is None:
        request,digest=read_json(input_path)
        receipt={"source_sha256":digest,"format":"JSON"}
    else:
        metadata,md=read_json(metadata_path)
        request,receipt=ingest(mode,input_path,metadata)
        receipt["metadata_sha256"]=md
    return run_analysis(mode,request,receipt=receipt)


def write_new(path,content):
    with Path(path).open("x",encoding="utf-8",newline="\n") as destination:
        destination.write(content)


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--list",action="store_true",help="List installed analytics modules")
    p.add_argument("mode",nargs="?",choices=tuple(REGISTRY))
    p.add_argument("input",nargs="?",help="JSON request or CSV when --metadata supplied")
    p.add_argument("--metadata",help="Explicit analysis metadata JSON for CSV import")
    p.add_argument("--format",choices=("markdown","json"),default="markdown")
    p.add_argument("--output",help="Create new report without overwriting")
    args=p.parse_args(argv)
    try:
        if args.list:
            if any((args.mode,args.input,args.metadata,args.output)):
                raise ValueError("List mode does not accept analysis files.")
            print(json.dumps(available(),indent=2))
            return 0
        if not args.mode or not args.input:
            raise ValueError("Choose an analysis and input file.")
        envelope,markdown=from_files(args.mode,args.input,metadata_path=args.metadata)
        content=render(envelope,markdown,args.format)
        if args.output:
            write_new(args.output,content)
        else:
            sys.stdout.write(content)
        return 0
    except (OSError,ValueError,TypeError,RuntimeError,RecursionError,ArithmeticError):
        print("Analytics request unavailable or invalid; verify the mode, input convention and files.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
