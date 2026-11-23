"""Summarize locally collected, content-free Astra inference timings.

Input CSV columns: run_id,mode,first_output_ms,total_ms,completed,anr
No prompts, responses, paths or identifiers are accepted. This is diagnostic
instrumentation, not a model-quality score or proof of ANR root cause.
"""
import csv
import math
import statistics
import sys
from pathlib import Path

FIELDS = ("run_id", "mode", "first_output_ms", "total_ms", "completed", "anr")
MODES = {"cold", "warm"}
BOOLS = {"0", "1"}


def percentile(values, pct):
    ordered = sorted(values)
    if not ordered:
        return None
    position = (len(ordered) - 1) * pct
    low = math.floor(position)
    high = math.ceil(position)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def summarize(rows):
    result = {}
    for mode in ("cold", "warm"):
        group = [r for r in rows if r["mode"] == mode]
        successful = [r for r in group if r["completed"] == "1" and r["anr"] == "0"]
        result[mode] = {
            "attempts": len(group),
            "successful": len(successful),
            "blank_or_failed": sum(r["completed"] == "0" for r in group),
            "anr": sum(r["anr"] == "1" for r in group),
            "first_output_p50_ms": percentile([r["first_output_ms"] for r in successful], .5),
            "first_output_p95_ms": percentile([r["first_output_ms"] for r in successful], .95),
            "total_p50_ms": percentile([r["total_ms"] for r in successful], .5),
            "total_p95_ms": percentile([r["total_ms"] for r in successful], .95),
        }
    return result


def parse(path):
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if tuple(reader.fieldnames or ()) != FIELDS:
            raise ValueError("Unexpected columns; only content-free timing fields are allowed")
        rows = []
        for row in reader:
            if None in row or any(v is None for v in row.values()):
                raise ValueError("Malformed CSV row")
            if row["mode"] not in MODES or row["completed"] not in BOOLS or row["anr"] not in BOOLS:
                raise ValueError("Invalid mode or flag")
            if not row["run_id"].isdigit() or len(row["run_id"]) > 6:
                raise ValueError("run_id must be a short numeric sequence")
            for key in ("first_output_ms", "total_ms"):
                value = row[key]
                if not value.isdigit() or len(value) > 9:
                    raise ValueError("Timing must be a nonnegative integer in milliseconds")
                row[key] = int(value)
            if row["first_output_ms"] > row["total_ms"]:
                raise ValueError("First output cannot occur after request completion")
            rows.append(row)
            if len(rows) > 10000:
                raise ValueError("Too many records")
    if not rows:
        raise ValueError("At least one measurement required")
    return rows


def main(argv):
    if len(argv) != 2:
        raise SystemExit("Usage: python3 scripts/quality/analyze_inference_timings.py LOCAL_TIMINGS.csv")
    summary = summarize(parse(Path(argv[1])))
    for mode, metrics in summary.items():
        print(mode.upper())
        for key, value in metrics.items():
            print(f"  {key}: {value if value is not None else 'UNVERIFIED'}")


if __name__ == "__main__":
    main(sys.argv)
