"""Explicitly opted-in local finance model runner; deterministic by default.

Requires a trusted recent llama-cli and distinct local GGUF weights.
The binary receives a temporary prompt file, never raw inputs in argv.
No network/download flags or shell invocation. This is not an OS sandbox.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from astra_native_subprocess import run_bounded
from astra_resource_plan import model_file, plan, probe
from astra_quantitative_verify import number
from astra_verified_finance import (
    adjudicate, cooperative_adjudicate, read_request, validated_request,
)

MAX_PROMPT_BYTES = 4096
MAX_OUTPUT_BYTES = 65536
DECIMAL_GRAMMAR = r'root ::= "FINAL: " [+-]? [0-9]{1,18} ("." [0-9]{1,12})? "\n"?'
FORMAT = "\nOutput precisely FINAL: followed by the single decimal value."


def file_digest(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_proposal(raw):
    if not isinstance(raw, bytes) or len(raw) > MAX_OUTPUT_BYTES:
        raise ValueError("Invalid local output size.")
    text = raw.decode("utf-8")
    if re.search(r"</?think\b", text, re.I):
        raise ValueError("Unexpected thinking output.")
    markers = re.findall(r"(?im)^[ \t]*FINAL:[ \t]*([+-]?[0-9]{1,18}(?:\.[0-9]{1,12})?)[ \t]*\r?$", text)
    if len(markers) != 1 or len(re.findall(r"(?im)^[ \t]*FINAL:", text)) != 1:
        raise ValueError("Expected exactly one valid final proposal.")
    number(markers[0])
    return markers[0]


class LocalProposer:
    def __init__(self, binary, primary, critic, timeout=180):
        self.binary, primary, critic = (Path(x).resolve() for x in (binary, primary, critic))
        if not self.binary.is_file() or self.binary.name.lower() not in ("llama-cli", "llama-cli.exe"):
            raise ValueError("Trusted local llama-cli required.")
        if type(timeout) is not int or not 30 <= timeout <= 300:
            raise ValueError("Timeout must be 30–300 seconds.")
        model_file(primary)
        model_file(critic)
        if primary == critic or file_digest(primary) == file_digest(critic):
            raise ValueError("Distinct model files required.")
        self.models = {"primary": primary, "critic": critic}
        self.timeout = timeout

    def __call__(self, role, prompt):
        if role not in self.models or not isinstance(prompt, str):
            raise ValueError("Invalid proposal request.")
        model = self.models[role]
        payload = (prompt + FORMAT).encode("utf-8")
        if len(payload) > MAX_PROMPT_BYTES:
            raise ValueError("Prompt exceeds local inference limit.")
        name, size = model_file(model)
        admission = plan(probe(model.parent), [(name, size)], context=2048)
        if not admission["all_admitted_individually"]:
            raise RuntimeError("Insufficient available memory.")
        threads = str(admission["threads"])
        environment = {k: v for k, v in os.environ.items()
                       if not k.upper().startswith(("LLAMA_", "HF_", "HUGGINGFACE_", "MTMD_"))}
        # Temporary files are removed on ordinary exits, not guaranteed erased
        # after crashes or from swap/SSD snapshots. Use trusted local storage.
        with tempfile.TemporaryDirectory(prefix="astra-finance-") as directory:
            path = Path(directory) / "prompt.txt"
            path.write_bytes(payload)
            command = [str(self.binary), "-m", str(model), "-f", str(path),
                       "--offline", "--no-mmproj", "--no-escape", "--log-disable",
                       "--single-turn", "--no-display-prompt",
                       "--simple-io", "--color", "off", "--no-show-timings",
                       "--reasoning", "off", "--cache-ram", "0", "--no-context-shift",
                       "--grammar", DECIMAL_GRAMMAR, "-c", "2048", "-n", "48",
                       "-ngl", "0", "-t", threads, "-tb", threads, "--temp", "0", "--seed", "42"]
            try:
                result = run_bounded(command, timeout=self.timeout,
                                             env=environment, cwd=directory,
                                             max_stdout_bytes=MAX_OUTPUT_BYTES)
            except subprocess.TimeoutExpired:
                raise RuntimeError("Local model timed out.") from None
            if result.returncode != 0:
                raise RuntimeError("Local model execution failed.")
            return parse_proposal(result.stdout)


def run_request(request, *, allow_model=False, binary=None, primary=None, critic=None, timeout=180):
    validated_request(request)
    if not allow_model:
        if any(value is not None for value in (binary, primary, critic)):
            raise ValueError("Model flags require explicit --allow-model.")
        return adjudicate(request)
    if allow_model is not True or not all((binary, primary, critic)):
        raise ValueError("Explicit approval and three local model-runtime paths required.")
    if request.get("candidates"):
        raise ValueError("Cannot mix supplied and generated proposals.")
    try:
        proposer = LocalProposer(binary, primary, critic, timeout)
    except (OSError, ValueError, RuntimeError):
        # No fabricated model evidence when configuration or loading fails.
        # The verified arithmetic remains available.
        result = adjudicate(request)
        result["proposal_assessment"] = {"primary": "unavailable", "critic": "unavailable"}
        result["proposal_agreement"] = "incomplete_model_comparison"
        return result
    return cooperative_adjudicate(request, proposer, allow_model=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request", help="Local structured finance JSON.")
    parser.add_argument("--allow-model", action="store_true")
    parser.add_argument("--binary")
    parser.add_argument("--primary")
    parser.add_argument("--critic")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args(argv)
    try:
        result = run_request(read_request(args.request), allow_model=args.allow_model,
                             binary=args.binary, primary=args.primary, critic=args.critic,
                             timeout=args.timeout)
    except (OSError, ValueError, TypeError, RecursionError):
        print("Invalid local finance request; no result.", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
