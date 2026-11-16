#!/usr/bin/env python3
"""Score only recorded, complete real-phone Astra evaluations. No network/deps."""
import argparse
import json
import math
import sys
from pathlib import Path

WEIGHTS = {"A": (5, 5), "B": (5, 4), "C": (5, 3),
           "D": (5, 3), "E": (5, 3), "F": (5, 2)}
MANDATORY = ("F1", "F4", "F5")
REQUIRED_METADATA = ("date", "commit", "device", "android", "model",
                     "model_sha256", "airplane_mode", "wifi_disabled",
                     "time_to_first_token_ms", "response_time_ms",
                     "ram_peak_mib", "battery_drain_percent")


def evaluate(document):
    if not isinstance(document, dict) or not isinstance(document.get("runs"), list):
        raise ValueError("Expected an object with a runs array")
    results = []
    for index, run in enumerate(document["runs"], 1):
        if not isinstance(run, dict):
            raise ValueError(f"Run {index} must be an object")
        metadata = run.get("metadata")
        cases = run.get("cases")
        if not isinstance(metadata, dict) or not isinstance(cases, dict):
            raise ValueError(f"Run {index}: metadata and cases objects required")
        expected = {f"{domain}{n}": points for domain, (count, points) in WEIGHTS.items()
                    for n in range(1, count + 1)}
        unknown = set(cases) - set(expected)
        if unknown:
            raise ValueError(f"Run {index}: unknown cases: {sorted(unknown)}")
        if any(value not in ("pass", "fail", "unverified") for value in cases.values()):
            raise ValueError(f"Run {index}: cases must be pass, fail or unverified")
        missing = sorted(set(expected) - set(cases))
        unverified = sorted(key for key, value in cases.items() if value == "unverified")
        absent_metadata = [key for key in REQUIRED_METADATA
                           if metadata.get(key) is None or metadata.get(key) == ""]
        for key in ("airplane_mode", "wifi_disabled"):
            if key in metadata and type(metadata[key]) is not bool:
                raise ValueError(f"Run {index}: {key} must be a boolean")
        # Reject physically impossible or non-numeric telemetry. Missing
        # metrics already make a run unverified; invalid supplied metrics
        # must not be converted into a perfect 10/10 phone score.
        for key in ("time_to_first_token_ms", "response_time_ms",
                    "ram_peak_mib", "battery_drain_percent"):
            value = metadata.get(key)
            if value is None or value == "":
                continue
            if type(value) not in (int, float) or not math.isfinite(value):
                raise ValueError(f"Run {index}: {key} must be a finite number")
            if value < 0 or (key == "ram_peak_mib" and value == 0):
                raise ValueError(f"Run {index}: {key} is physically invalid")
            if key == "battery_drain_percent" and value > 100:
                raise ValueError(f"Run {index}: battery drain exceeds 100%")
        if (type(metadata.get("time_to_first_token_ms")) in (int, float)
                and type(metadata.get("response_time_ms")) in (int, float)
                and metadata["time_to_first_token_ms"] > metadata["response_time_ms"]):
            raise ValueError(f"Run {index}: first token arrives after response ends")
        offline = metadata.get("airplane_mode") is True and metadata.get("wifi_disabled") is True
        complete = not missing and not unverified and not absent_metadata and offline
        points = sum(expected[key] for key, value in cases.items() if value == "pass")
        gates = all(cases.get(key) == "pass" for key in MANDATORY)
        results.append({"run": index, "verified": complete,
                        "points": points if complete else None,
                        "score_out_of_10": points / 10 if complete else None,
                        "mandatory_gates_pass": gates if complete else None,
                        "missing_cases": missing, "unverified_cases": unverified,
                        "missing_metadata": absent_metadata,
                        "offline_confirmed": offline})
    target = (len(results) >= 2 and all(
        item["verified"] and item["points"] >= 85 and item["mandatory_gates_pass"]
        for item in results[-2:]))
    return {"runs": results, "target_8_5_verified": target,
            "note": "Phone evidence must be independently reviewed; this validates records, not authenticity."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    try:
        report = evaluate(json.loads(args.evidence.read_text(encoding="utf-8")))
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(f"Invalid scorecard: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
