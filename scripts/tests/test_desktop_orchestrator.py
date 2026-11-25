"""End-to-end orchestration regression tests with no real model downloads."""
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
             "astra_local_finance", "astra_orchestrator", "astra_local_answer", "astra_desktop"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
O = sys.modules["astra_orchestrator"]
A = sys.modules["astra_local_answer"]
D = sys.modules["astra_desktop"]
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]
P = sys.modules["astra_resource_plan"]


class OrchestratorTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.path = self.root / "ledger.sqlite"
        self.ledger = E.Ledger(self.path)
        self.addCleanup(self.ledger.close)
        self.record_id = self.ledger.add("Bond duration", "Modified duration measures price sensitivity.",
                                         "Reviewed textbook", reviewed=True)
        self.hybrid = H.HybridEvidence(self.ledger)
        self.request = {"kind": "answer", "question": "Explain bond duration"}

    def reply(self):
        return json.dumps({"answer":"A measure of price sensitivity.","citations":[self.record_id]})

    def test_calculation_and_retrieval_do_not_call_models(self):
        runner = lambda *args: self.fail("Unexpected model call")
        service = O.Orchestrator(self.ledger,self.hybrid,runner,runner)
        result = service.execute({"kind":"finance","calculation":{"operation":"present_value",
                                  "inputs":{"amount":"121","rate":"0.1","periods":2}}})
        self.assertEqual("100",result["finance"]["result"]["decimal_approximation"])
        result = service.execute({"kind":"retrieve","question":"bond duration"})
        self.assertEqual(self.record_id,result["retrieval"]["results"][0]["id"])

    def test_requires_model_consent_and_abstains_without_evidence(self):
        service = O.Orchestrator(self.ledger,self.hybrid,lambda *args: self.fail("Unexpected model call"))
        with self.assertRaises(ValueError):
            service.execute(self.request)
        result = service.execute({"kind":"answer","question":"unrelated astronomy"},approve_models=True)
        self.assertEqual("insufficient_evidence",result["status"])
        self.assertIsNone(result["answer"])

    def test_primary_and_critic_outputs_are_separate_unverified_drafts(self):
        calls = []
        def runner(role,prompt):
            calls.append((role,prompt))
            if role == "primary":
                return self.reply()
            return json.dumps({"assessment":"concerns","concerns":["Does not explain yield changes."]})
        result = O.Orchestrator(self.ledger,self.hybrid,runner).execute(
            dict(self.request,review=True),approve_models=True)
        self.assertEqual(["primary","critic"],[x[0] for x in calls])
        self.assertIn("untrusted data",calls[0][1])
        self.assertIn("Reviewed textbook",calls[0][1])
        self.assertEqual("unverified_evidence_grounded_draft",result["status"])
        self.assertEqual("concerns",result["review"]["assessment"])
        self.assertEqual(self.record_id,result["citations"][0]["id"])
        self.assertIn("not independently verified",result["scope"])

    def test_invalid_citations_and_extra_fields_fail_closed(self):
        for response in ({"answer":"answer","citations":[999]},
                         {"answer":"answer","citations":[]},
                         {"answer":"answer","citations":[True]},
                         {"answer":"answer","citations":[self.record_id,self.record_id]},
                         {"answer":"answer","citations":[self.record_id],"action":"delete"}):
            service = O.Orchestrator(self.ledger,self.hybrid,lambda *args:json.dumps(response))
            result = service.execute(self.request,approve_models=True)
            self.assertEqual("model_unavailable_or_invalid",result["status"])
            self.assertIsNone(result["answer"])
        self.assertEqual(1,len(self.ledger.search("duration")))

    def test_changed_evidence_discards_generated_answer(self):
        def runner(role,prompt):
            self.ledger.add("Bond duration","New reviewed correction.","Reviewed textbook",
                            reviewed=True,supersedes=self.record_id)
            return self.reply()
        result = O.Orchestrator(self.ledger,self.hybrid,runner).execute(self.request,approve_models=True)
        self.assertEqual("evidence_changed_retry",result["status"])
        self.assertIsNone(result["answer"])

    def test_critic_timeout_does_not_certify_primary(self):
        def runner(role,prompt):
            if role == "critic":
                raise TimeoutError("private runtime path")
            return self.reply()
        result = O.Orchestrator(self.ledger,self.hybrid,runner).execute(
            dict(self.request,review=True),approve_models=True)
        self.assertEqual("unavailable",result["review"]["assessment"])
        self.assertEqual("unverified_evidence_grounded_draft",result["status"])
        self.assertNotIn("private runtime path",str(result))

    def test_prompt_budget_skips_whole_oversized_record(self):
        large = self.ledger.add("Duration","界"*4000,"Reviewed source",reviewed=True)
        records = self.hybrid.search("duration")["results"]
        chosen,prompt = O.select_evidence("duration",records)
        self.assertNotIn(large,[row["id"] for row in chosen])
        self.assertIn(self.record_id,[row["id"] for row in chosen])
        self.assertLessEqual(len(prompt.encode()),O.MAX_PROMPT_BYTES)

    def test_cli_lexical_and_calculation_routes(self):
        result = D.execute_request({"kind":"retrieve","question":"duration"},ledger_path=self.path)
        self.assertEqual(self.record_id,result["retrieval"]["results"][0]["id"])
        result = D.execute_request({"kind":"finance","calculation":{"operation":"present_value",
                                   "inputs":{"amount":"121","rate":"0.1","periods":2}}})
        self.assertEqual("100",result["finance"]["result"]["decimal_approximation"])
        with self.assertRaises(ValueError):
            D.execute_request(self.request,ledger_path=self.path)
        with self.assertRaises(ValueError):
            D.execute_request({"kind":"retrieve","question":"duration"},ledger_path=self.root/"missing.sqlite")
        self.assertFalse((self.root/"missing.sqlite").exists())

    def test_native_runner_rechecks_resources_and_cleans_prompt(self):
        binary = self.root/"llama-cli.exe"
        primary = self.root/"primary.gguf"
        binary.write_bytes(b"fixture")
        primary.write_bytes(b"GGUF"+b"x"*1024)
        runner = A.LocalAnswerRunner(binary,primary,approved=True)
        with patch.object(A,"probe",return_value=P.Resources(P.GIB,4,P.GIB)), \
             patch.object(A.subprocess,"run") as run:
            with self.assertRaises(RuntimeError):
                runner("primary","private question")
            run.assert_not_called()
        paths = []
        def execute(args,**kwargs):
            path = Path(args[args.index("-f")+1])
            paths.append(path)
            self.assertEqual("private question",path.read_text())
            self.assertNotIn("private question"," ".join(args))
            self.assertEqual("0",args[args.index("-ngl")+1])
            self.assertIn("--offline",args)
            self.assertFalse(kwargs["shell"])
            return subprocess.CompletedProcess(args,0,self.reply().encode())
        with patch.object(A,"probe",return_value=P.Resources(6*P.GIB,4,P.GIB)), \
             patch.object(A.subprocess,"run",side_effect=execute):
            self.assertEqual(self.reply(),runner("primary","private question"))
        self.assertTrue(paths)
        self.assertFalse(paths[0].exists())
        primary.write_bytes(b"GGUF"+b"x"*1025)
        with self.assertRaises(RuntimeError):
            runner("primary","private question")


if __name__ == "__main__":
    unittest.main()
