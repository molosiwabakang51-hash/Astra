"""Vector search contracts using synthetic mock embeddings, no downloaded model."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("astra_vector_memory", ROOT / "desktop/astra_vector_memory.py")
V = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = V
SPEC.loader.exec_module(V)

MODEL = "a" * 64


def evidence(record_id, subject="Fixed income", text="Duration measures price sensitivity.", **extra):
    return {"id": record_id, "subject": subject, "text": text,
            "source": "Reviewed source", "trust": V.TRUST, "active": True, **extra}


def build(records, embed):
    return V.EvidenceVectorIndex.build(records, embed, model_sha=MODEL,
                                       dimensions=2, approved=True)


class VectorMemoryTests(unittest.TestCase):
    def test_cosine_search_with_synthetic_nonlexical_match(self):
        records = [evidence(1), evidence(2, "Astronomy", "Stars and planets.")]
        def embed(texts):
            vectors = {"Fixed income\nDuration measures price sensitivity.": (2,0),
                       "Astronomy\nStars and planets.": (0,3),
                       "interest-rate exposure": (9,0)}
            return [vectors[text] for text in texts]
        index = build(records, embed)
        hits = index.search("interest-rate exposure", embed, model_sha=MODEL, approved=True)
        self.assertEqual([1], [hit.id for hit in hits])
        self.assertAlmostEqual(1.0, hits[0].similarity)
        self.assertEqual(V.TRUST, V.resolve_hits(hits, records)[0]["trust"])

    def test_reject_unreviewed_inactive_and_duplicate_records_before_embedding(self):
        calls = []
        def embed(texts):
            calls.append(texts)
            return [(1,0)] * len(texts)
        for records in ([evidence(1, trust="model_generated")],
                        [evidence(1, active=False)],
                        [evidence(1), evidence(1)]):
            with self.assertRaises(ValueError):
                build(records, embed)
        with self.assertRaises(ValueError):
            V.EvidenceVectorIndex.build([evidence(1)], embed, model_sha=MODEL, dimensions=2)
        self.assertEqual([], calls)

    def test_model_identity_and_query_consent_enforced(self):
        embed = lambda texts: [(1,0)] * len(texts)
        index = build([evidence(1)], embed)
        calls = []
        def query_embed(texts):
            calls.append(texts)
            return embed(texts)
        for opts in ({"model_sha":"b"*64, "approved":True}, {"model_sha":MODEL, "approved":False}):
            with self.assertRaises(ValueError):
                index.search("duration", query_embed, **opts)
        self.assertEqual([], calls)

    def test_deleted_corrected_and_reused_ids_cannot_leak_stale_content(self):
        records = [evidence(1), evidence(2), evidence(3)]
        embed = lambda texts: [(1,0)] * len(texts)
        index = build(records, embed)
        hits = index.search("duration", embed, model_sha=MODEL, approved=True)
        current = [evidence(1, text="Updated correction"), evidence(2, active=False), evidence(3)]
        resolved = V.resolve_hits(hits, current)
        self.assertEqual([3], [row["id"] for row in resolved])
        self.assertEqual([], V.resolve_hits(hits, []))

    def test_invalid_vectors_fail_closed(self):
        for vector in ((1,), (0,0), (float("nan"),0), (float("inf"),1),
                       (True,0), (10**1000,0)):
            with self.assertRaises(ValueError):
                build([evidence(1)], lambda texts: [vector])
        with self.assertRaises(ValueError):
            build([evidence(1)], lambda texts: [])
        with self.assertRaises(ValueError):
            V.EvidenceVectorIndex.build([evidence(1)], lambda texts: [],
                                       model_sha=MODEL, dimensions=2049, approved=True)

    def test_batch_size_and_index_memory_bound(self):
        calls = []
        def embed(texts):
            calls.append(len(texts))
            return [(1,0)] * len(texts)
        index = build([evidence(i) for i in range(1, 36)], embed)
        self.assertEqual([16,16,3], calls)
        hits = index.search("duration", embed, model_sha=MODEL, approved=True, limit=3)
        self.assertEqual([1,2,3], [hit.id for hit in hits])
        with self.assertRaises(ValueError):
            build([evidence(i) for i in range(1, V.MAX_RECORDS+2)], embed)

    def test_index_retains_vectors_and_fingerprints_not_raw_evidence(self):
        record = evidence(1, source="private source label", text="private evidence excerpt")
        def embed(texts):
            self.assertNotIn("private source label", texts[0])
            return [(1,0)]
        index = build([record], embed)
        self.assertNotIn("private evidence excerpt", repr(index.__dict__))
        self.assertNotIn("private source label", repr(index.__dict__))
        self.assertEqual(64, len(index._entries[0].fingerprint))

    def test_negative_similarity_and_empty_index(self):
        index = build([evidence(1)], lambda texts: [(1,0)])
        self.assertEqual([], index.search("unrelated", lambda texts: [(-1,0)],
                                        model_sha=MODEL, approved=True))
        empty = build([], lambda texts: self.fail("No evidence should be embedded"))
        self.assertEqual([], empty.search("duration", lambda texts: self.fail("No query needed"),
                                         model_sha=MODEL, approved=True))


if __name__ == "__main__":
    unittest.main()
