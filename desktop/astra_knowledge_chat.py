"""Interactive evidence-grounded desktop chat with optional local embeddings.

This pilot retains the root question and last successful draft in memory only.
A trusted local executable and model must be explicitly approved. The optional
embedding model is separately approved; startup embedding failures use lexical
search. Neither model may write to the ledger or execute generated commands.
"""
import argparse
from contextlib import ExitStack
import json
from pathlib import Path
import sys

from astra_evidence import Ledger
from astra_hybrid_evidence import HybridEvidence
from astra_knowledge_session import KnowledgeSession
from astra_local_answer import LocalAnswerRunner
from astra_local_embedding import LocalEmbedder
from astra_orchestrator import Orchestrator

MAX_LINE_BYTES = 4096


def interact(session, source, output, errors, **settings):
    try:
        while True:
            raw = source.readline(MAX_LINE_BYTES + 1)
            if not raw:
                return 0
            if len(raw) > MAX_LINE_BYTES:
                errors.write("Input exceeds session limit; session ended.\n")
                return 1
            try:
                question = raw.decode("utf-8", errors="strict").strip()
                if question.casefold() in ("quit", "exit"):
                    return 0
                if not question:
                    continue
                result = session.execute(question, **settings)
                output.write(json.dumps(result, ensure_ascii=True, allow_nan=False) + "\n")
                output.flush()
            except (ValueError, OSError, RuntimeError, TypeError, RecursionError):
                session.reset()
                errors.write("Invalid or unavailable local question; context cleared.\n")
                errors.flush()
    finally:
        session.reset()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger", required=True)
    parser.add_argument("--binary", required=True)
    parser.add_argument("--primary", required=True)
    parser.add_argument("--critic")
    parser.add_argument("--review", action="store_true")
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--embedding-binary")
    parser.add_argument("--embedding-model")
    parser.add_argument("--dimensions", type=int)
    parser.add_argument("--pooling", choices=("mean", "cls", "last"))
    parser.add_argument("--query-prefix", default="")
    parser.add_argument("--document-prefix", default="")
    parser.add_argument("--approve-embeddings", action="store_true")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    if not args.approve_models or (args.review and not args.critic):
        parser.error("Model use requires explicit approval and a critic for review.")
    embed_config = (args.embedding_binary, args.embedding_model, args.dimensions, args.pooling)
    if args.approve_embeddings and not all(value is not None for value in embed_config):
        parser.error("Approved embeddings require binary, model, dimensions and pooling.")
    if not args.approve_embeddings and (any(value is not None for value in embed_config)
                                         or args.query_prefix or args.document_prefix):
        parser.error("Embedding configuration requires separate explicit approval.")
    if not Path(args.ledger).is_file():
        parser.error("Existing local evidence ledger required.")
    try:
        with ExitStack() as stack:
            ledger = stack.enter_context(Ledger(args.ledger))
            retrieval = HybridEvidence(ledger)
            runner = LocalAnswerRunner(args.binary, args.primary, args.critic,
                                       timeout=args.timeout, approved=True)
            settings = {"approve_models": True, "review": args.review}
            if args.approve_embeddings:
                try:
                    adapter = LocalEmbedder(args.embedding_binary, args.embedding_model,
                                            dimensions=args.dimensions, pooling=args.pooling,
                                            query_prefix=args.query_prefix,
                                            document_prefix=args.document_prefix,
                                            timeout=args.timeout, approved=True)
                    retrieval.refresh(adapter.documents, model_sha=adapter.profile_sha,
                                      dimensions=adapter.dimensions, approved=True)
                    settings.update(use_vector=True, approve_embeddings=True,
                                    embed=adapter.query, model_sha=adapter.profile_sha)
                except (OSError, ValueError, RuntimeError):
                    retrieval.discard_index()
                    sys.stderr.write("Local embedding unavailable; lexical retrieval only.\n")
            session = KnowledgeSession(Orchestrator(ledger, retrieval, runner))
            return interact(session, sys.stdin.buffer, sys.stdout, sys.stderr, **settings)
    except (OSError, ValueError, RuntimeError):
        print("Local knowledge session unavailable.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
