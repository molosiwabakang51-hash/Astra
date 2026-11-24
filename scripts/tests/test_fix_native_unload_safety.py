"""Regression checks: never dereference or double-free a failed JNI model load."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_unload_safety import (
    CPP, BEFORE_CONTEXT, AFTER_CONTEXT, BEFORE_UNLOAD, AFTER_UNLOAD,
    apply, hardened,
)

ROOT = Path(__file__).resolve().parents[2]


class NativeUnloadingTests(unittest.TestCase):
    def fixture(self):
        return (
            "static void reset_long_term_states(const bool clear_kv_cache = true) {\n"
            + BEFORE_CONTEXT + "\n}\n\n"
            + "JNIEXPORT void JNICALL Java_com_arm_aichat_internal_InferenceEngineImpl_unload() {\n"
            + BEFORE_UNLOAD + "\n}\n"
        )

    def test_unload_skips_null_context_before_touching_kv_cache(self):
        source = hardened(self.fixture())
        self.assertIn(AFTER_CONTEXT, source)
        self.assertIn("clear_kv_cache && g_context != nullptr", source)
        self.assertNotIn(BEFORE_CONTEXT, source)

    def test_sampler_context_model_are_guarded_and_invalidated_after_free(self):
        source = hardened(self.fixture())
        self.assertIn(AFTER_UNLOAD, source)
        for pointer in ("g_sampler", "g_context", "g_model"):
            self.assertIn(f"if ({pointer} != nullptr)", source)
            self.assertIn(f"{pointer} = nullptr;", source)
            self.assertLess(source.index(f"{pointer} = nullptr;"), source.index("}\n", source.index(f"{pointer} = nullptr;")))
        self.assertNotIn(BEFORE_UNLOAD, source)

    def test_pinned_source_drift_duplicate_and_idempotence(self):
        once = hardened(self.fixture())
        self.assertEqual(once, hardened(once))
        with self.assertRaisesRegex(RuntimeError, "teardown drift"):
            hardened(self.fixture().replace("llama_model_free(g_model);", "free(g_model);"))
        with self.assertRaisesRegex(RuntimeError, "teardown drift"):
            hardened(self.fixture() + "\n" + BEFORE_CONTEXT)

    def test_verify_only_never_rewrites_source(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / CPP
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text(self.fixture(), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "not been applied"):
                apply(Path(temp), verify_only=True)
            self.assertEqual(self.fixture(), source.read_text())
            apply(Path(temp))
            apply(Path(temp), verify_only=True)
            self.assertEqual(hardened(self.fixture()), source.read_text())

    def test_prepare_pipeline_places_unload_guard_last(self):
        prepare = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertIn("patch_native_unload_safety(UPSTREAM)", prepare)
        self.assertLess(
            prepare.index("patch_native_model_load_cancel(UPSTREAM)"),
            prepare.index("patch_native_unload_safety(UPSTREAM)"),
        )


if __name__ == "__main__":
    unittest.main()
