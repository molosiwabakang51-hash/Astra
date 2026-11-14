"""Regression tests for Android native prompt error and KV-cache recovery."""
import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_prompt_error import IMPL, REWRITES, apply, hardened


def upstream_fixture() -> str:
    return (
        "\n\n".join(before for before, _ in REWRITES)
        + "\n\n_state.value = InferenceEngine.State.Error(e)\n"
    )


class NativePromptErrorTest(unittest.TestCase):
    def test_prompt_failure_throws_instead_of_leaving_state_processing(self):
        fixed = hardened(upstream_fixture())
        self.assertIn(
            'throw IllegalStateException("Native prompt processing failed (code $result)")',
            fixed
        )
        self.assertNotIn("return@flow", fixed)
        self.assertIn("if (_nativeModelPrepared) {", fixed)

    def test_recovery_unloads_prepared_context_and_marks_it_unready(self):
        fixed = hardened(upstream_fixture())
        self.assertIn(
            "if (_nativeModelPrepared) {\n                        unload()\n"
            "                        _nativeModelPrepared = false",
            fixed
        )
        self.assertIn("_readyForSystemPrompt = false", fixed)
        self.assertIn("_nativeModelPrepared = true", fixed)

    def test_safe_to_apply_multiple_times(self):
        once = hardened(upstream_fixture())
        self.assertEqual(hardened(once), once)

    def test_unrecognised_upstream_returns_build_error(self):
        broken = upstream_fixture().replace(
            'Log.e(TAG, "Failed to process user prompt: $result")',
            'Log.e(TAG, "Prompt code changed upstream: $result")'
        )
        with self.assertRaisesRegex(RuntimeError, "source drift"):
            hardened(broken)

    def test_duplicate_error_paths_refused(self):
        src = upstream_fixture() + "\n" + REWRITES[3][0]
        with self.assertRaisesRegex(RuntimeError, "duplicated marker"):
            hardened(src)

    def test_verify_only_requires_actual_patched_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / IMPL
            path.parent.mkdir(parents=True)
            path.write_text(upstream_fixture(), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "was not applied"):
                apply(Path(directory), verify_only=True)
            apply(Path(directory))
            apply(Path(directory), verify_only=True)


if __name__ == "__main__":
    unittest.main()
