"""Generate a private, unscored two-run Astra phone evaluation template.

The output is intentionally unverified. Never commit a completed owner record.
"""
import argparse
import json
from pathlib import Path

MANIFEST = Path(__file__).with_name("scorecard_cases.json")


def blank_record(manifest):
    ids = [case["id"] for case in manifest["cases"]]
    if len(ids) != 30 or len(set(ids)) != 30 or sum(c["points"] for c in manifest["cases"]) != 100:
        raise ValueError("Invalid scorecard manifest")
    return {
        "runs": [
            {
                "evidence": {
                    "commit": "",
                    "android_version": "",
                    "model_sha256": "",
                    "airplane_mode": False,
                    "wifi_disabled": False,
                    "owner_signed_upgrade": False
                },
                "cases": {case_id: {"status": "unverified"} for case_id in ids}
            }
            for _ in range(2)
        ]
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="Private output path; never commit")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Refusing to overwrite an existing evidence record")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    payload = json.dumps(blank_record(manifest), indent=2) + "\n"
    args.output.open("x", encoding="utf-8").write(payload)
    print("Created unverified local template. No quality score awarded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
