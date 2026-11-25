"""Test end-to-end evidence retrieval with synthetic local embedding callbacks."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_evidence", "astra_vector_memory", "astra_hybrid_evidence"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
E = sys.modules["astra_evidence"]
H = sys.modules["astra_hybrid_evidence"]
MODEL = "a" * 64


class HybridEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.ledger = E.Ledger(Path(self.folder.name) / "knowledge.sqlite")
        self.addCleanup(self.ledger.close)
        self.hybrid = H.HybridEvidence(self.ledger)

    def add(self, subject, text, **kwargs):
        return self.ledger.add(subject, text, "Reviewed textbook", reviewed=True, **kwargs)

    def refresh(self, embed=lambda texts: [(1, 0)] * len(texts), **kwargs):
        return self.hybrid.refresh(embed, model_sha=MODEL, dimensions=2,
                                   approved=True, **kwargs)

    def search(self, query, embed=lambda texts: [(1, 0)] * len(texts), **kwargs):
        return self.hybrid.search(query, use_vector=True, approved=True,
                                  embed=embed, model_sha=MODEL, **kwargs)

    def test_lexical_default_and_explicit_vector_consent(self):
        expected = self.add("Duration", "Interest rate exposure.")
        result = self.hybrid.search("duration")
        self.assertEqual("not_requested", result["vector_status"])
        self.assertEqual(expected, result["results"][0]["id"])
        def no_embedding(texts):
            self.fail("Unauthorized embedding invocation")
        with self.assertRaises(ValueError):
            self.hybrid.refresh(no_embedding, model_sha=MODEL, dimensions=2)
        with self.assertRaises(ValueError):
            self.hybrid.search("duration", use_vector=True, embed=no_embedding)
        with self.assertRaises(ValueError):
            self.hybrid.search("duration", embed=no_embedding)

    def test_vector_retrieval_finds_evidence_without_shared_words(self):
        target = self.add("Fixed income", "Duration measures price sensitivity.")
        other = self.add("Astronomy", "Stars and planets.")
        def embed(texts):
            return [(0,1) if "Astronomy" in text else (1,0) for text in texts]
        self.refresh(embed)
        result = self.search("bond exposure", embed)
        self.assertEqual([target], [row["id"] for row in result["results"]])
        self.assertIsNone(result["results"][0]["lexical_rank"])
        self.assertEqual(1, result["results"][0]["vector_rank"])
        self.assertEqual("owner_reviewed_not_independently_verified", result["results"][0]["trust"])

    def test_fusion_combines_rankings_and_keeps_single_copy(self):
        shared = self.add("Duration", "Fixed income risk.")
        self.add("Duration", "Another discussion.")
        self.refresh(lambda texts: [(1,0) if "Fixed income" in t else (0,1) for t in texts])
        result = self.search("duration", lambda texts: [(1,0)] * len(texts))
        self.assertEqual(shared, result["results"][0]["id"])
        self.assertEqual(2, len(result["results"]))
        self.assertEqual(1, result["results"][0]["lexical_rank"])
        self.assertEqual(1, result["results"][0]["vector_rank"])

    def test_corrected_or_deleted_evidence_cannot_reappear(self):
        stale = self.add("Duration", "Incorrect claim.")
        self.refresh()
        active = self.add("Duration", "Corrected statement.", supersedes=stale)
        result = self.search("duration")
        self.assertEqual([active], [row["id"] for row in result["results"]])
        self.assertIsNone(result["results"][0]["vector_rank"])
        self.ledger.clear(confirmed=True)
        self.assertEqual([], self.search("duration")["results"])

    def test_revalidation_after_concurrent_change_during_query_embedding(self):
        stale = self.add("Duration", "Incorrect claim.")
        self.refresh()
        def embed(texts):
            self.add("Duration", "Corrected while searching.", supersedes=stale)
            return [(1,0)]
        result = self.search("duration", embed)
        self.assertEqual([], result["results"])
        self.assertNotIn("Incorrect claim", str(result))

    def test_partial_snapshot_is_explicit_and_lexical_search_covers_older_rows(self):
        older = self.add("Old reference", "Historic yields.")
        latest = self.add("New reference", "Current coupon.")
        coverage = self.refresh(limit=1)
        self.assertTrue(coverage["partial_snapshot"])
        self.assertEqual(2, coverage["active_records_at_index_build"])
        self.assertEqual(1, coverage["indexed_snapshot_records"])
        result = self.search("historic")
        self.assertEqual({older, latest}, {row["id"] for row in result["results"]})
        old = next(row for row in result["results"] if row["id"] == older)
        self.assertIsNone(old["vector_rank"])

    def test_embedding_failure_falls_back_and_failed_refresh_drops_index(self):
        expected = self.add("Duration", "Fixed income risk.")
        self.refresh()
        def broken(texts):
            raise RuntimeError("private runtime path")
        result = self.search("duration", broken)
        self.assertEqual("embedding_failed_lexical_only", result["vector_status"])
        self.assertEqual([expected], [row["id"] for row in result["results"]])
        self.assertNotIn("private runtime path", str(result))
        with self.assertRaises(RuntimeError):
            self.refresh(broken)
        self.assertEqual("index_unavailable", self.search("duration")["vector_status"])

    def test_model_version_mismatch_rejected_before_embedding(self):
        self.add("Duration", "Fixed income risk.")
        self.refresh()
        with self.assertRaises(ValueError):
            self.hybrid.search("duration", use_vector=True, approved=True,
                               model_sha="b"*64, embed=lambda texts: self.fail("unexpected call"))

    def test_ledger_active_lookup_is_bounded_and_excludes_superseded_rows(self):
        first = self.add("Duration", "Old.")
        second = self.add("Duration", "New.", supersedes=first)
        records, total = self.ledger.vector_snapshot()
        self.assertEqual(1, total)
        self.assertEqual([second], [row["id"] for row in records])
        self.assertEqual([second], [row["id"] for row in self.ledger.lookup_active([first, second])])
        for ids in ([True], [0], list(range(1,22))):
            with self.assertRaises(ValueError):
                self.ledger.lookup_active(ids)


if __name__ == "__main__":
    unittest.main()
