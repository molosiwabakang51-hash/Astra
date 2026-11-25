"""Small local semantic-retrieval smoke test; only synthetic evidence is used."""
import argparse
import json
from pathlib import Path
import tempfile
import time
import sys

from astra_evidence import Ledger
from astra_hybrid_evidence import HybridEvidence
from astra_local_embedding import LocalEmbedder

DOCUMENTS = (
    ("fixed_income", "Modified duration", "A bond's price sensitivity to changes in yield."),
    ("astronomy", "Planetary orbits", "Celestial objects revolve around stars under gravity."),
    ("computing", "Volatile memory", "RAM temporarily retains information while power is supplied."),
    ("biology", "Photosynthesis", "Plants use sunlight to produce sugars from water and carbon dioxide."),
)
QUERIES = (
    ("fixed_income", "Interest rate exposure of debt securities"),
    ("astronomy", "Movement of worlds around a sun"),
    ("computing", "Short-term computer storage lost when switched off"),
    ("biology", "How vegetation converts light into chemical energy"),
)


def smoke(adapter):
    with tempfile.TemporaryDirectory(prefix="astra-synthetic-retrieval-") as directory:
        with Ledger(Path(directory) / "synthetic.sqlite") as ledger:
            ids = {}
            for key, subject, text in DOCUMENTS:
                ids[key] = ledger.add(subject, text, "Synthetic evaluation fixture", reviewed=True)
            hybrid = HybridEvidence(ledger)
            start = time.monotonic()
            hybrid.refresh(adapter.documents, model_sha=adapter.profile_sha,
                           dimensions=adapter.dimensions, approved=True)
            build_seconds = time.monotonic() - start
            rows = []
            for key, query in QUERIES:
                lexical = hybrid.search(query, limit=1)
                started = time.monotonic()
                combined = hybrid.search(query, limit=1, use_vector=True, approved=True,
                                         embed=adapter.query, model_sha=adapter.profile_sha)
                if combined["vector_status"] != "searched_snapshot":
                    raise RuntimeError("Incomplete embedding evaluation.")
                rows.append({
                    "case": key,
                    "lexical_top1": bool(lexical["results"] and lexical["results"][0]["id"] == ids[key]),
                    "hybrid_top1": bool(combined["results"] and combined["results"][0]["id"] == ids[key]),
                    "query_seconds": round(time.monotonic() - started, 3),
                })
    return {
        "profile_sha": adapter.profile_sha,
        "cases": rows,
        "lexical_top1_correct": sum(row["lexical_top1"] for row in rows),
        "hybrid_top1_correct": sum(row["hybrid_top1"] for row in rows),
        "index_build_seconds": round(build_seconds, 3),
        "scope": "Four fixed synthetic cases only; no general retrieval or intelligence claim.",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary")
    parser.add_argument("model")
    parser.add_argument("--dimensions", type=int, required=True)
    parser.add_argument("--pooling", choices=("mean","cls","last"), required=True)
    parser.add_argument("--document-prefix", default="")
    parser.add_argument("--query-prefix", default="")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--approve-local-model", action="store_true")
    args = parser.parse_args(argv)
    try:
        adapter = LocalEmbedder(args.binary,args.model,dimensions=args.dimensions,
                                pooling=args.pooling,document_prefix=args.document_prefix,
                                query_prefix=args.query_prefix,timeout=args.timeout,
                                approved=args.approve_local_model)
        result = smoke(adapter)
    except (OSError, ValueError, RuntimeError):
        print("Local retrieval smoke test incomplete; no score.", file=sys.stderr)
        return 1
    print(json.dumps(result,indent=2,allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
