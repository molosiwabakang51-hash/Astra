"""A failed 1.7B native load must not leak model or KV context across retries."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_partial_load_cleanup import BEFORE, AFTER, IMPL, apply, hardened
from fix_native_unload_safety import BEFORE_CONTEXT, AFTER_CONTEXT, BEFORE_UNLOAD, AFTER_UNLOAD, hardened as safe_unload

ROOT = Path(__file__).resolve().parents[2]


class NativePartialLoadCleanupTest(unittest.TestCase):
    @staticmethod
    def fixture():
        # The real pinned Kotlin binding has two or more preparation-state
        # invalidations; source fixture mimics those independent assignments.
        return ("    private var _nativeModelPrepared = false\n"
                "    _nativeModelPrepared = false\n" + BEFORE)

    def test_error_cleanup_unconditionally_unloads_native_model(self):
        s = hardened(self.fixture())
        self.assertIn(AFTER, s)
        self.assertNotIn(BEFORE, s)
        cleanup = s[s.index("is InferenceEngine.State.Error -> {"):]
        self.assertLess(cleanup.index("unload()"), cleanup.index("_nativeModelPrepared = false"))
        self.assertNotIn("if (_nativeModelPrepared)", cleanup)
        self.assertLess(cleanup.index("_nativeModelPrepared = false"),
                        cleanup.index("_state.value = InferenceEngine.State.Initialized"))

    def test_native_unload_must_skip_null_context_and_null_all_freed_objects(self):
        cpp = safe_unload(
            "// pinned loader context and teardown\n" + BEFORE_CONTEXT + "\n" + BEFORE_UNLOAD
        )
        self.assertIn(AFTER_CONTEXT, cpp)
        self.assertIn(AFTER_UNLOAD, cpp)
        self.assertIn("if (g_context != nullptr)", cpp)
        for p in ("g_model", "g_sampler", "g_context"):
            self.assertIn(p + " = nullptr;", cpp)

    def test_reapply_is_idempotent_and_unrecognized_error_branch_fails_closed(self):
        fixture = self.fixture()
        once = hardened(fixture)
        self.assertEqual(hardened(once), once)
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(fixture.replace("Resetting error states...", "Unknown refactor"))
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(fixture + "\n" + BEFORE)

    def test_verify_only_preserves_file_until_patch_installed(self):
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

    def test_prepare_order_guarantees_safe_native_teardown_before_error_unload(self):
        p = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertIn("patch_native_partial_cleanup(UPSTREAM)", p)
        self.assertLess(p.index("patch_native_unload_safety(UPSTREAM)"),
                        p.index("patch_native_partial_cleanup(UPSTREAM)"))
        self.assertLess(p.index("patch_native_prompt_error(UPSTREAM)"),
                        p.index("patch_native_partial_cleanup(UPSTREAM)"))


if __name__ == "__main__":
    unittest.main()
