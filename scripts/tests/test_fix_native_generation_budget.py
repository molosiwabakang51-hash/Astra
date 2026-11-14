"""Regression tests for on-device JNI prompt and generation token accounting."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_generation_budget import CPP, BEFORE, AFTER, apply, hardened


class NativeGenerationBudgetTest(unittest.TestCase):
    def test_limits_assistant_tokens_to_n_predict_after_user_prompt(self):
        corrected = hardened("void process() {\n" + BEFORE + "\n}\n")
        self.assertIn(
            "stop_generation_position = current_position + n_predict;",
            corrected
        )
        self.assertNotIn(
            "stop_generation_position = current_position + user_prompt_size + n_predict;",
            corrected
        )
        self.assertIn("current_position += user_prompt_size;", corrected)

    def test_counts_truncated_prompt_tokens_instead_of_original_length(self):
        corrected = hardened(BEFORE)
        self.assertLess(
            corrected.index("user_tokens.resize(max_batch_size);"),
            corrected.index("const int user_prompt_size = (int) user_tokens.size();")
        )
        self.assertIn(
            "const int skipped_tokens = original_user_prompt_size - max_batch_size;",
            corrected
        )
        self.assertIn(
            "current_position += user_prompt_size;", corrected
        )

    def test_idempotent_native_patch(self):
        once = hardened(BEFORE)
        self.assertEqual(hardened(once), once)
        self.assertEqual(once.count("stop_generation_position = current_position + n_predict;"), 1)

    def test_rejects_upstream_changes_to_stop_position(self):
        changed = BEFORE.replace(
            "current_position + user_prompt_size + n_predict",
            "current_position + 2 * user_prompt_size + n_predict"
        )
        with self.assertRaisesRegex(RuntimeError, "source drift"):
            hardened(changed)

    def test_rejects_duplicate_native_entry_points(self):
        with self.assertRaisesRegex(RuntimeError, "duplicated blocks"):
            hardened(BEFORE + "\n" + BEFORE)

    def test_verify_only_fails_until_correction_is_installed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / CPP
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("void process() {\n" + BEFORE + "\n}\n")
            with self.assertRaisesRegex(RuntimeError, "has not been applied"):
                apply(Path(tmp), verify_only=True)
            apply(Path(tmp))
            apply(Path(tmp), verify_only=True)
            self.assertIn(AFTER, path.read_text())


if __name__ == "__main__":
    unittest.main()
