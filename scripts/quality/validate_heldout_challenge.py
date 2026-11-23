"""Validate the structure of Astra's supplementary held-out challenge.

This script does not run Qwen, score a phone, or certify intelligence.
"""
from pathlib import Path
import re

DOC = Path(__file__).resolve().parents[2] / "docs" / "intelligence-heldout-challenge.md"
EXPECTED = [f"H{i:02d}" for i in range(1, 13)]


def validate(text: str) -> list[str]:
    errors = []
    rows = re.findall(r"^\| (H\d{2}) \|.*$", text, re.MULTILINE)
    if rows != EXPECTED:
        errors.append(f"Expected exactly {EXPECTED}, found {rows}")
    if "0, 1 or 2" not in text or "maximum **24**" not in text:
        errors.append("Missing explicitly bounded 0–2 rubric / 24-point total")
    for phrase in ("model-only", "tool-assisted", "retrieval-grounded", "airplane mode", "two"):
        if phrase.casefold() not in text.casefold():
            errors.append(f"Missing protocol safeguard: {phrase}")
    if "not a replacement score" not in text:
        errors.append("Must explicitly prohibit replacing official score")
    return errors


if __name__ == "__main__":
    issues = validate(DOC.read_text(encoding="utf-8"))
    if issues:
        raise SystemExit("\n".join(issues))
    print("Supplementary challenge structure: PASS (no device score awarded)")
