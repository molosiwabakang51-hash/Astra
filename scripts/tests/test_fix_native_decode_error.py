"""Pinned Android JNI must not silently accept a failed token decode."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_decode_error import AFTER, BEFORE, CPP, apply, hardened


class NativeDecodeErrorTest(unittest.TestCase):
    def test_decode_error_raises_content_free_jni_exception(self):
        result = hardened("// fixed b12248 native source\n" + BEFORE)
        self.assertIn("FindClass(\"java/lang/IllegalStateException\")", result)
        self.assertIn('env->ThrowNew(failure_class, "Offline native token decode failed.");', result)
        self.assertIn("env->DeleteLocalRef(failure_class);", result)
        self.assertNotIn(BEFORE, result)
        self.assertNotIn("user_prompt", result)
        self.assertNotIn("model_path", result)

    def test_error_exception_precedes_native_end_of_stream(self):
        fixed = hardened(BEFORE)
        self.assertLess(fixed.index("env->ThrowNew("), fixed.index("return nullptr;"))
        self.assertEqual(fixed.count("Offline native token decode failed."), 1)

    def test_idempotent_pinned_source_patch(self):
        once = hardened(BEFORE)
        self.assertEqual(once, hardened(once))
        self.assertEqual(once, AFTER)

    def test_renamed_or_duplicate_native_error_handler_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(BEFORE.replace("failed for generated token", "failed for decoder"))
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(BEFORE + "\n" + BEFORE)
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(AFTER + "\n" + BEFORE)

    def test_verify_requires_patch_and_never_rewrites_in_verify_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / CPP
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(BEFORE, encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "has not been applied"):
                apply(Path(tmp), verify_only=True)
            self.assertEqual(BEFORE, file.read_text(encoding="utf-8"))
            apply(Path(tmp))
            apply(Path(tmp), verify_only=True)
            self.assertEqual(AFTER, file.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
