"""Validate ACIE v1 independent review records. Does not execute or grade Qwen."""
import argparse
import hashlib
import hmac
import json
import math
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


def manifest_digest(manifest):
    """Canonical commitment to opaque identifiers and private rubric digests."""
    raw = json.dumps(manifest, sort_keys=True, separators=(",", ":"),
                     ensure_ascii=True, allow_nan=False).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def freeze_matches(record, manifest, expected_digest):
    """Verify binding to an externally retained commitment, not its timestamp."""
    if (not isinstance(expected_digest, str) or not HEX.fullmatch(expected_digest)
            or not isinstance(manifest, dict)
            or set(manifest) != {"protocol", "configuration", "runs"}
            or manifest["protocol"] != "ACIE-v1-freeze"
            or not valid_config(manifest["configuration"])):
        return False
    try:
        if not hmac.compare_digest(manifest_digest(manifest), expected_digest):
            return False
    except (ValueError, TypeError, RecursionError):
        return False
    planned = manifest["runs"]
    if not isinstance(planned, list) or len(planned) != 4:
        return False
    indexed, seen_variants, seen_rubrics = {}, set(), set()
    for run in planned:
        if not isinstance(run, dict) or set(run) != {"form", "track", "cases"}:
            return False
        if not isinstance(run["form"], str) or not isinstance(run["track"], str):
            return False
        key = (run["form"], run["track"])
        if key not in TRACKS or key in indexed:
            return False
        cases = run["cases"]
        if not isinstance(cases, list) or len(cases) != 200:
            return False
        by_id = {}
        for case in cases:
            if not isinstance(case, dict) or set(case) != {"variant_id", "dimension", "rubric_sha256"}:
                return False
            ident, dim, digest = case["variant_id"], case["dimension"], case["rubric_sha256"]
            if (not isinstance(ident, str) or not OPAQUE.fullmatch(ident)
                    or ident in seen_variants or not isinstance(dim, str) or dim not in DIMENSIONS
                    or not isinstance(digest, str) or not HEX.fullmatch(digest)
                    or digest in seen_rubrics):
                return False
            seen_variants.add(ident)
            seen_rubrics.add(digest)
            by_id[ident] = (dim, digest)
        indexed[key] = by_id
    for run in record["runs"]:
        if run["configuration"] != manifest["configuration"]:
            return False
        planned_cases = indexed[(run["form"], run["track"])]
        actual_cases = {c["variant_id"]: (c["dimension"], c.get("rubric_sha256")) for c in run["cases"]}
        if actual_cases != planned_cases:
            return False
    return True


def resource_summary(cases):
    """Descriptive nearest-rank percentiles; failures remain visible."""
    complete = [c for c in cases if c["outcome"] == "complete"]
    def latency(rows):
        if not rows:
            return {"p50_ms": None, "p95_ms": None}
        values = sorted(c["elapsed_ms"] for c in rows)
        def percentile(p):
            return values[(len(values) * p + 99) // 100 - 1]
        return {"p50_ms": percentile(50), "p95_ms": percentile(95)}
    return {
        "attempted": len(cases), "completed": len(complete),
        "failed": len(cases) - len(complete),
        "all_attempt_latency": latency(cases),
        "completed_latency": latency(complete),
        "max_reported_peak_ram_bytes": max(c["peak_ram_bytes"] for c in cases),
        "full_credit": sum(c["credits"] == 2 for c in cases),
        "partial_credit": sum(c["credits"] == 1 for c in cases),
        "zero_credit": sum(c["credits"] == 0 for c in cases),
        "assisted_cases": sum(c["assisted"] is True for c in cases),
    }


def evaluate(record, manifest=None, expected_freeze_sha256=None):
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
        results[form + ":" + track]["resources"] = resource_summary(cases)
        results[form + ":" + track]["by_dimension_resources"] = {
            d: resource_summary([c for c in cases if c["dimension"] == d])
            for d in DIMENSIONS}
    freeze_supplied = manifest is not None or expected_freeze_sha256 is not None
    if freeze_supplied and not freeze_matches(record, manifest, expected_freeze_sha256):
        return incomplete("freeze commitment or planned variants mismatch")
    target = all(x["score"] >= 90 and x["meets_floors"] for x in results.values())
    # Report the owner's provisional threshold separately. Do not weaken
    # the existing conservative 90-point acceptance rule or dimension floors.
    provisional_numeric = all(x["score"] >= 80 for x in results.values())
    form_spread = {track: abs(results["A:" + track]["score"] -
                             results["B:" + track]["score"])
                   for track in ("model", "assisted")}
    return {"status": "REVIEWED_ATTESTATIONS", "scores": results,
            "provisional_80_numeric_met": provisional_numeric,
            "form_score_spread_points": form_spread,
            "resource_note": "Nearest-rank descriptive latency, not a confidence interval. No first-token, token-rate, thermal or crash data inferred.",
            "model_only_min": min(results[f + ":model"]["score"] for f in ("A", "B")),
            "assisted_min": min(results[f + ":assisted"]["score"] for f in ("A", "B")),
            "target_met": target, "release_ready": False,
            "freeze_commitment_matched": freeze_supplied,
            "acceptance_record_eligible": target and freeze_supplied,
            "note": "Attestations only. Commitment match does not prove pre-run timing, device behavior, independence, rubric quality or outcomes."}



def read_private_json(path):
    """Reject ambiguous duplicate keys and nonfinite numbers at the file boundary."""
    def unique_object(pairs):
        obj = {}
        for key, value in pairs:
            if key in obj:
                raise ValueError("Duplicate JSON key")
            obj[key] = value
        return obj

    def reject_constant(_):
        raise ValueError("Nonfinite JSON constant")

    def finite_float(token):
        value = float(token)
        if not math.isfinite(value):
            raise ValueError("Nonfinite JSON number")
        return value

    # The protocol uses compact metadata only, not transcripts or raw answers.
    # Bound input before parsing, without logging private contents.
    with path.open("rb") as stream:
        raw = stream.read(4 * 1024 * 1024 + 1)
    if len(raw) > 4 * 1024 * 1024:
        raise ValueError("Private record exceeds metadata limit")
    return json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                      parse_constant=reject_constant, parse_float=finite_float)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", type=Path)
    parser.add_argument("--freeze-manifest", type=Path)
    parser.add_argument("--expected-freeze-sha256")
    args = parser.parse_args()
    try:
        manifest = read_private_json(args.freeze_manifest) if args.freeze_manifest else None
        result = evaluate(read_private_json(args.record), manifest, args.expected_freeze_sha256)
    except (OSError, ValueError, RecursionError):
        result = incomplete("unreadable review record")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "REVIEWED_ATTESTATIONS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
