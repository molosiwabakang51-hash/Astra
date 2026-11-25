"""Native benchmark execution with local configuration fingerprints.

Transfer evaluation is standalone. Direct development-suite history recording
requires the equal-call benchmark and history modules. Store only completed
reports after explicit approval; no private prompts or answers are retained.
"""
import argparse
import hashlib
import importlib
import json
import os
from pathlib import Path
import platform
import secrets
import sys

import astra_generated_validation as transfer
import astra_local_answer as native
import astra_resource_plan as resources
import astra_symbiotic_eval as parsing
from astra_local_finance import file_digest


def fingerprint(binary, primary, critic, timeout, suite_module):
    paths = tuple(Path(p).resolve() for p in (binary, primary, critic))
    primary_sha, critic_sha = file_digest(paths[1]), file_digest(paths[2])
    if primary_sha == critic_sha:
        raise ValueError("Distinct local model weights required.")
    # No hostname, username, serial number or raw model path in the report.
    profile = {
        "protocol": "native-benchmark-v1",
        "binary_sha": file_digest(paths[0]),
        "source_sha": {
            name: file_digest(module.__file__) for name, module in (
                ("native", native), ("resources", resources), ("parsing", parsing),
                ("suite", suite_module),
            )
        },
        "timeout": timeout,
        "context_tokens": native.CONTEXT,
        "generation_tokens": native.GENERATE_TOKENS,
        "cpu_count": os.cpu_count() or 1,
        "machine": platform.machine(),
        "processor": platform.processor(),
        "system": platform.system(),
        "python": platform.python_version(),
    }
    digest = hashlib.sha256(json.dumps(profile, sort_keys=True,
                                      separators=(",", ":")).encode("utf-8")).hexdigest()
    return primary_sha, critic_sha, digest


def run(binary, primary, critic, *, suite="transfer", seed=None, timeout=180,
        approved=False, database=None, approve_storage=False):
    if approved is not True:
        raise ValueError("Explicit approval for local native evaluation required.")
    if suite not in ("transfer", "development"):
        raise ValueError("Unsupported benchmark suite.")
    if type(timeout) is not int or not 30 <= timeout <= 300:
        raise ValueError("Invalid native timeout.")
    if database is not None and (suite != "development" or approve_storage is not True):
        raise ValueError("Only approved development reports can be recorded to history.")
    if approve_storage and database is None:
        raise ValueError("An explicit history database is required.")
    if suite == "development" and seed is not None:
        raise ValueError("The fixed development suite does not use a seed.")

    if suite == "transfer":
        module = transfer
    else:
        # Optional integration with independently submitted benchmark/history
        # modules; refuse the request before inference if either is absent.
        try:
            module = importlib.import_module("astra_equal_call_eval")
            history_module = importlib.import_module("astra_benchmark_history") if database is not None else None
        except ImportError:
            raise RuntimeError("Development benchmark/history integration unavailable.") from None

    runner = native.LocalAnswerRunner(binary, primary, critic, timeout=timeout, approved=True)
    before = fingerprint(binary, primary, critic, timeout, module)
    if suite == "transfer":
        # Generate a new local seed only after recording configuration identity.
        chosen_seed = secrets.token_hex(16) if seed is None else seed
        result = module.evaluate(chosen_seed, runner, approved=True)
    else:
        result = module.evaluate(runner, approved=True)

    after = fingerprint(binary, primary, critic, timeout, module)
    if before != after:
        raise RuntimeError("Runtime configuration changed during evaluation.")
    primary_sha, critic_sha, configuration_sha = before
    result["identity"] = {"primary_sha": primary_sha, "critic_sha": critic_sha,
                          "configuration_sha": configuration_sha,
                          "scope": "Local file and configuration fingerprints; not authenticated runtime attestation."}
    if suite == "transfer":
        result["reproduction_seed"] = chosen_seed
    if database is not None:
        # A failed or incomplete evaluation never opens the database.
        with history_module.BenchmarkHistory(database) as history:
            result["history_receipt"] = history.record(
                result, primary_sha, critic_sha, configuration_sha, approved=True)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary")
    parser.add_argument("primary")
    parser.add_argument("critic")
    parser.add_argument("--suite", choices=("transfer", "development"), default="transfer")
    parser.add_argument("--seed-file")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--database")
    parser.add_argument("--approve-models", action="store_true")
    parser.add_argument("--approve-storage", action="store_true")
    args = parser.parse_args(argv)
    try:
        seed = None
        if args.seed_file:
            with Path(args.seed_file).open("rb") as source:
                raw = source.read(65)
            if len(raw) > 64:
                raise ValueError("Seed file outside limit.")
            seed = raw.decode("ascii", errors="strict").strip()
        result = run(args.binary, args.primary, args.critic, suite=args.suite, seed=seed,
                     timeout=args.timeout, approved=args.approve_models,
                     database=args.database, approve_storage=args.approve_storage)
        print(json.dumps(result, indent=2, allow_nan=False))
        return 0
    except (OSError, ValueError, RuntimeError, RecursionError):
        print("Native benchmark incomplete or unavailable; no score.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
