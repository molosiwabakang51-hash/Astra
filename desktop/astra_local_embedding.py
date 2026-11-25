"""Opt-in, sequential CPU embedding adapter for a trusted local llama.cpp binary.

Uses llama-embedding's documented array output. No download or server. The
runtime profile includes the binary, weights, dimensions, pooling and prefixes.
Use documents() to index and query() to search with the same profile_sha.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading

from astra_resource_plan import model_file, plan, probe
from astra_vector_memory import BATCH, validate_model, unit_vector

SEPARATOR = "<#astra-embedding-separator#>"
MAX_BATCH_BYTES = 3500
MAX_OUTPUT_BYTES = 2 * 1024 * 1024
CONTEXT = 4096


def digest(path):
    hash_value = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hash_value.update(block)
    return hash_value.hexdigest()


def stamp(path):
    state = path.stat()
    return state.st_size, state.st_mtime_ns, state.st_ino


def parse_vectors(raw, count, dimensions):
    if not isinstance(raw, bytes) or len(raw) > MAX_OUTPUT_BYTES:
        raise ValueError("Embedding output outside byte limit.")
    vectors = json.loads(raw.decode("utf-8"))
    if not isinstance(vectors, list) or len(vectors) != count:
        raise ValueError("Incomplete or unexpected embedding array.")
    return [unit_vector(row, dimensions) for row in vectors]


def pack(texts, prefix):
    if not isinstance(texts, (tuple, list)) or not 1 <= len(texts) <= BATCH:
        raise ValueError("Embedding request outside batch limit.")
    payloads = []
    # Validate the entire batch before processing any content.
    for text in texts:
        if not isinstance(text, str) or not text.strip() or "\x00" in text:
            raise ValueError("Invalid embedding text.")
        value = (prefix + text).encode("utf-8")
        if SEPARATOR.encode() in value or len(value) > MAX_BATCH_BYTES:
            raise ValueError("Text exceeds embedding input limit or contains separator.")
        payloads.append(value)
    groups = []
    current = []
    size = 0
    separator = SEPARATOR.encode("utf-8")
    for value in payloads:
        added = len(value) + (len(separator) if current else 0)
        if current and size + added > MAX_BATCH_BYTES:
            groups.append(current)
            current, size = [], 0
        size += len(value) + (len(separator) if current else 0)
        current.append(value)
    if current:
        groups.append(current)
    return groups


class LocalEmbedder:
    def __init__(self, binary, model, *, dimensions, pooling="mean",
                 document_prefix="", query_prefix="", timeout=180, approved=False):
        if approved is not True:
            raise ValueError("Explicit local embedding approval required.")
        self.binary, self.model = (Path(x).resolve() for x in (binary, model))
        if not self.binary.is_file() or self.binary.name.lower() not in ("llama-embedding", "llama-embedding.exe"):
            raise ValueError("Trusted local llama-embedding executable required.")
        model_file(self.model)
        if pooling not in ("mean", "cls", "last"):
            raise ValueError("Supported pooled embedding modes are mean, cls and last.")
        for prefix in (document_prefix, query_prefix):
            if not isinstance(prefix, str) or len(prefix.encode("utf-8")) > 500 or "\x00" in prefix or SEPARATOR in prefix:
                raise ValueError("Invalid model-specific prefix.")
        if type(timeout) is not int or not 30 <= timeout <= 300:
            raise ValueError("Timeout must be 30–300 seconds.")
        validate_model("0" * 64, dimensions)
        self.dimensions, self.pooling, self.timeout = dimensions, pooling, timeout
        self.document_prefix, self.query_prefix = document_prefix, query_prefix
        self._stamps = stamp(self.binary), stamp(self.model)
        self.weights_sha = digest(self.model)
        profile = [digest(self.binary), self.weights_sha, dimensions, pooling,
                   document_prefix, query_prefix, CONTEXT, "astra-embedding-v1"]
        self.profile_sha = hashlib.sha256(json.dumps(profile, ensure_ascii=False,
                                                     separators=(",", ":")).encode()).hexdigest()
        self._lock = threading.Lock()
        self._check_files()

    def _check_files(self):
        if (stamp(self.binary), stamp(self.model)) != self._stamps:
            raise RuntimeError("Embedding runtime or model changed; rebuild the index.")

    def documents(self, texts):
        return self._embed(texts, self.document_prefix)

    def query(self, texts):
        return self._embed(texts, self.query_prefix)

    def _embed(self, texts, prefix):
        groups = pack(texts, prefix)
        with self._lock:
            result = []
            for group in groups:
                result.extend(self._execute(group))
            return result

    def _execute(self, group):
        self._check_files()
        name, size = model_file(self.model)
        admission = plan(probe(self.model.parent), [(name, size)], context=CONTEXT)
        if not admission["all_admitted_individually"]:
            raise RuntimeError("Insufficient RAM for embedding model.")
        threads = str(admission["threads"])
        environment = {key: value for key, value in os.environ.items()
                       if not key.upper().startswith(("LLAMA_", "HF_", "HUGGINGFACE_", "MTMD_"))}
        with tempfile.TemporaryDirectory(prefix="astra-embedding-") as directory:
            prompt = Path(directory) / "batch.txt"
            prompt.write_bytes(SEPARATOR.encode("utf-8").join(group))
            command = [str(self.binary), "-m", str(self.model), "-f", str(prompt),
                       "--offline", "--no-escape", "--log-disable",
                       "--pooling", self.pooling, "--embd-normalize", "2",
                       "--embd-output-format", "array", "--embd-separator", SEPARATOR,
                       "-c", str(CONTEXT), "-b", str(CONTEXT), "-ub", str(CONTEXT),
                       "-ngl", "0", "-t", threads, "-tb", threads]
            try:
                process = subprocess.run(command, stdin=subprocess.DEVNULL,
                                         stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                         timeout=self.timeout, shell=False, check=False,
                                         env=environment, cwd=directory)
            except subprocess.TimeoutExpired:
                raise RuntimeError("Local embedding timed out.") from None
            if process.returncode:
                raise RuntimeError("Local embedding failed.")
            self._check_files()
            return parse_vectors(process.stdout, len(group), self.dimensions)
