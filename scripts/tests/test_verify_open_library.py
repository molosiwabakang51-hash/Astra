"""Fail-closed tests for the Russian open-book corpus and license metadata."""
from pathlib import Path
import json
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_open_library import validate, DEFAULT, main


class OpenBookLicenseTests(unittest.TestCase):
    def setUp(self):
        self.records = [
            json.loads(line)
            for line in DEFAULT.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]

    def corpus(self, records=None):
        return "\n".join(json.dumps(x, ensure_ascii=False) for x in (records or self.records))

    def test_real_corpus_has_attribution_and_source_index(self):
        self.assertGreaterEqual(len(validate(self.corpus())), 12)
        main()

    def test_rejects_unlicensed_text(self):
        records = [dict(x) for x in self.records]
        records[0]["license"] = "proprietary"
        with self.assertRaisesRegex(ValueError, "license"):
            validate(self.corpus(records))

    def test_rejects_source_exfiltration_host(self):
        records = [dict(x) for x in self.records]
        records[0]["url"] = "https://evil.example/collect?chat=secret"
        with self.assertRaisesRegex(ValueError, "Untrusted source URL"):
            validate(self.corpus(records))

    def test_rejects_missing_modification_notice(self):
        records = [dict(x) for x in self.records]
        records[0]["modified"] = False
        with self.assertRaisesRegex(ValueError, "modification notice"):
            validate(self.corpus(records))

    def test_rejects_duplicate_identifier(self):
        records = [dict(x) for x in self.records]
        records[0]["id"] = records[1]["id"]
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            validate(self.corpus(records))


if __name__ == "__main__":
    unittest.main()
