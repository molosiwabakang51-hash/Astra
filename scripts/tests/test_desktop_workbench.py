"""Headless controller tests: real evidence ledger, mocked model inference."""
import importlib.util
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan","astra_evidence","astra_vector_memory",
             "astra_hybrid_evidence","astra_quantitative_verify","astra_verified_finance",
             "astra_local_finance","astra_orchestrator","astra_local_answer",
             "astra_natural_language","astra_finance_session","astra_knowledge_session",
             "astra_workbench"):
    spec=importlib.util.spec_from_file_location(name,ROOT/"desktop"/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
W=sys.modules["astra_workbench"]


class WorkbenchTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.db=self.root/"evidence.sqlite"
        self.workbench=W.Workbench()
        self.addCleanup(self.workbench.close)

    def connect(self):
        self.workbench.connect(self.db,create=True)
        return self.workbench.add_evidence("Bond duration","Measures price sensitivity.",
                                          "Reviewed textbook",reviewed=True)["id"]

    def test_finance_without_database_or_model(self):
        result=self.workbench.ask_finance("present value of P10,000 at 8% over five years")
        self.assertIn("P6,805.83",W.format_result(result))
        updated=self.workbench.ask_finance("What if 10%?")
        self.assertIn("P6,209.21",W.format_result(updated))
        result=self.workbench.ask_finance("Compare both results")
        self.assertIn("lower",W.format_result(result))
        self.workbench.reset("finance")
        self.assertEqual("clarification_required",self.workbench.ask_finance("What if 10%?")["status"])

    def test_explicit_creation_and_existing_database(self):
        self.connect()
        with self.assertRaises(ValueError):
            self.workbench.connect(self.db,create=True)
        self.workbench.close()
        self.workbench.connect(self.db)
        self.assertIn("Reviewed textbook",W.format_result(self.workbench.ask_knowledge("bond duration")))

    def test_unrelated_database_not_modified_or_opened(self):
        other=self.root/"unrelated.sqlite"
        with sqlite3.connect(other) as db:
            db.execute("CREATE TABLE unrelated(id INTEGER)")
        with self.assertRaises(ValueError):
            self.workbench.connect(other)
        with sqlite3.connect(other) as db:
            self.assertEqual(["unrelated"],[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")])

    def test_retrieval_default_and_reviewed_storage_requirements(self):
        with self.assertRaises(ValueError):
            self.workbench.ask_knowledge("duration")
        identifier=self.connect()
        result=self.workbench.ask_knowledge("duration")
        self.assertEqual("retrieved",result["status"])
        self.assertIn("Owner-reviewed; not independently verified",W.format_result(result))
        self.assertEqual(identifier,result["retrieval"]["results"][0]["id"])
        with self.assertRaises(ValueError):
            self.workbench.add_evidence("Duration","Unreviewed text","Source")
        with self.assertRaises(ValueError):
            self.workbench.ask_knowledge("duration",generate=True)
        with self.assertRaises(ValueError):
            self.workbench.ask_knowledge("duration",review=True)

    def test_optional_model_configuration_and_contextual_generation(self):
        identifier=self.connect()
        class Runner:
            models={"primary":"fixture","critic":"fixture"}
            def __call__(self,role,prompt):
                if role=="critic":
                    return '{"assessment":"no_issue_found","concerns":[]}'
                return json.dumps({"answer":"Tentative explanation.","citations":[identifier]})
        with patch.object(W,"LocalAnswerRunner",return_value=Runner()) as factory:
            self.workbench.configure_models("runtime","primary","critic",approved=True)
            self.assertTrue(factory.call_args.kwargs["approved"])
        first=self.workbench.ask_knowledge("Explain duration",generate=True)
        followup=self.workbench.ask_knowledge("Why?",generate=True,review=True)
        self.assertEqual("unverified_evidence_grounded_draft",first["status"])
        self.assertTrue(followup["contextual_followup"])
        self.assertIn("Unverified draft",W.format_result(followup))
        self.workbench.disable_models()
        with self.assertRaises(ValueError):
            self.workbench.ask_knowledge("Why?",generate=True)

    def test_correction_clears_context_and_old_evidence(self):
        identifier=self.connect()
        class Runner:
            models={"primary":"fixture"}
            def __call__(self,*args):
                return json.dumps({"answer":"Tentative.","citations":[identifier]})
        with patch.object(W,"LocalAnswerRunner",return_value=Runner()):
            self.workbench.configure_models("runtime","primary",approved=True)
        self.workbench.ask_knowledge("Explain duration",generate=True)
        new=self.workbench.add_evidence("Bond duration","Corrected explanation.","Reviewed textbook",
                                        reviewed=True,supersedes=identifier)["id"]
        self.assertEqual("clarification_required",self.workbench.ask_knowledge("Why?",generate=True)["status"])
        result=self.workbench.ask_knowledge("duration")
        self.assertEqual([new],[r["id"] for r in result["retrieval"]["results"]])

    def test_percentage_display_and_exact_fraction(self):
        result=self.workbench.ask_finance("weighted return: 30% at 10% and 70% at 5%")
        rendered=W.format_result(result)
        self.assertIn("6.5%",rendered)
        self.assertIn("13/200",rendered)


if __name__=="__main__":
    unittest.main()
