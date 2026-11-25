"""Bounded in-memory evidence vector index for Astra Desktop.

The caller must provide a trusted, local embedding adapter. This module does
not download, train or invoke a model. Embeddings are potentially sensitive:
only explicitly owner-reviewed active evidence is accepted and nothing is
persisted. Model digests are caller-supplied, not runtime attestation.
"""
from dataclasses import dataclass
from hashlib import sha256
import json
import math
import re

MAX_RECORDS = 256
MAX_DIMENSIONS = 2048
MAX_QUERY = 500
BATCH = 16
DIGEST = re.compile(r"[0-9a-f]{64}\Z")
TRUST = "owner_reviewed_not_independently_verified"


def validate_model(model_sha, dimensions):
    if not isinstance(model_sha, str) or not DIGEST.fullmatch(model_sha):
        raise ValueError("Embedding model SHA-256 identity required.")
    if type(dimensions) is not int or not 1 <= dimensions <= MAX_DIMENSIONS:
        raise ValueError("Embedding dimensions out of bounds.")


def bounded_text(value, limit):
    if not isinstance(value, str) or not value.strip() or len(value) > limit or "\x00" in value:
        raise ValueError("Invalid evidence text.")
    return value


def validate_record(record):
    if type(record) is not dict:
        raise ValueError("Expected structured evidence record.")
    record_id = record.get("id")
    if type(record_id) is not int or not 1 <= record_id <= (2**63 - 1):
        raise ValueError("Invalid evidence ID.")
    if record.get("trust") != TRUST or record.get("active") is not True:
        raise ValueError("Only owner-reviewed active evidence may be embedded.")
    subject = bounded_text(record.get("subject"), 200)
    text = bounded_text(record.get("text"), 4000)
    source = bounded_text(record.get("source"), 500)
    serialized = json.dumps([record_id, subject, text, source], ensure_ascii=False,
                            separators=(",", ":")).encode("utf-8")
    digest = sha256(serialized).hexdigest()
    # Source provenance is fingerprinted but not sent to the embedder.
    return record_id, digest, subject + "\n" + text


def unit_vector(vector, dimensions):
    if not isinstance(vector, (list, tuple)) or len(vector) != dimensions:
        raise ValueError("Embedding dimensions do not match the selected model.")
    for value in vector:
        if type(value) not in (float, int) or abs(value) > 1e20 or not math.isfinite(value):
            raise ValueError("Invalid embedding coordinate.")
    magnitude = math.hypot(*vector)
    if magnitude <= 1e-20 or not math.isfinite(magnitude):
        raise ValueError("Zero or invalid embedding magnitude.")
    return tuple(value / magnitude for value in vector)


def embeddings(embed, texts, dimensions):
    result = embed(tuple(texts))
    if not isinstance(result, (list, tuple)) or len(result) != len(texts):
        raise ValueError("Embedding batch is incomplete.")
    return [unit_vector(vector, dimensions) for vector in result]


@dataclass(frozen=True)
class Entry:
    id: int
    fingerprint: str
    vector: tuple


@dataclass(frozen=True)
class Hit:
    id: int
    fingerprint: str
    similarity: float


class EvidenceVectorIndex:
    def __init__(self, model_sha, dimensions, entries):
        validate_model(model_sha, dimensions)
        self.model_sha = model_sha
        self.dimensions = dimensions
        self._entries = tuple(entries)

    @classmethod
    def build(cls, records, embed, *, model_sha, dimensions, approved=False):
        if approved is not True:
            raise ValueError("Explicit local embedding approval required.")
        validate_model(model_sha, dimensions)
        if not isinstance(records, (tuple, list)) or len(records) > MAX_RECORDS:
            raise ValueError("Evidence batch exceeds the bounded index.")
        validated = [validate_record(record) for record in records]
        if len({item[0] for item in validated}) != len(validated):
            raise ValueError("Duplicate evidence IDs.")
        entries = []
        for start in range(0, len(validated), BATCH):
            batch = validated[start:start+BATCH]
            vectors = embeddings(embed, [item[2] for item in batch], dimensions)
            entries.extend(Entry(item[0], item[1], vector)
                           for item, vector in zip(batch, vectors))
        # No text or provenance is stored in this index; the evidence ledger
        # must supply the current matching record when a result is resolved.
        return cls(model_sha, dimensions, entries)

    def search(self, query, embed, *, model_sha, approved=False, limit=5, minimum_similarity=0.25):
        if approved is not True or model_sha != self.model_sha:
            raise ValueError("Embedding consent and matching model identity required.")
        bounded_text(query, MAX_QUERY)
        if type(limit) is not int or not 1 <= limit <= 10:
            raise ValueError("Invalid result limit.")
        if type(minimum_similarity) not in (float, int) or not 0 <= minimum_similarity <= 1:
            raise ValueError("Invalid similarity threshold.")
        if not self._entries:
            return []
        vector = embeddings(embed, [query], self.dimensions)[0]
        results = []
        for entry in self._entries:
            score = min(1.0, max(-1.0, math.fsum(a*b for a,b in zip(vector, entry.vector))))
            if score >= minimum_similarity:
                results.append(Hit(entry.id, entry.fingerprint, score))
        return sorted(results, key=lambda hit: (-hit.similarity, hit.id))[:limit]


def resolve_hits(hits, current_records):
    """Resolve only unchanged active records; superseded or deleted rows vanish.

    Similarity is relevance, not a factual confidence score. The caller must
    re-read active evidence immediately before supplying it to a model.
    """
    if not isinstance(hits, (list, tuple)) or len(hits) > 10:
        raise ValueError("Invalid vector hits.")
    if not isinstance(current_records, (tuple, list)) or len(current_records) > MAX_RECORDS:
        raise ValueError("Invalid current evidence batch.")
    active = {}
    for record in current_records:
        if type(record) is not dict or record.get("active") is not True:
            continue
        record_id, digest, _ = validate_record(record)
        if record_id in active:
            raise ValueError("Duplicate current evidence ID.")
        active[record_id] = (digest, record)
    result = []
    seen = set()
    for hit in hits:
        if not isinstance(hit, Hit) or hit.id in seen or not math.isfinite(hit.similarity) or not -1 <= hit.similarity <= 1:
            raise ValueError("Invalid vector hit.")
        seen.add(hit.id)
        current = active.get(hit.id)
        if current and current[0] == hit.fingerprint:
            row = current[1]
            result.append({"id": row["id"], "subject": row["subject"], "text": row["text"],
                           "source": row["source"], "trust": TRUST,
                           "vector_similarity": round(hit.similarity, 6)})
    return result
