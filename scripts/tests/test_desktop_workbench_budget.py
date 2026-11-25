"""Contextual request budgets and explicitly exported timing receipts."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_workbench as W


class Clock:
    value=0
    def __call__(self):
        return self.value


class WorkbenchBudgetTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root=Path(tmp.name)
        self.wb=W.Workbench()
        self.addCleanup(self.wb.close)
        self.wb.connect(self.root/"evidence.sqlite",create=True)
        identifier=self.wb.add_evidence("Duration","Measures price sensitivity.","Reviewed source",reviewed=True)["id"]
        self.clock=Clock()
        self.calls=[]
        calls,clock=self.calls,self.clock
        class Runner:
            timeout=60
            models={"primary":"synthetic","critic":"synthetic"}
            def __call__(self,role,prompt,*,timeout_seconds):
                calls.append((role,timeout_seconds))
                clock.value+=10
                if role=="critic":
                    return json.dumps({"assessment":"no_issue_found","concerns":[]})
                return json.dumps({"answer":"Private synthetic draft","citations":[identifier]})
        self.runner=Runner()
        with patch.object(W,"LocalAnswerRunner",return_value=self.runner):
            self.wb.configure_models("fixture","primary","critic",approved=True)
        original=W.BudgetedRunner
        self.patcher=patch.object(W,"BudgetedRunner",side_effect=lambda *a,**k:original(*a,clock=self.clock,**k))
        self.patcher.start()
        self.addCleanup(self.patcher.stop)

    def ask(self,question="Explain duration",budget=60,review=True):
        return self.wb.ask_knowledge(question,generate=True,review=review,budget_seconds=budget)

    def test_short_budget_skips_optional_critic_preserves_followup(self):
        first=self.ask(budget=25)
        self.assertEqual("unverified_evidence_grounded_draft",first["status"])
        self.assertEqual("unavailable",first["review"]["assessment"])
        self.assertEqual(1,first["compute"]["model_calls"])
        self.assertIn("Optional review skipped",W.format_result(first))
        self.assertIs(self.runner,self.wb.knowledge.orchestrator.runner)
        followup=self.ask("Why?",budget=60)
        self.assertTrue(followup["contextual_followup"])
        self.assertEqual("no_issue_found",followup["review"]["assessment"])
        self.assertEqual(2,followup["compute"]["model_calls"])

    def test_known_slow_critic_skips_even_with_long_request_budget(self):
        self.wb.latency.record("critic",100)
        result=self.ask(budget=180)
        self.assertEqual("unavailable",result["review"]["assessment"])
        self.assertEqual([("primary",60)],self.calls)
        self.assertEqual("budget_skipped",result["compute"]["events"][-1]["status"])

    def test_no_review_never_calls_critic(self):
        result=self.ask(review=False)
        self.assertEqual([("primary",60)],self.calls)
        self.assertEqual("fast",result["compute"]["mode"])
        self.assertEqual("not_requested",result["review"]["assessment"])

    def test_approved_export_is_bounded_and_contains_no_content(self):
        path=self.root/"timing.json"
        for _ in range(26):
            self.ask(review=False)
        self.assertEqual(24,len(self.wb.compute_history))
        with self.assertRaises(ValueError):
            self.wb.export_compute_history(path)
        self.assertFalse(path.exists())
        result=self.wb.export_compute_history(path,approved=True)
        raw=path.read_text(encoding="utf-8")
        self.assertEqual(24,result["count"])
        self.assertEqual(24,len(json.loads(raw)["records"]))
        for private in ("Private synthetic draft","Reviewed source","Explain duration",str(self.root)):
            self.assertNotIn(private,raw)
        with self.assertRaises(FileExistsError):
            self.wb.export_compute_history(path,approved=True)
        self.wb.disable_models()
        self.assertEqual(0,len(self.wb.compute_history))
        self.assertEqual(0,self.wb.latency.summary()["primary"]["samples"])

    def test_late_primary_never_publishes_draft(self):
        result=self.ask(budget=5,review=False)
        self.assertEqual("model_unavailable_or_invalid",result["status"])
        self.assertEqual("deadline_exceeded",result["compute"]["events"][0]["status"])
        self.assertIs(self.runner,self.wb.knowledge.orchestrator.runner)

if __name__=="__main__":
    unittest.main()
