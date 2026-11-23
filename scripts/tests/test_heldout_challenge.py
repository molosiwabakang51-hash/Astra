"""Regression guard for the supplemental blind generalization challenge.

These tests validate only protocol integrity, never model performance.
"""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "astra_heldout_validator", ROOT / "scripts" / "quality" / "validate_heldout_challenge.py"
)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class HeldoutChallengeTests(unittest.TestCase):
    def setUp(self):
        self.document = (ROOT / "docs" / "intelligence-heldout-challenge.md").read_text(encoding="utf-8")

    def test_current_challenge_structure(self):
        self.assertEqual([], module.validate(self.document))

    def test_missing_case_is_rejected(self):
        self.assertTrue(module.validate(self.document.replace("| H07 |", "| H77 |", 1)))

    def test_duplicate_case_is_rejected(self):
        self.assertTrue(module.validate(self.document.replace("| H08 |", "| H07 |", 1)))

    def test_missing_score_boundary_is_rejected(self):
        self.assertTrue(module.validate(self.document.replace("maximum **24**", "maximum **25**", 1)))

    def test_tool_and_model_distinction_is_required(self):
        self.assertTrue(module.validate(self.document.replace("tool-assisted", "tool aided", 1)))


if __name__ == "__main__":
    unittest.main()
