"""Regression tests for bounded Android Qwen3 context and batch allocations."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "reduce_native_context_memory.py"
spec = importlib.util.spec_from_file_location("reduce_native_context_memory", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class NativeContextMemoryTest(unittest.TestCase):
    def fixture(self):
        return (
            "const int dummy = 1;\n"
            + module.OLD_CONTEXT + "\n"
            + module.OLD_BATCH + "\n"
            "auto use = DEFAULT_CONTEXT_SIZE - BATCH_SIZE;\n"
        )

    def test_reduces_context_and_batch(self):
        result = module.hardened(self.fixture())
        self.assertIn(module.NEW_CONTEXT, result)
        self.assertIn(module.NEW_BATCH, result)
        self.assertNotIn(module.OLD_CONTEXT, result)
        self.assertNotIn(module.OLD_BATCH, result)

    def test_idempotent(self):
        changed = module.hardened(self.fixture())
        self.assertEqual(changed, module.hardened(changed))

    def test_rejects_source_drift_or_duplicated_defaults(self):
        for source in (
            self.fixture().replace("8192", "16384"),
            self.fixture().replace("512", "1024"),
            self.fixture() + module.OLD_CONTEXT,
        ):
            with self.subTest(source=source[-50:]):
                with self.assertRaises(RuntimeError):
                    module.hardened(source)

    def test_verify_mode_refuses_to_modify_unpatched_native_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / module.CPP
            path.parent.mkdir(parents=True)
            path.write_text(self.fixture(), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "not been applied"):
                module.apply(Path(tmp), verify_only=True)
            self.assertEqual(self.fixture(), path.read_text(encoding="utf-8"))
            module.apply(Path(tmp))
            module.apply(Path(tmp), verify_only=True)

    def test_build_preparation_calls_memory_patch(self):
        text = (Path(__file__).resolve().parents[1] /
                "prepare_offline_android.py").read_text(encoding="utf-8")
        self.assertIn("patch_native_memory(UPSTREAM)", text)


if __name__ == "__main__":
    unittest.main()
