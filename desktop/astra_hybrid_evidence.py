"""Unified bounded lexical/vector retrieval over the reviewed evidence ledger.

Lexical search is the default. Vector processing requires separate explicit
approval both when indexing and when querying. This module accepts a trusted
local embedding callback; it cannot attest to the callback's implementation.
It never writes embeddings to disk or treats relevance as factual verification.
"""
from astra_evidence import clean, MAX_QUERY
from astra_vector_memory import (
    EvidenceVectorIndex, MAX_RECORDS, resolve_hits, validate_record,
)


class HybridEvidence:
    def __init__(self, ledger):
        self.ledger = ledger
        self._index = None
        self._snapshot_total = 0
        self._snapshot_indexed = 0

    def discard_index(self):
        self._index = None
        self._snapshot_total = 0
        self._snapshot_indexed = 0

    def refresh(self, embed, *, model_sha, dimensions, approved=False, limit=MAX_RECORDS):
        if approved is not True:
            raise ValueError("Explicit local embedding approval required.")
        # Do not keep serving an old index when a replacement fails.
        self.discard_index()
        records, total = self.ledger.vector_snapshot(limit)
        index = EvidenceVectorIndex.build(
            records, embed, model_sha=model_sha, dimensions=dimensions, approved=True)
        self._index = index
        self._snapshot_total = total
        self._snapshot_indexed = len(records)
        return self.coverage()

    def coverage(self):
        return {
            "indexed_snapshot_records": self._snapshot_indexed,
            "active_records_at_index_build": self._snapshot_total,
            "partial_snapshot": self._snapshot_indexed < self._snapshot_total,
            "scope": "Newest active records at build time; subsequent changes require refresh.",
        }

    def search(self, query, *, limit=5, use_vector=False, approved=False,
               embed=None, model_sha=None, minimum_similarity=0.25):
        query = clean(query, MAX_QUERY, "query")
        if type(limit) is not int or not 1 <= limit <= 10:
            raise ValueError("Search limit must be 1–10.")
        if use_vector is not True and (embed is not None or model_sha is not None):
            raise ValueError("Vector settings require explicit vector retrieval.")
        if use_vector is True and (approved is not True or not callable(embed)):
            raise ValueError("Explicit approval and local embedding callback required.")
        lexical = self.ledger.search(query, limit=10)
        vector = []
        vector_status = "not_requested"
        if use_vector is True:
            if self._index is None:
                vector_status = "index_unavailable"
            else:
                # Reject version mismatch before passing the query to a model.
                if model_sha != self._index.model_sha:
                    raise ValueError("Embedding model identity mismatch.")
                try:
                    vector = self._index.search(
                        query, embed, model_sha=model_sha, approved=True,
                        limit=10, minimum_similarity=minimum_similarity)
                    vector_status = "searched_snapshot"
                except (OSError, RuntimeError, ValueError):
                    vector_status = "embedding_failed_lexical_only"
        # Fetch both result sets in a single bounded active-record lookup.
        # Exact content/provenance comparison prevents stale lexical results;
        # vector resolution compares fingerprints and removes superseded rows.
        ids = list(dict.fromkeys(
            [row["id"] for row in lexical] + [hit.id for hit in vector]))
        current = self.ledger.lookup_active(ids)
        active = {row["id"]: row for row in current}
        lexical_ranks = {}
        for rank, row in enumerate(lexical, 1):
            now = active.get(row["id"])
            fields = ("subject", "text", "source", "kind", "supersedes")
            if now is not None and all(row[key] == now[key] for key in fields):
                lexical_ranks[row["id"]] = rank
        resolved = resolve_hits(vector, current)
        vector_ranks = {row["id"]: rank for rank, row in enumerate(resolved, 1)}
        similarities = {row["id"]: row["vector_similarity"] for row in resolved}
        # Reciprocal rank fusion avoids comparing a cosine score directly
        # with a lexical hit count. Rank is not factual confidence.
        ranked = []
        for record_id in lexical_ranks.keys() | vector_ranks.keys():
            score = sum(1 / (60 + ranks[record_id]) for ranks in
                        (lexical_ranks, vector_ranks) if record_id in ranks)
            row = active[record_id]
            # Check evidence invariants before returning any content.
            validate_record(row)
            ranked.append((score, record_id, {
                "id": record_id, "subject": row["subject"], "text": row["text"],
                "source": row["source"], "trust": row["trust"],
                "lexical_rank": lexical_ranks.get(record_id),
                "vector_rank": vector_ranks.get(record_id),
                "vector_similarity": similarities.get(record_id),
            }))
        ranked.sort(key=lambda item: (-item[0], item[1]))
        return {
            "results": [item[2] for item in ranked[:limit]],
            "vector_status": vector_status,
            "vector_coverage": self.coverage(),
            "scope": "Retrieved owner-reviewed evidence; relevance is not verification.",
        }
