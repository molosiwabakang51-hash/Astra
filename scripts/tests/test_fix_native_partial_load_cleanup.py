"""A failed 1.7B JNI load must not strand partially allocated model weights."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_partial_load_cleanup import BEFORE, AFTER, IMPL, apply, hardened
from fix_native_unload_safety import (
    BEFORE_CONTEXT, AFTER_CONTEXT, BEFORE_UNLOAD, AFTER_UNLOAD,
    hardened as safe_unload,
)
from fix_native_prompt_error import hardened as original_error_guard

ROOT = Path(__file__).resolve().parents[2]


class NativePartialLoadCleanupTest(unittest.TestCase):
    @staticmethod
    def fixture():
        # The original Error handler keeps its guard; a new pre-load marker
        # must ensure even incomplete native model allocation is reclaimed.
        return (
            BEFORE + "\n"
            '                prepare().let {\n'
            '                    if (it != 0) throw IOException("Failed to prepare resources")\n'
            '                }\n'
            '                _nativeModelPrepared = true\n'
            '                Log.i(TAG, "Model loaded!")\n'
            '                is InferenceEngine.State.Error -> {\n'
            '                    if (_nativeModelPrepared) {\n'
            '                        unload()\n'
            '                        _nativeModelPrepared = false\n'
            '                    }\n'
            '                }\n'
        )

    def test_resource_ownership_is_marked_before_native_load(self):
        s = hardened(self.fixture())
        self.assertIn(AFTER, s)
        self.assertNotIn(BEFORE, s)
        self.assertEqual(s.count("_nativeModelPrepared = true"), 2)
        self.assertLess(s.index("_nativeModelPrepared = true"), s.index("load(pathToModel).let"))
        self.assertLess(s.index("load(pathToModel).let"), s.index("prepare().let"))
        self.assertIn("if (_nativeModelPrepared) {", s)

    def test_cpp_cleanup_handles_context_missing_before_prepare(self):
        cpp = safe_unload("// pinned C++\n" + BEFORE_CONTEXT + "\n" + BEFORE_UNLOAD)
        self.assertIn(AFTER_CONTEXT, cpp)
        self.assertIn(AFTER_UNLOAD, cpp)
        for p in ("g_model", "g_sampler", "g_context"):
            self.assertIn(p + " = nullptr;", cpp)
        self.assertIn("if (g_context != nullptr)", cpp)

    def test_reapply_is_idempotent_and_drift_fails_closed(self):
        once = hardened(self.fixture())
        self.assertEqual(hardened(once), once)
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(self.fixture().replace("load(pathToModel).let", "loadModelUnsafe(path).let"))
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(self.fixture() + "\n" + BEFORE)

    def test_verification_does_not_write_until_patched(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / IMPL
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(self.fixture(), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "not applied"):
                apply(Path(temp), verify_only=True)
            self.assertEqual(path.read_text(), self.fixture())
            apply(Path(temp))
            apply(Path(temp), verify_only=True)
            self.assertIn(AFTER, path.read_text())

    def test_original_pinned_error_guard_is_not_mutated(self):
        # This contract is important because CI independently reruns
        # fix_native_prompt_error.py --verify after all build-time patches.
        self.assertIn("if (_nativeModelPrepared) {", hardened(self.fixture()))
        self.assertNotIn("is InferenceEngine.State.Error -> {\n                    unload()", hardened(self.fixture()))

    def test_pipeline_applies_cpp_null_guards_before_early_owner_marker(self):
        p = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertIn("patch_native_partial_cleanup(UPSTREAM)", p)
        self.assertLess(p.index("patch_native_unload_safety(UPSTREAM)"),
                        p.index("patch_native_partial_cleanup(UPSTREAM)"))
        self.assertLess(p.index("patch_native_prompt_error(UPSTREAM)"),
                        p.index("patch_native_partial_cleanup(UPSTREAM)"))


if __name__ == "__main__":
    unittest.main()
