"""Offline-only, privacy-minimal validator for manually reviewed Astra handset tests.

No transcripts, names, paths or identifiers are required or printed.
An incomplete or invalid record never receives a published numeric rating.
"""
import argparse
import json
from pathlib import Path

MANIFEST = Path(__file__).with_name("scorecard_cases.json")
STATES = {"pass", "fail", "unverified"}
REQUIRED = ("commit", "android_version", "model_sha256", "airplane_mode", "wifi_disabled", "owner_signed_upgrade")
GATES = {"F1", "F4", "F5"}


def evaluate(record, manifest=None):
    manifest = manifest or json.loads(MANIFEST.read_text(encoding="utf-8"))
    cases = manifest["cases"]
    ids = {item["id"] for item in cases}
    errors = []
    if not isinstance(record, dict):
        return {"status": "INVALID", "errors": ["Record must be an object"], "rating": None}
    runs = record.get("runs")
    if not isinstance(runs, list) or len(runs) != 2:
        return {"status": "INCOMPLETE", "errors": ["Two independent phone runs required"], "rating": None}
    scores = []
    # The 8.5/10 campaign requires two runs on the SAME build, model and
    # Android configuration. A better result after changing the model/build
    # cannot silently replace a repeatability check.
    first_meta = runs[0].get("evidence", {}) if isinstance(runs[0], dict) else {}
    second_meta = runs[1].get("evidence", {}) if isinstance(runs[1], dict) else {}
    if isinstance(first_meta, dict) and isinstance(second_meta, dict):
        for field in ("commit", "android_version", "model_sha256"):
            if first_meta.get(field) != second_meta.get(field):
                errors.append(f"Run configuration mismatch: {field} differs between runs")
    for n, run in enumerate(runs, 1):
        if not isinstance(run, dict):
            errors.append(f"Run {n}: expected an object")
            continue
        meta = run.get("evidence", {})
        if not isinstance(meta, dict):
            errors.append(f"Run {n}: evidence must be an object")
            meta = {}
        for field in REQUIRED:
            value = meta.get(field)
            if field in ("airplane_mode", "wifi_disabled", "owner_signed_upgrade"):
                if value is not True:
                    errors.append(f"Run {n}: {field} must be verified true")
            elif not isinstance(value, str) or not value.strip():
                errors.append(f"Run {n}: missing {field}")
        sha = meta.get("model_sha256")
        if isinstance(sha, str) and (len(sha) != 64 or any(x not in "0123456789abcdefABCDEF" for x in sha)):
            errors.append(f"Run {n}: model_sha256 must be a 64-character hex digest")
        results = run.get("cases")
        if not isinstance(results, dict):
            errors.append(f"Run {n}: missing per-case outcomes")
            continue
        if set(results) != ids:
            errors.append(f"Run {n}: missing or unexpected case IDs")
        for key, value in results.items():
            if not isinstance(value, dict) or value.get("status") not in STATES:
                errors.append(f"Run {n}: invalid outcome for {key}")
            elif value["status"] in {"pass", "fail"} and (
                not isinstance(value.get("evidence"), str) or not value["evidence"].strip()
            ):
                errors.append(f"Run {n}: {key} requires a nonempty local evidence reference")
        if any(isinstance(results.get(g), dict) and results[g].get("status") != "pass" for g in GATES):
            errors.append(f"Run {n}: mandatory F1/F4/F5 gate did not pass")
        if any(isinstance(v, dict) and v.get("status") == "unverified" for v in results.values()):
            errors.append(f"Run {n}: unverified cases remain")
        if set(results) == ids and all(isinstance(v, dict) and v.get("status") in STATES for v in results.values()):
            scores.append(sum(case["points"] for case in cases if results[case["id"]]["status"] == "pass"))
    if errors:
        return {"status": "INCOMPLETE", "errors": errors, "rating": None}
    if len(scores) != 2:
        return {"status": "INCOMPLETE", "errors": ["Both runs must be valid"], "rating": None}
    return {"status": "EVALUATED", "scores": scores, "rating": min(scores) / 10,
            "campaign_target_met": min(scores) >= 85,
            "stable_release": False,
            "note": "Handset results are owner-attested, not independently verified by this script."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", type=Path, help="Private local JSON evidence file; do not commit")
    args = parser.parse_args()
    try:
        result = evaluate(json.loads(args.record.read_text(encoding="utf-8")))
    except (OSError, ValueError) as exc:
        parser.exit(2, "Cannot read or parse evidence JSON; no score awarded.\n")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "EVALUATED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
