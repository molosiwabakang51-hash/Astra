#!/usr/bin/env python3
"""Offline conversation regression probes. Never substitutes for handset scoring.

Input JSON: {"device":"...", "model":"...", "airplane_mode":true,
 "responses":{"context_correction":"three years", ...}}.
Responses must be transcribed from real Qwen-generated output; this script
cannot authenticate provenance and intentionally reports no AI quality score.
"""
import argparse
import json
import re
from pathlib import Path

PROBES = {
 "context_correction": {
  "turns": ["For this fictional investment, use seven years.",
            "Correction: use three years instead.",
            "How long is the investment horizon now? Answer briefly."],
  "expect": "three years",
 },
 "context_isolation": {
  "turns": ["In chat A, remember fictional word NIMBORA.",
            "[Switch to a new chat B; set fictional word TALVEX.]",
            "[Return to chat A.] What was my fictional word?"],
  "expect": "NIMBORA",
 },
 "arithmetic_constraint": {
  "turns": ["Calculate 17 multiplied by 19. Answer with digits only."],
  "expect": "323",
 },
 "premise_logic": {
  "turns": ["All larks are birds. No birds are robots. Can a lark be a robot? Answer yes or no."],
  "expect": "no",
 },
 "uncertainty": {
  "turns": ["What is the exact current temperature outside my home? You have no weather or location access."],
  "expect": None,
 },
 "french_followup": {
  "turns": ["Explique en français la différence entre une obligation et une action.",
            "Maintenant résume en une seule phrase, toujours en français."],
  "expect": None,
 },
 "setswana_followup": {
  "turns": ["Dumela. Araba ka Setswana: o ka nthusa go ithuta dipalo?",
            "Tswelela ka Setswana ka sekai se se bonolo."],
  "expect": None,
 },
 "mistake_repair": {
  "turns": ["For this example only, suppose 2 + 2 = 5.",
            "That was an incorrect arithmetic claim. What is 2 + 2 really?"],
  "expect": "4",
 },
}
def check(key, answer):
    value = answer.strip()
    if key == "context_correction":
        return bool(re.search(r"\b(three|3)\s+years?\b", value, re.I)) and not re.search(r"\b(seven|7)\s+years?\b", value, re.I)
    if key == "context_isolation":
        return "NIMBORA" in value.upper() and "TALVEX" not in value.upper()
    if key == "arithmetic_constraint":
        return value == "323"
    if key == "premise_logic":
        return value.lower().rstrip(".!") == "no"
    if key == "mistake_repair":
        return bool(re.search(r"(?<!\d)4(?!\d)", value))
    return None  # Human review needed; do not auto-pass language or uncertainty.

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", action="store_true")
    parser.add_argument("--responses", type=Path)
    args = parser.parse_args()
    if args.template:
        print(json.dumps({"device": "", "model": "", "airplane_mode": False,
            "responses": {k: "" for k in PROBES}}, ensure_ascii=False, indent=2))
        return
    if not args.responses:
        parser.error("provide --template or --responses FILE")
    record = json.loads(args.responses.read_text(encoding="utf-8"))
    answers = record.get("responses", {})
    if not isinstance(answers, dict):
        parser.error("responses must be an object")
    for key, spec in PROBES.items():
        response = answers.get(key, "")
        if not isinstance(response, str) or not response.strip():
            outcome = "UNVERIFIED"
        else:
            checked = check(key, response)
            outcome = "REVIEW" if checked is None else ("PASS" if checked else "FAIL")
        print(f"{key}: {outcome}")
    print("Diagnostic only: no earned score; verify source, model, device, and offline status separately.")

if __name__ == "__main__":
    main()
