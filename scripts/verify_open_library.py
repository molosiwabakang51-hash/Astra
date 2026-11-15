#!/usr/bin/env python3
"""Fail an Astra build if bundled open-book extracts lose license provenance."""
from __future__ import annotations

import json
import re
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / "android/app/src/main/assets/knowledge/open_russian.jsonl"
SOURCE_NOTICE = ROOT / "android/app/src/main/assets/knowledge/SOURCES.md"
ALLOWED = {"en.wikibooks.org", "en.wiktionary.org"}


def validate(raw: str) -> list[dict]:
    if len(raw.encode("utf-8")) > 80_000:
        raise ValueError("Knowledge corpus exceeds safe size bound.")
    entries: list[dict] = []
    ids: set[str] = set()
    for line in raw.splitlines():
        if not line.strip():
            continue
        if len(line) > 5_000:
            raise ValueError("Knowledge record exceeds line-length bound.")
        record = json.loads(line)
        identifier = record.get("id")
        if not isinstance(identifier, str) or not re.fullmatch(r"ru-[a-z-]{2,40}", identifier):
            raise ValueError("Invalid open-book entry ID.")
        if identifier in ids:
            raise ValueError("Duplicate knowledge entry ID.")
        ids.add(identifier)
        url = record.get("url", "")
        target = urlsplit(url)
        if (target.scheme != "https" or target.hostname not in ALLOWED or
            not target.path.startswith("/wiki/") or target.username or target.password or
            target.fragment or target.query):
            raise ValueError(f"Untrusted source URL: {url!r}")
        if target.hostname == "en.wikibooks.org" and not target.path.startswith("/wiki/Russian/"):
            raise ValueError("Wikibooks knowledge is restricted to Russian course pages.")
        if record.get("license") != "CC BY-SA 4.0":
            raise ValueError(f"Missing CC BY-SA 4.0 license for {identifier}.")
        if record.get("attribution") != "Wikimedia contributors":
            raise ValueError(f"Missing Wikimedia contributor attribution for {identifier}.")
        if record.get("modified") is not True:
            raise ValueError(f"Paraphrase/modification notice missing for {identifier}.")
        title, text = record.get("title"), record.get("text")
        if not isinstance(title, str) or not 5 <= len(title) <= 120:
            raise ValueError(f"Invalid title for {identifier}.")
        if not isinstance(text, str) or not 30 <= len(text) <= 1_500:
            raise ValueError(f"Invalid excerpt for {identifier}.")
        entries.append(record)
    if len(entries) < 12:
        raise ValueError("Russian offline reference pack has insufficient breadth.")
    return entries


def main() -> None:
    records = validate(DEFAULT.read_text(encoding="utf-8"))
    notices = SOURCE_NOTICE.read_text(encoding="utf-8")
    for record in records:
        if record["url"] not in notices:
            raise ValueError("Source attribution index missing: " + record["url"])
    if "https://creativecommons.org/licenses/by-sa/4.0/" not in notices:
        raise ValueError("CC BY-SA 4.0 license URL is absent from source notices.")
    print(f"PASS: {len(records)} attributed licensed Russian references, bounded and validated.")


if __name__ == "__main__":
    main()
