"""Contextual grounding tests: mock local generation, real SQLite evidence."""
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory",
             "astra_hybrid_evidence", "astra_quantitative_verify", "astra_verified_finance",
             "astra_local_finance", "astra_orchestrator", "astra_local_answer",
             "astra_local_embedding", "astra_knowledge_session", "astra_knowledge_chat"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]
O = sys.modules["astra_orchestrator"]
K = sys.modules["astra_knowledge_session"]
C = sys.modules["astra_knowledge_chat"]


class KnowledgeContextTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.ledger = E.Ledger(Path(tmp.name) / "knowledge.sqlite")
        self.addCleanup(self.ledger.close)
        self.id = self.ledger.add("Bond duration", "Duration measures price sensitivity.",
                                  "Reviewed textbook", reviewed=True)
        self.other = self.ledger.add("Planetary orbits", "Planets revolve around stars.",
                                     "Reviewed reference", reviewed=True)
        self.calls = []

        def runner(role, prompt):
            data = json.loads(prompt.split("\n", 1)[1])
            self.calls.append((role, data))
            if role == "critic":
                return json.dumps({"assessment": "no_issue_found", "concerns": []})
            return json.dumps({"answer": "Tentative explanation.",
                               "citations": [data["evidence"][0]["id"]]})

        self.orchestrator = O.Orchestrator(self.ledger, H.HybridEvidence(self.ledger), runner)
        self.session = K.KnowledgeSession(self.orchestrator)

    def ask(self, text, **kwargs):
        return self.session.execute(text, approve_models=True, **kwargs)

    def test_followup_reuses_context_and_current_evidence(self):
        first = self.ask("Explain bond duration")
        self.assertEqual("unverified_evidence_grounded_draft", first["status"])
        result = self.ask("Give an example.")
        self.assertTrue(result["contextual_followup"])
        self.assertEqual("Explain bond duration", self.calls[-1][1]["conversation"]["root_question"])
        self.assertEqual(first["answer"], self.calls[-1][1]["conversation"]["previous_draft"])
        self.assertIn(self.id, [row["id"] for row in self.calls[-1][1]["evidence"]])
        self.assertNotIn("evidence_receipts", result)

    def test_new_topic_cannot_inherit_previous_draft(self):
        self.ask("Explain bond duration")
        result = self.ask("Explain planetary orbits")
        self.assertFalse(result["contextual_followup"])
        self.assertNotIn("conversation", self.calls[-1][1])
        self.assertEqual(self.other, result["citations"][0]["id"])

    def test_superseded_evidence_blocks_followup_before_model_call(self):
        self.ask("Explain bond duration")
        self.ledger.add("Bond duration", "Corrected statement.", "Reviewed textbook",
                        reviewed=True, supersedes=self.id)
        count = len(self.calls)
        result = self.ask("Why?")
        self.assertEqual("evidence_changed_retry", result["status"])
        self.assertIsNone(result["answer"])
        self.assertEqual(count, len(self.calls))
        self.assertEqual("clarification_required", self.ask("Why?")["status"])

    def test_uncited_evidence_also_invalidates_context(self):
        uncited = self.ledger.add("Bond duration", "Another reviewed description.",
                                  "Other source", reviewed=True)
        first = self.ask("Explain bond duration")
        self.assertEqual([self.id], [row["id"] for row in first["citations"]])
        self.ledger.add("Bond duration", "Updated second description.", "Other source",
                        reviewed=True, supersedes=uncited)
        self.assertEqual("evidence_changed_retry", self.ask("Explain that")["status"])

    def test_stale_evidence_during_review_prevents_release(self):
        original = self.orchestrator.runner

        def runner(role, prompt):
            reply = original(role, prompt)
            if role == "critic":
                self.ledger.clear(confirmed=True)
            return reply

        self.orchestrator.runner = runner
        self.assertEqual("evidence_changed_retry",
                         self.ask("Explain bond duration", review=True)["status"])

    def test_model_failure_clears_anchor(self):
        self.ask("Explain bond duration")
        self.orchestrator.runner = lambda *args: "bad output"
        self.assertEqual("model_unavailable_or_invalid", self.ask("Why?")["status"])
        self.assertEqual("clarification_required", self.ask("Why?")["status"])

    def test_consent_reset_and_ambiguous_reference(self):
        with self.assertRaises(ValueError):
            self.session.execute("Explain bonds")
        self.assertEqual("clarification_required", self.ask("Why?")["status"])
        self.assertEqual([], self.calls)
        self.ask("Explain bond duration")
        self.assertEqual("clarification_required", self.ask("What about that?")["status"])
        self.assertEqual("knowledge_context_cleared", self.ask("reset conversation")["status"])
        self.assertEqual("clarification_required", self.ask("Simplify")["status"])

    def test_critic_receives_prior_context_without_certification(self):
        self.ask("Explain bond duration")
        result = self.ask("Simplify", review=True)
        self.assertEqual("critic", self.calls[-1][0])
        self.assertIn("conversation", self.calls[-1][1])
        self.assertEqual("no_issue_found", result["review"]["assessment"])
        self.assertEqual("unverified_evidence_grounded_draft", result["status"])

    def test_interactive_session_clears_on_exit_and_bounds_input(self):
        output, errors = io.StringIO(), io.StringIO()
        source = io.BytesIO(b"Explain bond duration\nWhy?\nexit\n")
        self.assertEqual(0, C.interact(self.session, source, output, errors,
                                      approve_models=True))
        rows = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertEqual([False, True], [row["contextual_followup"] for row in rows])
        self.assertEqual("", errors.getvalue())
        self.assertEqual("clarification_required", self.ask("Why?")["status"])
        self.ask("Explain bond duration")
        self.assertEqual(1, C.interact(self.session, io.BytesIO(b"x"*4097),
                                      io.StringIO(), io.StringIO(), approve_models=True))
        self.assertEqual("clarification_required", self.ask("Why?")["status"])


if __name__ == "__main__":
    unittest.main()
