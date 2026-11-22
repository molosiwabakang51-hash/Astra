"""Regression checks for diagnostic-only conversation probes."""
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "scripts" / "conversation_failure_probes.py"
spec = importlib.util.spec_from_file_location("conversation_failure_probes", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ProbeTests(unittest.TestCase):
    def test_corrected_horizon(self):
        self.assertTrue(module.check("context_correction", "Three years."))
        self.assertFalse(module.check("context_correction", "Seven years."))
        self.assertFalse(module.check("context_correction", "Three years, not seven years."))
    def test_cross_chat_leak(self):
        self.assertTrue(module.check("context_isolation", "NIMBORA"))
        self.assertFalse(module.check("context_isolation", "NIMBORA and TALVEX"))
    def test_strict_math(self):
        self.assertTrue(module.check("arithmetic_constraint", "323"))
        self.assertFalse(module.check("arithmetic_constraint", "Answer: 323"))
    def test_logic(self):
        self.assertTrue(module.check("premise_logic", "No."))
        self.assertFalse(module.check("premise_logic", "Yes"))
    def test_manual_cases_cannot_autopass(self):
        for key in ("uncertainty", "french_followup", "setswana_followup"):
            self.assertIsNone(module.check(key, "Looks fine"))

if __name__ == "__main__":
    unittest.main()
