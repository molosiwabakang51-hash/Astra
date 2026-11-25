"""Validate ACIE v1 independent review records. Does not execute or grade Qwen."""
import argparse
import json
import re
from pathlib import Path

MODEL = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
DIMENSIONS = {
    "logic": (40, 18), "semantic": (30, 14), "factual": (30, 14),
    "context": (30, 14), "unfamiliar": (20, 9), "quantitative": (20, 9),
    "verification": (20, 8), "creativity": (10, 4),
}
TRACKS = {(form, track) for form in ("A", "B") for track in ("model", "assisted")}
ATTEST = ("physical_device", "airplane_mode", "wifi_disabled", "independent_review",
          "private_fresh_variants", "rubrics_frozen_before_run", "no_training_on_variants",
          "restarted_before_run", "no_external_ai")
CONFIG = ("commit", "apk_sha256", "model_sha256", "policy_sha256", "context_tokens", "device_label")
HEX = re.compile(r"[0-9a-f]{64}\Z")
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
OPAQUE = re.compile(r"[A-Za-z0-9_-]{1,80}\Z")


def incomplete(reason):
    return {"status": "INCOMPLETE", "reason": reason, "scores": None,
            "target_met": False, "release_ready": False}


def valid_config(config):
    return (isinstance(config, dict) and set(config) == set(CONFIG) and
            isinstance(config["commit"], str) and COMMIT.fullmatch(config["commit"]) and
            config["model_sha256"] == MODEL and
            all(isinstance(config[k], str) and HEX.fullmatch(config[k])
                for k in ("apk_sha256", "policy_sha256")) and
            type(config["context_tokens"]) is int and 1 <= config["context_tokens"] <= 32768 and
            isinstance(config["device_label"], str) and OPAQUE.fullmatch(config["device_label"]))


def evaluate(record):
    if not isinstance(record, dict) or record.get("protocol") != "ACIE-v1":
        return incomplete("protocol")
    runs = record.get("runs")
    if not isinstance(runs, list) or len(runs) != 4:
        return incomplete("four runs required")
    seen_runs, variants, evidence = set(), set(), set()
    results, reference = {}, None
    for run in runs:
        if not isinstance(run, dict):
            return incomplete("malformed run")
        form, track = run.get("form"), run.get("track")
        if not isinstance(form, str) or not isinstance(track, str):
            return incomplete("invalid form or track")
        key = (form, track)
        if key not in TRACKS or key in seen_runs:
            return incomplete("duplicate or invalid form/track")
        seen_runs.add(key)
        config = run.get("configuration")
        if not valid_config(config) or (reference is not None and config != reference):
            return incomplete("configuration mismatch")
        reference = config
        if any(run.get(field) is not True for field in ATTEST):
            return incomplete("missing attestation")
        cases = run.get("cases")
        if not isinstance(cases, list) or len(cases) != 200:
            return incomplete("exactly 200 cases required")
        counts = dict.fromkeys(DIMENSIONS, 0)
        credits = dict.fromkeys(DIMENSIONS, 0)
        failed = 0
        for case in cases:
            if not isinstance(case, dict):
                return incomplete("malformed case")
            dim = case.get("dimension")
            if not isinstance(dim, str) or dim not in DIMENSIONS:
                return incomplete("invalid dimension")
            points = case.get("credits")
            if type(points) is not int or points not in (0, 1, 2) or case.get("reviewed") is not True:
                return incomplete("invalid review")
            # Failed inference stays in the denominator as zero. Completed
            # model-only must be genuinely neural and use no assistance.
            outcome = case.get("outcome")
            if outcome not in ("complete", "failed") or (outcome == "failed" and points != 0):
                return incomplete("invalid outcome")
            if case.get("track") != track or type(case.get("native_generated")) is not bool:
                return incomplete("invalid route")
            if outcome == "complete" and case["native_generated"] is not True:
                return incomplete("non-neural completion")
            if track == "model" and case.get("assisted") is not False:
                return incomplete("model-only contamination")
            if track == "assisted" and type(case.get("assisted")) is not bool:
                return incomplete("missing assistance declaration")
            for field, seen in (("variant_id", variants), ("evidence_id", evidence)):
                ident = case.get(field)
                if not isinstance(ident, str) or not OPAQUE.fullmatch(ident) or ident in seen:
                    return incomplete("invalid or duplicate evidence")
                seen.add(ident)
            elapsed, ram = case.get("elapsed_ms"), case.get("peak_ram_bytes")
            if type(elapsed) is not int or elapsed < 0 or type(ram) is not int or ram <= 0:
                return incomplete("missing resource evidence")
            counts[dim] += 1
            credits[dim] += points
            failed += outcome == "failed"
        if any(counts[d] != allocation[0] for d, allocation in DIMENSIONS.items()):
            return incomplete("dimension allocation mismatch")
        scores = {d: credits[d] / 4 for d in DIMENSIONS}
        total = sum(scores.values())
        results[form + ":" + track] = {"score": total, "dimensions": scores,
                                       "failed": failed, "meets_floors": all(
                                           scores[d] >= floor for d, (_, floor) in DIMENSIONS.items())}
    target = all(x["score"] >= 90 and x["meets_floors"] for x in results.values())
    return {"status": "REVIEWED_ATTESTATIONS", "scores": results,
            "model_only_min": min(results[f + ":model"]["score"] for f in ("A", "B")),
            "assisted_min": min(results[f + ":assisted"]["score"] for f in ("A", "B")),
            "target_met": target, "release_ready": False,
            "note": "Attestations only. Device, independence, rubric quality and outcomes not authenticated."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", type=Path)
    args = parser.parse_args()
    try:
        result = evaluate(json.loads(args.record.read_text(encoding="utf-8")))
    except (OSError, ValueError, RecursionError):
        result = incomplete("unreadable review record")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "REVIEWED_ATTESTATIONS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
