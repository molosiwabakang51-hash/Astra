"""Tests for the owner-reviewed local evidence ledger."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("astra_evidence", ROOT / "desktop/astra_evidence.py")
E = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(E)

class EvidenceLedgerTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.path = Path(self.folder.name) / "evidence.sqlite"
        self.ledger = E.Ledger(self.path)

    def tearDown(self):
        self.ledger.close()
        self.folder.cleanup()

    def add(self, subject, text, source="Owner-reviewed textbook", **kwargs):
        return self.ledger.add(subject, text, source, reviewed=True, **kwargs)

    def test_requires_review_and_provenance(self):
        for reviewed in (False, None, "yes", 1):
            with self.assertRaises(ValueError):
                self.ledger.add("Bonds", "Coupons are payments.", "Textbook", reviewed=reviewed)
        with self.assertRaises(ValueError):
            self.add("Bonds", "Coupons are payments.", "")
        self.assertEqual([], self.ledger.search("bonds"))

    def test_duplicate_is_idempotent_and_survives_restart(self):
        first = self.add("Bond duration", "Duration measures interest rate sensitivity.")
        second = self.add("Bond duration", "Duration measures interest rate sensitivity.")
        self.assertEqual(first, second)
        with E.Ledger(self.path) as reopened:
            records = reopened.search("bond duration")
            self.assertEqual(1, len(records))
            self.assertEqual(first, records[0]["id"])
            self.assertEqual("owner_reviewed_not_independently_verified", records[0]["trust"])

    def test_superseded_claim_is_not_active_but_history_is_retained(self):
        first = self.add("Bond duration", "Duration equals maturity.")
        second = self.add("Bond duration", "Modified duration measures price sensitivity.", supersedes=first)
        self.assertEqual(second, self.add("Bond duration", "Modified duration measures price sensitivity.", supersedes=first))
        self.assertEqual([second], [x["id"] for x in self.ledger.search("bond duration")])
        history = self.ledger.history("Bond duration")
        self.assertEqual([0, 1], [x["active"] for x in history])
        self.assertEqual(first, history[1]["supersedes"])
        with self.assertRaises(ValueError):
            self.add("Bond duration", "Conflicting stale correction", supersedes=first)
        with self.assertRaises(ValueError):
            self.add("Equities", "Unrelated claim", supersedes=second)
        self.assertEqual(2, len(self.ledger.history("Bond duration")))

    def test_unicode_and_subject_relevance(self):
        expected = self.add("Облигации", "Облигации имеют купоны.")
        distractor = self.add("Финансы", "Облигации являются активом.")
        hits = self.ledger.search("ОБЛИГАЦИИ")
        self.assertEqual([expected, distractor], [x["id"] for x in hits])
        self.assertEqual([], self.ledger.search("космос"))

    def test_conflicting_independent_records_are_not_silently_collapsed(self):
        a = self.add("Maturity", "Five years", "Document A")
        b = self.add("Maturity", "Six years", "Document B")
        self.assertEqual([a, b], [x["id"] for x in self.ledger.search("maturity")])

    def test_confirmed_clear_removes_correction_chain(self):
        first = self.add("Bonds", "Old claim")
        self.add("Bonds", "Replacement", supersedes=first)
        with self.assertRaises(ValueError):
            self.ledger.clear()
        self.assertEqual(2, len(self.ledger.history("Bonds")))
        self.ledger.clear(confirmed=True)
        self.assertEqual([], self.ledger.history("Bonds"))
        with E.Ledger(self.path) as reopened:
            self.assertEqual([], reopened.search("Bonds"))

    def test_limits_and_parameterized_input(self):
        with self.assertRaises(ValueError):
            self.add("Bonds", "x" * (E.MAX_TEXT + 1))
        with self.assertRaises(ValueError):
            self.ledger.search("bonds", 1000)
        self.add("SQL", "A quote: '); DROP TABLE evidence; --")
        self.assertEqual(1, len(self.ledger.search("SQL")))

if __name__ == "__main__":
    unittest.main()
