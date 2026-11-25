"""Headless controller for Astra's local desktop workbench.

Call from a single owner thread; the UI worker owns SQLite connections.
Finance requires no database or model. Knowledge generation and writes
require explicit approval; model output is never written automatically.
"""
from decimal import Decimal, localcontext
from contextlib import closing
from collections import deque
import json
from pathlib import Path

from astra_compute_budget import BudgetedRunner, LatencyProfile, Policy
from astra_evidence import Ledger
from astra_finance_session import FinanceSession
from astra_hybrid_evidence import HybridEvidence
from astra_knowledge_session import KnowledgeSession
from astra_local_answer import LocalAnswerRunner
from astra_orchestrator import Orchestrator
from astra_task_routing import classify


class Workbench:
    def __init__(self):
        self.finance = FinanceSession()
        self.ledger = None
        self.retrieval = None
        self.runner = None
        self.knowledge = None
        self.latency = LatencyProfile()
        self.compute_history = deque(maxlen=24)

    def _reset_knowledge(self):
        self.knowledge = (KnowledgeSession(Orchestrator(self.ledger, self.retrieval, self.runner))
                          if self.ledger is not None and self.runner is not None else None)

    def connect(self, path, *, create=False):
        if type(create) is not bool:
            raise ValueError("Invalid database creation setting.")
        path = Path(path).expanduser().resolve()
        if path.suffix.lower() not in (".sqlite", ".db", ".sqlite3"):
            raise ValueError("Select a dedicated SQLite evidence database.")
        if create and path.exists():
            raise ValueError("Database already exists; open it instead.")
        if not create and not path.is_file():
            raise ValueError("Select an existing evidence database.")
        # Validate existing schema without interpreting another Astra database
        # as evidence merely because it has the same schema version.
        if not create:
            import sqlite3
            with closing(sqlite3.connect(path.as_uri()+"?mode=ro", uri=True)) as inspection:
                tables = {row[0] for row in inspection.execute(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
                columns = {row[1] for row in inspection.execute("PRAGMA table_info(evidence)")}
                if tables != {"evidence"} or columns != {"id", "subject", "text", "source", "kind", "supersedes", "fingerprint", "active"}:
                    raise ValueError("Not a dedicated Astra evidence database.")
        if create:
            # Exclusive creation prevents accidentally opening a file created
            # between the initial existence check and database initialization.
            with path.open("xb"):
                pass
        candidate = Ledger(path)
        if self.ledger is not None:
            self.ledger.close()
        self.ledger = candidate
        self.retrieval = HybridEvidence(candidate)
        self._reset_knowledge()
        return {"status": "database_connected", "name": path.name}

    def configure_models(self, binary, primary, critic=None, *, approved=False, timeout=180, context_policy="fixed"):
        candidate = LocalAnswerRunner(binary, primary, critic, timeout=timeout, approved=approved,
                                      context_policy=context_policy)
        self.runner = candidate
        self.latency = LatencyProfile()
        self.compute_history.clear()
        self._reset_knowledge()
        return {"status": "models_configured", "critic_available": "critic" in candidate.models}

    def runtime_profile(self):
        profile = getattr(self.runner,"last_profile",{}) if self.runner is not None else {}
        keys = ("role","context_policy","context_tokens","generation_tokens","threads",
                "available_ram_mib","elapsed_seconds","outcome","output_bytes",
                "memory_samples","observed_peak_working_set_bytes")
        return {"status":"runtime_profile","profile":{k:profile[k] for k in keys if k in profile}}

    def disable_models(self):
        self.runner = None
        self.latency = LatencyProfile()
        self.compute_history.clear()
        self._reset_knowledge()
        return {"status": "models_disabled"}

    def ask_finance(self, question):
        return self.finance.execute(question)

    def ask_knowledge(self, question, *, generate=False, review=False, budget_seconds=None):
        if type(generate) is not bool or type(review) is not bool:
            raise ValueError("Invalid knowledge settings.")
        if self.ledger is None:
            raise ValueError("Open or create a reviewed evidence database first.")
        if not generate:
            if review:
                raise ValueError("Review requires approved generation.")
            if self.knowledge is not None:
                self.knowledge.reset()
            return Orchestrator(self.ledger,self.retrieval).execute(
                {"kind":"retrieve","question":question})
        if self.knowledge is None:
            raise ValueError("Configure explicitly approved local models first.")
        if review and "critic" not in self.runner.models:
            raise ValueError("Independent review requires a distinct critic model.")
        if budget_seconds is None:
            return self.knowledge.execute(question, approve_models=True, review=review)
        task = classify(question)
        policy = Policy("balanced" if review else "fast", budget_seconds)
        wrapped = BudgetedRunner(self.runner, policy, self.latency, approved=True)
        # Preserve the existing contextual session and evidence receipts; the
        # request-scoped wrapper is removed even if validation or generation fails.
        orchestrator = self.knowledge.orchestrator
        previous = orchestrator.runner
        orchestrator.runner = wrapped
        try:
            result = self.knowledge.execute(question, approve_models=True, review=review)
        finally:
            orchestrator.runner = previous
        receipt = wrapped.report()
        if receipt["model_calls"]:
            self.compute_history.append(receipt)
        return dict(result, compute=receipt, task_form=task)

    def export_compute_history(self, path, *, approved=False):
        if approved is not True:
            raise ValueError("Explicit local diagnostics export approval required.")
        if not self.compute_history:
            raise ValueError("No completed budgeted invocation records.")
        path = Path(path)
        if path.suffix.lower() != ".json":
            raise ValueError("Choose a new JSON diagnostics file.")
        report = {"format":"astra-compute-history-v1",
                  "scope":"Session-local request timing, not accuracy or authenticated configuration identity. No prompts or answers. Not imported for future routing.",
                  "records":list(self.compute_history)}
        with path.open("x", encoding="utf-8") as output:
            json.dump(report,output,indent=2,allow_nan=False)
            output.write("\n")
        return {"status":"compute_history_exported","count":len(self.compute_history)}

    def add_evidence(self, subject, text, source, *, reviewed=False, supersedes=None):
        if self.ledger is None:
            raise ValueError("Open or create a reviewed evidence database first.")
        identifier = self.ledger.add(subject,text,source,reviewed=reviewed,supersedes=supersedes)
        if self.knowledge is not None:
            self.knowledge.reset()
        return {"status":"evidence_saved","id":identifier}

    def reset(self, section):
        if section == "finance":
            self.finance.reset()
        elif section == "knowledge":
            if self.knowledge is not None:
                self.knowledge.reset()
        else:
            raise ValueError("Unknown context section.")
        return {"status":"context_cleared"}

    def close(self):
        self.finance.reset()
        if self.knowledge is not None:
            self.knowledge.reset()
        if self.ledger is not None:
            self.ledger.close()
        self.ledger = self.retrieval = self.runner = self.knowledge = None
        self.compute_history.clear()
        self.latency = LatencyProfile()


def numeric_display(value, currency=None):
    with localcontext() as ctx:
        ctx.prec = 40
        amount = Decimal(value["decimal_approximation"])
        if value.get("units") == "decimal_ratio":
            return f"{amount*100:,.4f}".rstrip("0").rstrip(".")+"%"
        return ("P" if currency=="BWP" else "") + f"{amount:,.2f}"


def _format_result(result):
    """Render human-readable output without promoting drafts to facts."""
    status = result["status"]
    if status == "calculated":
        scenario = result["scenario"]
        calculation = scenario["calculation"]
        value = scenario["finance"]["result"]
        lines = [numeric_display(value,scenario["currency"]),
                 "Operation: "+calculation["operation"].replace("_"," "),
                 "Assumptions:"]
        for key, item in calculation["inputs"].items():
            if key in ("rate",):
                item = numeric_display({"decimal_approximation":item,"units":"decimal_ratio"})
            elif key in ("weights","returns"):
                item = ", ".join(numeric_display({"decimal_approximation":x,"units":"decimal_ratio"}) for x in item)
            lines.append(f"  {key}: {item}")
        lines.append("Exact result: "+value["numerator"]+"/"+value["denominator"])
        lines.append(result["scope"])
        return "\n".join(lines)
    if status == "compared":
        before, after = result["before"],result["after"]
        return "\n".join([
            "Before: "+numeric_display(before["finance"]["result"],before["currency"]),
            "After: "+numeric_display(after["finance"]["result"],after["currency"]),
            "Change: "+numeric_display(result["absolute_change"],after["currency"]),
            "Relative change: "+(numeric_display(result["relative_change"]) if result["relative_change"] else "not applicable"),
            result["explanation"],result["scope"],
        ])
    if status == "retrieved":
        rows = result["retrieval"]["results"]
        return "\n\n".join(f"[{row['id']}] {row['subject']}\n{row['text']}\nSource: {row['source']}\nOwner-reviewed; not independently verified."
                            for row in rows) or "No matching reviewed evidence."
    if status == "unverified_evidence_grounded_draft":
        lines = ["Unverified draft",result["answer"],"Sources:"]
        for row in result["citations"]:
            lines.append(f"[{row['id']}] {row['subject']} — {row['source']}")
        review = result["review"]
        lines.append("Advisory review: "+review["assessment"].replace("_"," "))
        lines.extend(review["concerns"])
        lines.append(result["scope"])
        return "\n\n".join(lines)
    if status == "clarification_required":
        return result["clarification"]
    if status == "evidence_saved":
        return f"Reviewed record saved. Evidence ID: {result['id']}."
    if status == "database_connected":
        return "Connected: "+result["name"]
    if status == "runtime_profile":
        profile = result["profile"]
        if not profile:
            return "No completed native invocation profile available."
        lines = ["Latest native invocation (not an intelligence score)"]
        for key,value in profile.items():
            if key == "observed_peak_working_set_bytes" and value is not None:
                value = f"{value / (1024*1024):,.1f} MiB"
            lines.append(f"{key.replace('_',' ')}: {value if value is not None else 'unavailable'}")
        lines.append("Direct child only. Observed memory is not an enforced limit. No prompt or answer text retained.")
        return "\n".join(lines)
    if status == "models_configured":
        return "Local models configured (not yet inference-tested)."
    messages = {
        "outside_finance_session":"Use the Knowledge workspace for explanatory questions.",
        "insufficient_evidence":"Insufficient reviewed evidence for a draft.",
        "model_unavailable_or_invalid":"Local model unavailable or invalid response; no draft.",
        "evidence_changed_retry":"Supporting evidence changed. Start a new question.",
        "context_budget_exceeded":"Context budget exceeded. Start a narrower question.",
        "models_disabled":"Local model generation disabled.",
        "context_cleared":"Context cleared.",
        "finance_context_cleared":"Financial scenario cleared.",
        "knowledge_context_cleared":"Knowledge context cleared.",
    }
    return messages.get(status,"No usable result.")

def format_result(result):
    message = _format_result(result)
    if result["status"]=="compute_history_exported":
        return f"Saved {result['count']} content-free compute receipts to a new JSON file."
    if "compute" in result:
        receipt=result["compute"]
        message += "\n\nCompute: "+str(receipt["model_calls"])+"/"+str(receipt["max_model_calls"])+ " model calls"
        message += f" · {receipt['remaining_seconds']:.1f}s budget remaining"
        if any(event["status"]=="budget_skipped" for event in receipt["events"]):
            message += "\nOptional review skipped: estimated cost exceeds the remaining budget."
        if "task_form" in result:
            message += "\nTask form: "+result["task_form"]["task_form"].replace("_"," ")
        message += "\nTiming and task-form labels are not accuracy measurements."
    return message
