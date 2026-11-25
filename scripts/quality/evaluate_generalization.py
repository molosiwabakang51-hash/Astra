"""Validate private independent generalization reviews; never run or train the model.

Public H01-H12 examples define dimensions, not secret held-out questions.
Reviewers author fresh equivalent variants outside the repository.
"""
import argparse
import json
import re
from pathlib import Path

MODEL = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
CASES = {f"H{i:02}": ("retrieval" if i == 8 else "model") for i in range(1, 13)}
CONFIG = ("commit", "apk_sha256", "device_label", "android_version",
          "model_sha256", "prompt_revision", "context_tokens", "mode",
          "sampling_revision")
ATTESTATIONS = ("physical_device", "airplane_mode", "wifi_disabled",
                "independent_review", "fresh_private_variants",
                "no_training_on_variants", "restart_before_run")
LABEL = re.compile(r"[A-Za-z0-9_-]{1,80}\Z")
HEX = re.compile(r"[0-9a-fA-F]{64}\Z")


def evaluate(record):
    errors = []
    totals = []
    seen = set()
    runs = record.get("runs") if isinstance(record, dict) else None
    if not isinstance(runs, list) or len(runs) != 2:
        return {"status": "INCOMPLETE", "errors": ["Two independently reviewed phone runs required"],
                "generalization_score": None, "target_met": False}
    first = None
    for n, run in enumerate(runs, 1):
        if not isinstance(run, dict):
            errors.append(f"Run {n}: malformed")
            continue
        config = run.get("configuration")
        if not isinstance(config, dict):
            errors.append(f"Run {n}: missing configuration")
            continue
        if first is None:
            first = config
        elif any(config.get(k) != first.get(k) for k in CONFIG):
            errors.append(f"Run {n}: configuration changed")
        for key in CONFIG:
            value = config.get(key)
            if key == "context_tokens":
                ok = type(value) is int and value > 0
            elif key == "model_sha256":
                ok = value == MODEL
            elif key == "apk_sha256":
                ok = isinstance(value, str) and HEX.fullmatch(value) is not None
            elif key == "mode":
                ok = value in ("standard", "adaptive", "think")
            else:
                ok = isinstance(value, str) and LABEL.fullmatch(value) is not None
            if not ok:
                errors.append(f"Run {n}: invalid {key}")
        for key in ATTESTATIONS:
            if run.get(key) is not True:
                errors.append(f"Run {n}: missing attestation {key}")
        cases = run.get("cases")
        if not isinstance(cases, dict) or set(cases) != set(CASES):
            errors.append(f"Run {n}: all 12 case IDs required")
            continue
        points = {"model": 0, "retrieval": 0}
        for case, mode in CASES.items():
            item = cases[case]
            if not isinstance(item, dict):
                errors.append(f"Run {n}: {case} malformed")
                continue
            score = item.get("points")
            if type(score) is not int or score not in (0, 1, 2):
                errors.append(f"Run {n}: {case} requires reviewed 0/1/2 points")
                continue
            if item.get("route") != mode or item.get("native_generated") is not True:
                errors.append(f"Run {n}: {case} wrong inference route")
            for key in ("variant_id", "evidence_id"):
                label = item.get(key)
                if not isinstance(label, str) or not LABEL.fullmatch(label):
                    errors.append(f"Run {n}: {case} invalid {key}")
                elif key == "variant_id":
                    if label in seen:
                        errors.append(f"Run {n}: {case} reused held-out variant")
                    seen.add(label)
            if item.get("reviewed") is not True:
                errors.append(f"Run {n}: {case} not independently reviewed")
            points[mode] += score
        totals.append({"model_only": points["model"], "retrieval": points["retrieval"],
                       "total": sum(points.values())})
    if errors:
        return {"status": "INCOMPLETE", "errors": errors,
                "generalization_score": None, "target_met": False}
    minimum = min(t["total"] for t in totals)
    # Keep model-only performance separately visible, not obscured by retrieval.
    model_min = min(t["model_only"] for t in totals)
    return {"status": "REVIEWED", "runs": totals, "generalization_score": minimum,
            "maximum": 24, "model_only_minimum": model_min, "model_only_maximum": 22,
            "target_met": minimum >= 20 and model_min >= 18 and
                          all(t["retrieval"] == 2 for t in totals),
            "official_ai_rating": None, "stable_release": False,
            "note": "Attestation validation only; no hardware or reviewer identity verified."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("record", type=Path, help="Private local review record, never commit")
    args = parser.parse_args()
    try:
        result = evaluate(json.loads(args.record.read_text(encoding="utf-8")))
    except (OSError, ValueError):
        parser.exit(2, "Cannot read private review record; no score awarded.\n")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "REVIEWED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
