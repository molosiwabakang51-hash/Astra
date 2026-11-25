"""Bounded decomposition tests with exact arithmetic and real local SQLite."""
import contextlib
from copy import deepcopy
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory",
             "astra_hybrid_evidence", "astra_quantitative_verify", "astra_verified_finance",
             "astra_local_finance", "astra_orchestrator", "astra_local_answer",
             "astra_task_plan", "astra_plan_cli"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]
O = sys.modules["astra_orchestrator"]
P = sys.modules["astra_task_plan"]
C = sys.modules["astra_plan_cli"]


def sample_plan():
    return {"objective": "Compare the present values at 8% and 10% and explain duration.",
            "steps": [
                {"id": "base", "kind": "finance", "depends_on": [],
                 "calculation": {"operation": "present_value",
                                 "inputs": {"amount": "10000", "rate": "0.08", "periods": 5}}},
                {"id": "stress", "kind": "finance", "depends_on": ["base"],
                 "calculation": {"operation": "present_value",
                                 "inputs": {"amount": "10000", "rate": "0.10", "periods": 5}}},
                {"id": "reference", "kind": "retrieve", "depends_on": [],
                 "question": "bond duration"}
            ]}


class TaskPlanTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path = Path(tmp.name)
        self.ledger = E.Ledger(self.path / "evidence.sqlite")
        self.addCleanup(self.ledger.close)
        self.record = self.ledger.add("Bond duration", "Duration measures price sensitivity.",
                                      "Reviewed textbook", reviewed=True)
        self.orchestrator = O.Orchestrator(self.ledger, H.HybridEvidence(self.ledger))
        self.plan = sample_plan()

    def execute(self, plan=None, **kwargs):
        plan = self.plan if plan is None else plan
        return P.execute_plan(plan, self.orchestrator,
                              approved_digest=P.approval_digest(plan), **kwargs)

    def test_read_only_plan_executes_exact_arithmetic_without_models(self):
        self.orchestrator.runner = lambda *args: self.fail("Unexpected generation")
        result = self.execute()
        self.assertEqual("completed", result["status"])
        self.assertEqual(["base", "stress", "reference"], [x["id"] for x in result["steps"]])
        self.assertEqual(self.record, result["steps"][2]["records"][0]["id"])
        self.assertEqual("deterministic_exact_arithmetic", result["steps"][0]["finance"]["authority"])
        self.assertEqual("not_requested", result["synthesis"]["status"])

    def test_digest_binds_assumptions_and_order_before_execution(self):
        digest = P.approval_digest(self.plan)
        changed = deepcopy(self.plan)
        changed["steps"][0]["calculation"]["inputs"]["rate"] = "0.09"
        with patch.object(self.orchestrator, "execute") as execute:
            for plan, approval in ((changed,digest),(self.plan,None),(self.plan,"0"*64)):
                with self.assertRaises(ValueError):
                    P.execute_plan(plan,self.orchestrator,approved_digest=approval)
            execute.assert_not_called()

    def test_invalid_actions_dependencies_and_numeric_references_rejected(self):
        bad_plans = []
        for field, value in (("kind","shell"),("depends_on",["stress"]),("id","../x")):
            bad = sample_plan()
            bad["steps"][0][field] = value
            bad_plans.append(bad)
        bad = sample_plan()
        bad["steps"][1]["id"] = "base"
        bad_plans.append(bad)
        bad = sample_plan()
        bad["steps"][1]["calculation"]["inputs"]["amount"] = "$base.result"
        bad_plans.append(bad)
        bad = sample_plan()
        bad["steps"][1]["calculation"]["candidates"] = {"primary":"1"}
        bad_plans.append(bad)
        for plan in bad_plans:
            with self.assertRaises(ValueError):
                P.validate_plan(plan)

    def test_dependency_failure_skips_only_dependent_steps(self):
        self.plan["steps"] = [
            {"id":"missing","kind":"retrieve","depends_on":[],"question":"unrelated astronomy"},
            dict(self.plan["steps"][0],depends_on=["missing"]),
            self.plan["steps"][2]
        ]
        self.orchestrator.runner = lambda *args: self.fail("Partial plan must not synthesize")
        result = self.execute(synthesize=True,approve_models=True)
        self.assertEqual("partial",result["status"])
        self.assertEqual(["insufficient_evidence","skipped_dependency","completed"],
                         [x["status"] for x in result["steps"]])
        self.assertEqual("skipped_incomplete_plan",result["synthesis"]["status"])

    def test_model_proposal_is_not_execution_authority(self):
        calls = []
        def runner(role,prompt):
            calls.append(role)
            return json.dumps(self.plan)
        proposal = P.propose_plan(self.plan["objective"],runner,approved=True)
        self.assertEqual(["primary"],calls)
        self.assertEqual("awaiting_explicit_plan_approval",proposal["status"])
        self.assertEqual(P.approval_digest(self.plan),proposal["approval_digest"])
        with self.assertRaises(ValueError):
            P.propose_plan(self.plan["objective"],runner)
        with self.assertRaises(ValueError):
            P.propose_plan("different objective",runner,approved=True)

    def test_optional_synthesis_preserves_exact_outputs_and_marks_draft(self):
        def runner(role,prompt):
            payload = json.loads(prompt.split("\n",1)[1])
            self.assertEqual(2,len(payload["calculations"]))
            self.assertEqual(self.record,payload["evidence"][0]["id"])
            return json.dumps({"answer":"Tentative explanation","citations":[self.record]})
        self.orchestrator.runner = runner
        with self.assertRaises(ValueError):
            self.execute(synthesize=True)
        result = self.execute(synthesize=True,approve_models=True)
        self.assertEqual("unverified_draft",result["synthesis"]["status"])
        self.assertEqual("0.08",result["steps"][0]["calculation"]["inputs"]["rate"])
        self.assertEqual("deterministic_exact_arithmetic",result["steps"][1]["finance"]["authority"])

    def test_invalid_citations_do_not_replace_verified_steps(self):
        self.orchestrator.runner = lambda *args: json.dumps({"answer":"draft","citations":[999]})
        result = self.execute(synthesize=True,approve_models=True)
        self.assertEqual("model_unavailable_or_invalid",result["synthesis"]["status"])
        self.assertEqual("completed",result["status"])

    def test_stale_evidence_during_synthesis_discards_results(self):
        def runner(role,prompt):
            self.ledger.clear(confirmed=True)
            return json.dumps({"answer":"stale draft","citations":[self.record]})
        self.orchestrator.runner = runner
        result = self.execute(synthesize=True,approve_models=True)
        self.assertEqual("evidence_changed_retry",result["status"])
        self.assertEqual([],result["steps"])
        self.assertNotIn("stale draft",str(result))

    def test_stale_dependency_invalidates_descendants_before_release(self):
        plan = {"objective":"Review and calculate","steps":[
            self.plan["steps"][2],
            dict(self.plan["steps"][0],depends_on=["reference"]),
            self.plan["steps"][1]]}
        original = self.orchestrator.execute
        def run(request):
            result = original(request)
            if request["kind"] == "finance":
                self.ledger.clear(confirmed=True)
            return result
        with patch.object(self.orchestrator,"execute",side_effect=run):
            result = self.execute(plan)
        self.assertEqual(["evidence_changed","skipped_dependency","skipped_dependency"],
                         [x["status"] for x in result["steps"]])

    def test_cli_inspection_and_digest_approved_execution(self):
        file = self.path/"plan.json"
        plan = sample_plan()
        plan["steps"] = plan["steps"][:2]
        file.write_text(json.dumps(plan),encoding="utf-8")
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            self.assertEqual(0,C.main(["inspect",str(file)]))
        approved = json.loads(stdout.getvalue())["approval_digest"]
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            self.assertEqual(0,C.main(["execute",str(file),"--approved-digest",approved]))
        self.assertEqual("completed",json.loads(stdout.getvalue())["status"])
        with contextlib.redirect_stderr(io.StringIO()),patch.object(C,"Orchestrator") as runner:
            self.assertEqual(1,C.main(["execute",str(file),"--approved-digest","0"*64]))
            runner.assert_not_called()


if __name__ == "__main__":
    unittest.main()
