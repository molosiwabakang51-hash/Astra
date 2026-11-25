"""Build a reproducible, model-free source distribution for Astra Desktop.

Only the explicitly enumerated release files can enter the ZIP. No local
databases, credentials, model weights, caches or arbitrary directory contents.
Python and Tkinter are prerequisites on the target machine.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import stat
import zipfile

ROOT=Path(__file__).resolve().parent
CORE=(
    "astra_launcher.py","astra_frozen_smoke.py","astra_desktop_distribution.py","astra_desktop_gui.py","astra_workbench.py",
    "astra_evidence.py","astra_finance_session.py","astra_natural_language.py",
    "astra_verified_finance.py","astra_quantitative_verify.py",
    "astra_hybrid_evidence.py","astra_vector_memory.py",
    "astra_knowledge_session.py","astra_orchestrator.py","astra_local_answer.py",
    "astra_local_finance.py","astra_resource_plan.py",
    "ASTRA_START.cmd","DESKTOP_DISTRIBUTION.md",
)
GROUPS={
    "portfolio":("astra_portfolio_analytics.py","astra_portfolio_report.py","examples/portfolio_analysis.json"),
    "cashflow":("astra_cashflow_performance.py","astra_cashflow_report.py","examples/cashflow_performance.json"),
    "bond":("astra_fixed_income.py","astra_bond_report.py","examples/bond_analysis.json"),
    "curve":("astra_yield_curve.py","astra_yield_curve_report.py","examples/yield_curve.json"),
    "optimization":("astra_portfolio_optimize.py","astra_optimization_report.py","examples/portfolio_optimization.json"),
    "historical-risk":("astra_historical_risk.py","astra_historical_risk_report.py","examples/historical_risk.json"),
    "simulation":("astra_bootstrap_simulation.py","astra_simulation_report.py","examples/bootstrap_simulation.json"),
    "backtest":("astra_strategy_backtest.py","astra_backtest_report.py","examples/strategy_backtest.json"),
    "analytics":("astra_analytics.py","astra_analytics_csv.py","astra_analytics_gui.py"),
    "professional-report":("astra_professional_report.py","astra_report_export.py"),
}
OPTIONAL=(
    "WORKBENCH.md","PORTFOLIO_ANALYTICS.md","CASHFLOW_PERFORMANCE.md",
    "FIXED_INCOME.md","YIELD_CURVE.md","PORTFOLIO_OPTIMIZATION.md",
    "HISTORICAL_RISK.md","BOOTSTRAP_SIMULATION.md","STRATEGY_BACKTEST.md",
    "ANALYTICS_WORKSPACE.md","PROFESSIONAL_REPORTS.md",
    "examples/portfolio_holdings.csv","examples/portfolio_metadata.json",
    "examples/cashflow_events.csv","examples/cashflow_metadata.json",
)
MAX_FILE=2*1024*1024
MAX_TOTAL=16*1024*1024
STAMP=(2026,1,1,0,0,0)


def safe_read(root,name):
    path=root/name
    # Reject symlinks in every path component, not merely the final file.
    for parent in (path,*path.parents):
        if parent==root:
            break
        if parent.is_symlink():
            raise ValueError("Release path contains a symlink.")
    if not path.is_file() or not path.resolve().is_relative_to(root):
        raise ValueError("Required release file missing or outside root.")
    with path.open("rb") as source:
        value=source.read(MAX_FILE+1)
    if len(value)>MAX_FILE:
        raise ValueError("Release file exceeds size budget.")
    return value


def collect(root):
    root=Path(root).resolve()
    selected={name:safe_read(root,name) for name in CORE}
    included=[]
    for group,names in GROUPS.items():
        exists=[(root/name).exists() or (root/name).is_symlink() for name in names]
        if not any(exists):
            continue
        if not all(exists):
            raise ValueError("Incomplete release group: "+group)
        selected.update({name:safe_read(root,name) for name in names})
        included.append(group)
    if "professional-report" in included and "analytics" not in included:
        raise ValueError("Professional reporting requires analytics.")
    for name in OPTIONAL:
        if (root/name).exists() or (root/name).is_symlink():
            selected[name]=safe_read(root,name)
    if sum(map(len,selected.values()))>MAX_TOTAL:
        raise ValueError("Source distribution exceeds size budget.")
    return selected,included


def entry(name,data,archive):
    info=zipfile.ZipInfo("AstraDesktop/"+name,date_time=STAMP)
    info.create_system=3
    info.external_attr=0o100644<<16
    info.compress_type=zipfile.ZIP_DEFLATED
    archive.writestr(info,data,compresslevel=9)


def build(root,destination):
    selected,groups=collect(root)
    manifest={"format":"astra-desktop-source-v1","python_minimum":"3.10",
              "runtime":"Python and Tkinter required; no model or runtime bundled.",
              "groups":groups,
              "files":[{"path":name,"bytes":len(data),"sha256":hashlib.sha256(data).hexdigest()}
                       for name,data in sorted(selected.items())]}
    # Create exclusively, and remove partial output on write failure.
    target=Path(destination)
    with target.open("xb") as output:
        try:
            with zipfile.ZipFile(output,"w") as archive:
                for name,data in sorted(selected.items()):
                    entry(name,data,archive)
                entry("MANIFEST.json",(json.dumps(manifest,sort_keys=True,indent=2)+"\n").encode("utf-8"),archive)
        except BaseException:
            output.close()
            target.unlink()
            raise
    return manifest


def verify(path):
    """Validate manifest and contents without extracting any ZIP entries."""
    allowed=set(CORE)|set(OPTIONAL)|{name for group in GROUPS.values() for name in group}
    prefix="AstraDesktop/"
    with zipfile.ZipFile(path,"r") as archive:
        infos=archive.infolist()
        names=[i.filename for i in infos]
        if len(names)!=len(set(names)) or len(names)>len(allowed)+1:
            raise ValueError("Duplicate or excessive archive entries.")
        manifest_name=prefix+"MANIFEST.json"
        if manifest_name not in names or any(
                not i.filename.startswith(prefix) or
                (i.filename!=manifest_name and i.filename[len(prefix):] not in allowed) or
                i.file_size>MAX_FILE or i.is_dir() or
                (i.create_system==3 and stat.S_IFMT(i.external_attr>>16) not in (0,stat.S_IFREG))
                for i in infos):
            raise ValueError("Unexpected archive contents.")
        if sum(i.file_size for i in infos)>MAX_TOTAL+MAX_FILE:
            raise ValueError("Archive exceeds uncompressed size budget.")
        manifest=json.loads(archive.read(manifest_name).decode("utf-8"))
        if type(manifest) is not dict or manifest.get("format")!="astra-desktop-source-v1" or not isinstance(manifest.get("files"),list):
            raise ValueError("Unknown manifest format.")
        rows=manifest["files"]
        if any(type(row) is not dict or set(row)!={"path","bytes","sha256"} or
               not isinstance(row["path"],str) or type(row["bytes"]) is not int or
               not isinstance(row["sha256"],str) for row in rows):
            raise ValueError("Invalid manifest file records.")
        paths=[row["path"] for row in rows]
        if len(paths)!=len(set(paths)) or set(paths)!=set(n[len(prefix):] for n in names if n!=manifest_name):
            raise ValueError("Manifest entries do not match archive.")
        if not set(CORE)<=set(paths):
            raise ValueError("Missing core files.")
        groups=[]
        for group,files in GROUPS.items():
            present=set(files)&set(paths)
            if present:
                if present!=set(files):
                    raise ValueError("Incomplete archive group.")
                groups.append(group)
        if groups!=manifest.get("groups") or ("professional-report" in groups and "analytics" not in groups):
            raise ValueError("Manifest groups inconsistent.")
        for row in rows:
            data=archive.read(prefix+row["path"])
            if len(data)!=row["bytes"] or hashlib.sha256(data).hexdigest()!=row["sha256"]:
                raise ValueError("File digest mismatch.")
    return manifest


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("operation",choices=("build","verify"))
    p.add_argument("archive")
    args=p.parse_args(argv)
    try:
        result=build(ROOT,args.archive) if args.operation=="build" else verify(args.archive)
        print(json.dumps(result,indent=2))
        return 0
    except (OSError,ValueError,TypeError,KeyError,RuntimeError,zipfile.BadZipFile):
        print("Distribution operation failed; check source completeness and output path.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
