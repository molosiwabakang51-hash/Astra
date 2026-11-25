"""Regression tests for bounded revision using mock model outputs and SQLite."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_evidence", "astra_vector_memory", "astra_hybrid_evidence",
             "astra_quantitative_verify", "astra_verified_finance", "astra_orchestrator",
             "astra_recursive_revision"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]
R = sys.modules["astra_recursive_revision"]


class RevisionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.ledger = E.Ledger(Path(self.tmp.name)/"evidence.sqlite")
        self.addCleanup(self.ledger.close)
        self.id = self.ledger.add("Duration","Duration measures price sensitivity.",
                                  "Reviewed textbook",reviewed=True)
        self.retrieval = H.HybridEvidence(self.ledger)
        self.calls = []

    def draft(self, answer="Tentative first draft"):
        return json.dumps({"answer":answer,"citations":[self.id]})

    def critique(self, assessment="concerns"):
        return json.dumps({"assessment":assessment,
                           "concerns":["Explain sensitivity to yield changes."] if assessment=="concerns" else []})

    def runner(self, responses):
        responses = iter(responses)
        def run(role,prompt):
            self.calls.append((role,json.loads(prompt.split("\n",1)[1])))
            response = next(responses)
            if isinstance(response,Exception):
                raise response
            return response
        return run

    def revise(self, responses, question="Explain duration"):
        return R.revise(question,self.ledger,self.retrieval,self.runner(responses),approved=True)

    def test_revision_is_bounded_and_preference_is_advisory(self):
        result = self.revise([self.draft(),self.critique(),self.draft("Revised explanation"),
                              '{"preference":"first","reason":"Addresses the concern."}'])
        self.assertEqual(4,result["model_calls"])
        self.assertEqual(["primary","critic","primary","critic"],[role for role,_ in self.calls])
        self.assertEqual(["original","revision"],[row["label"] for row in result["drafts"]])
        self.assertEqual("revision",result["comparison"]["preference"])
        self.assertEqual("advisory_only",result["comparison"]["status"])
        self.assertEqual("unverified_drafts",result["status"])
        self.assertEqual("Revised explanation",self.calls[3][1]["first"]["answer"])
        self.assertNotIn("label",self.calls[3][1]["first"])
        self.assertEqual(1,len(self.ledger.search("duration")))

    def test_reviewer_can_prefer_original_or_be_uncertain(self):
        for preference,expected in (("second","original"),("tie","tie"),("uncertain","uncertain")):
            result = self.revise([self.draft(),self.critique(),self.draft("Revision"),
                                  json.dumps({"preference":preference,"reason":"Tentative review."})])
            self.assertEqual(expected,result["comparison"]["preference"])
            self.assertEqual(2,len(result["drafts"]))

    def test_no_concrete_concerns_avoids_extra_inference(self):
        for assessment in ("no_issue_found","uncertain"):
            result = self.revise([self.draft(),self.critique(assessment)])
            self.assertEqual(2,result["model_calls"])
            self.assertEqual("skipped_no_concrete_concerns",result["revision_status"])

    def test_invalid_or_failed_revision_preserves_original(self):
        for failure in ('{"answer":"wrong source","citations":[999]}',TimeoutError("private path")):
            result = self.revise([self.draft(),self.critique(),failure])
            self.assertEqual(3,result["model_calls"])
            self.assertEqual("model_unavailable_or_invalid",result["revision_status"])
            self.assertEqual(1,len(result["drafts"]))
            self.assertNotIn("private path",str(result))

    def test_failed_review_and_unchanged_revision_are_explicit(self):
        result = self.revise([self.draft(),"invalid"])
        self.assertEqual("skipped_review_unavailable",result["revision_status"])
        result = self.revise([self.draft(),self.critique(),self.draft()])
        self.assertEqual("unchanged",result["revision_status"])
        self.assertEqual(3,result["model_calls"])

    def test_malformed_comparative_review_does_not_select_winner(self):
        result = self.revise([self.draft(),self.critique(),self.draft("Revision"),
                              '{"preference":"second","reason":"ok","action":"delete"}'])
        self.assertEqual({"status":"unavailable"},result["comparison"])
        self.assertEqual(2,len(result["drafts"]))

    def test_consent_and_insufficient_evidence_prevent_model_execution(self):
        def forbidden(*args):
            self.fail("Unexpected model execution")
        with self.assertRaises(ValueError):
            R.revise("duration",self.ledger,self.retrieval,forbidden)
        result = R.revise("unrelated astronomy",self.ledger,self.retrieval,forbidden,approved=True)
        self.assertEqual(0,result["model_calls"])

    def test_stale_evidence_discards_even_on_model_failure(self):
        def run(role,prompt):
            if role=="critic":
                self.ledger.clear(confirmed=True)
                raise RuntimeError("unavailable")
            return self.draft()
        result = R.revise("duration",self.ledger,self.retrieval,run,approved=True)
        self.assertEqual("evidence_changed_retry",result["status"])
        self.assertEqual([],result["drafts"])
        self.assertEqual(2,result["model_calls"])

    def test_uncited_evidence_is_revalidated(self):
        other = self.ledger.add("Duration","Another explanation.","Other reviewed source",reviewed=True)
        def run(role,prompt):
            self.ledger.add("Duration","Corrected explanation.","Other reviewed source",
                            reviewed=True,supersedes=other)
            return self.draft()
        result = R.revise("duration",self.ledger,self.retrieval,run,approved=True)
        self.assertEqual("evidence_changed_retry",result["status"])
        self.assertEqual(1,result["model_calls"])

    def test_comparison_parser_rejects_extra_duplicate_or_missing_fields(self):
        for raw in ('{"preference":"first","preference":"second","reason":"x"}',
                    '{"preference":"winner","reason":"x"}','{"preference":"tie","reason":""}',
                    '{"preference":"first"}'):
            with self.assertRaises(ValueError):
                R.parse_comparison(raw)


if __name__ == "__main__":
    unittest.main()
