"""Tests for adaptive timing budgets and native timeout enforcement."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory",
             "astra_hybrid_evidence", "astra_quantitative_verify", "astra_verified_finance",
             "astra_local_finance", "astra_orchestrator", "astra_local_answer",
             "astra_compute_budget", "astra_adaptive_answer"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
B = sys.modules["astra_compute_budget"]
A = sys.modules["astra_local_answer"]
P = sys.modules["astra_resource_plan"]
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]
C = sys.modules["astra_adaptive_answer"]


class Clock:
    def __init__(self):
        self.value = 0

    def __call__(self):
        return self.value


class FakeRunner:
    timeout = 180

    def __init__(self, clock, duration=10, fail=False):
        self.clock, self.duration, self.fail = clock, duration, fail
        self.calls = []

    def __call__(self, role, prompt, *, timeout_seconds):
        self.calls.append((role, timeout_seconds))
        self.clock.value += self.duration
        if self.fail:
            raise RuntimeError("fixture failure")
        return "fixture"


class ComputeBudgetTests(unittest.TestCase):
    def test_consent_and_policy_validation(self):
        for mode, seconds in (("unknown",10),("fast",0),("deep",901),("balanced",True)):
            with self.assertRaises(ValueError):
                B.Policy(mode,seconds)
        clock = Clock()
        runner = FakeRunner(clock)
        with self.assertRaises(ValueError):
            B.BudgetedRunner(runner,B.Policy())
        self.assertEqual([],runner.calls)

    def test_fast_mode_never_runs_second_call(self):
        clock = Clock()
        runner = FakeRunner(clock)
        wrapped = B.BudgetedRunner(runner,B.Policy("fast",60),clock=clock,approved=True)
        self.assertEqual("fixture",wrapped("primary","question"))
        with self.assertRaises(B.BudgetExhausted):
            wrapped("critic","review")
        self.assertEqual([("primary",60)],runner.calls)
        self.assertEqual(1,wrapped.report()["model_calls"])

    def test_balanced_mode_skips_expensive_critic(self):
        clock = Clock()
        runner = FakeRunner(clock,duration=25)
        wrapped = B.BudgetedRunner(runner,B.Policy("balanced",50),clock=clock,approved=True)
        wrapped("primary","question")
        with self.assertRaises(B.BudgetExhausted):
            wrapped("critic","review")
        self.assertEqual([("primary",50)],runner.calls)

    def test_measured_profile_allows_affordable_critic(self):
        clock = Clock()
        runner = FakeRunner(clock,duration=10)
        profile = B.LatencyProfile()
        profile.record("critic",8)
        wrapped = B.BudgetedRunner(runner,B.Policy("balanced",25),profile,clock=clock,approved=True)
        wrapped("primary","question")
        wrapped("critic","review")
        self.assertEqual([("primary",25),("critic",15)],runner.calls)
        self.assertEqual(2,wrapped.report()["latency_profile"]["critic"]["samples"])

    def test_late_answer_is_discarded_and_recorded(self):
        clock = Clock()
        runner = FakeRunner(clock,duration=11)
        wrapped = B.BudgetedRunner(runner,B.Policy("fast",10),clock=clock,approved=True)
        with self.assertRaises(B.BudgetExhausted):
            wrapped("primary","question")
        self.assertEqual("deadline_exceeded",wrapped.report()["events"][0]["status"])

    def test_failures_consume_call_and_latency_budget(self):
        clock = Clock()
        runner = FakeRunner(clock,duration=20,fail=True)
        wrapped = B.BudgetedRunner(runner,B.Policy("fast",60),clock=clock,approved=True)
        with self.assertRaises(RuntimeError):
            wrapped("primary","question")
        self.assertEqual(1,wrapped.report()["model_calls"])
        self.assertEqual("failed",wrapped.report()["events"][0]["status"])
        self.assertEqual(25,wrapped.profile.estimate("primary"))

    def test_profile_is_bounded_and_does_not_store_content(self):
        profile = B.LatencyProfile()
        for duration in range(1,30):
            profile.record("primary",duration)
        self.assertEqual(B.MAX_SAMPLES,profile.summary()["primary"]["samples"])
        self.assertEqual(35,profile.estimate("primary"))
        with self.assertRaises(ValueError):
            profile.record("critic",float("nan"))

    def test_deep_mode_hard_call_cap(self):
        clock = Clock()
        runner = FakeRunner(clock,duration=5)
        wrapped = B.BudgetedRunner(runner,B.Policy("deep",180),clock=clock,approved=True)
        for role in ("primary","critic","primary","critic"):
            wrapped(role,"question")
        with self.assertRaises(B.BudgetExhausted):
            wrapped("primary","question")
        self.assertEqual(4,len(runner.calls))

    def test_native_timeout_can_only_be_reduced(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            binary = root/"llama-cli.exe"
            binary.write_bytes(b"fixture")
            model = root/"primary.gguf"
            model.write_bytes(b"GGUF"+b"x"*1024)
            runner = A.LocalAnswerRunner(binary,model,timeout=60,approved=True)
            with patch.object(A,"probe",return_value=P.Resources(6*P.GIB,4,P.GIB)), \
                 patch.object(A.subprocess,"run",return_value=subprocess.CompletedProcess([],0,b"answer")) as run:
                self.assertEqual("answer",runner("primary","question",timeout_seconds=7))
                self.assertEqual(7,run.call_args.kwargs["timeout"])
                for bad in (0,61,True):
                    with self.assertRaises(ValueError):
                        runner("primary","question",timeout_seconds=bad)
                self.assertEqual(1,run.call_count)

    def test_grounded_fast_answer_and_insufficient_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            with E.Ledger(Path(tmp)/"evidence.sqlite") as ledger:
                identifier = ledger.add("Duration","Price sensitivity.","Reviewed source",reviewed=True)
                class Runner:
                    timeout = 60
                    def __init__(self):
                        self.calls = []
                    def __call__(self,role,prompt,*,timeout_seconds):
                        self.calls.append(role)
                        return json.dumps({"answer":"Tentative explanation","citations":[identifier]})
                runner = Runner()
                profile = B.LatencyProfile()
                result = C.answer("duration",ledger,H.HybridEvidence(ledger),runner,
                                  B.Policy("fast",60),profile,approved=True)
                self.assertEqual("unverified_evidence_grounded_draft",result["status"])
                self.assertEqual(["primary"],runner.calls)
                result = C.answer("astronomy",ledger,H.HybridEvidence(ledger),runner,
                                  B.Policy("fast",60),profile,approved=True)
                self.assertEqual("insufficient_evidence",result["status"])
                self.assertEqual(0,result["compute"]["model_calls"])


if __name__ == "__main__":
    unittest.main()
