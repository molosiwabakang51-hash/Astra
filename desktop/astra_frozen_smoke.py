"""Headless functional smoke test for the bundled desktop runtime."""
import importlib
import importlib.util
from pathlib import Path
import sys
import tempfile

from astra_verified_finance import adjudicate
from astra_finance_session import FinanceSession
from astra_evidence import Ledger


def smoke_test():
    checks={}
    def check(name,fn):
        try:
            checks[name]=bool(fn())
        except Exception:
            checks[name]=False
    check("exact_finance",lambda:adjudicate({
        "operation":"present_value","inputs":{"amount":"121","rate":"0.1","periods":2}
    })["result"]["numerator"]=="100")
    def reviewed_memory():
        with tempfile.TemporaryDirectory() as folder:
            with Ledger(Path(folder)/"smoke.sqlite") as ledger:
                ledger.add("Synthetic bond","A bond pays a coupon.","Smoke fixture",reviewed=True)
                return len(ledger.search("bond"))==1
    check("reviewed_evidence",reviewed_memory)
    check("finance_session_import",lambda: FinanceSession() is not None)
    check("assistant_gui_import",lambda:importlib.import_module("astra_desktop_gui").AstraApp is not None)
    check("tkinter_import",lambda:importlib.import_module("tkinter").TkVersion>=8.6)
    if importlib.util.find_spec("astra_analytics") is not None:
        from astra_analytics import available,run_analysis,from_files,export_content
        check("analytics_gui_import",lambda:importlib.import_module("astra_analytics_gui").AnalyticsWindow is not None)
        def portfolio():
            envelope,md=run_analysis("portfolio",{
                "currency":"BWP","start":"2025-01-01","end":"2026-01-01",
                "basis":"unchanged_holdings_no_flows_income_excluded",
                "holdings":[{"symbol":"A","opening_value":"100","closing_value":"110","cash_income":"5"}],
            })
            return envelope["result"]["total_return"]["decimal_approximation"]=="0.15" and "Total return" in md
        check("portfolio_analytics",portfolio)
        from astra_desktop_distribution import GROUPS
        modes=available()
        for mode,files in GROUPS.items():
            if mode not in modes:
                continue
            path=Path(__file__).resolve().parent/files[2]
            ready=modes[mode]
            if not ready and not path.is_file():
                continue
            def example(m=mode,p=path,installed=ready):
                if not installed or not p.is_file():
                    return False
                envelope,md=from_files(m,p)
                if importlib.util.find_spec("astra_professional_report") is not None:
                    html=export_content(envelope,md,"html")
                    return "<table>" in html and "Canonical request SHA-256" in html
                return bool(envelope["result"]) and bool(md.strip())
            check("example_"+mode,example)
        if importlib.util.find_spec("astra_professional_report") is not None:
            from astra_professional_report import render_html
            check("professional_renderer",lambda:"<table>" in render_html(
                "# Synthetic\n\n| A | B |\n|---|---|\n| 1 | 2 |","unknown",{}))
    return {"passed":all(checks.values()),"checks":checks,
            "frozen":bool(getattr(sys,"frozen",False)),
            "scope":"Synthetic headless checks only; no model loading, GUI display, or physical hardware validation."}
