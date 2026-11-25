"""Hardware-independent tests for the desktop pilot."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("astra_resource_plan", ROOT / "desktop/astra_resource_plan.py")
P = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = P
SPEC.loader.exec_module(P)

class DesktopAdmissionTests(unittest.TestCase):
    def test_sequential_models_are_not_summed(self):
        resources = P.Resources(5 * P.GIB, 4, 100 * P.GIB)
        result = P.plan(resources, [("primary", 2 * P.GIB), ("critic", 2 * P.GIB)])
        self.assertTrue(result["all_admitted_individually"])
        self.assertEqual("sequential_cpu_only", result["mode"])
        self.assertEqual(2, result["threads"])

    def test_low_memory_fails_closed(self):
        result = P.plan(P.Resources(2 * P.GIB, 4, 10 * P.GIB), [("primary", P.GIB)])
        self.assertFalse(result["all_admitted_individually"])

    def test_context_and_inputs_are_bounded(self):
        resources = P.Resources(5 * P.GIB, 1, 10 * P.GIB)
        self.assertEqual(1, P.plan(resources, [("primary", P.GIB)])["threads"])
        for context in (0, 8192):
            with self.assertRaises(ValueError):
                P.plan(resources, [("primary", P.GIB)], context)
        with self.assertRaises(ValueError):
            P.plan(resources, [])
        with self.assertRaises(ValueError):
            P.plan(resources, [("bad", -1)])

    def test_header_validation_does_not_read_full_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.gguf"
            path.write_bytes(b"GGUF" + b"x" * 1024)
            self.assertEqual(("model.gguf", 1028), P.model_file(path))
            path.write_bytes(b"bad!" + b"x" * 1024)
            with self.assertRaises(ValueError):
                P.model_file(path)
            path.write_bytes(b"GGUF")
            with self.assertRaises(ValueError):
                P.model_file(path)

if __name__ == "__main__":
    unittest.main()
