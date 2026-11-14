"""Build-time regression tests for private Android llama.cpp logging."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from harden_native_inference import (
    CPP_PATH, KT_PATH, CPP_RULES, KT_RULES, apply_privacy_patch, patch_lines
)


def fixture(rules):
    return "".join("    " + needle + " upstream-untrusted-text\n"
                   for needle, _, count in rules for _ in range(count))


class HardenNativeInferenceTest(unittest.TestCase):
    def test_redacts_all_cpp_prompt_and_token_log_sites(self):
        src = fixture(CPP_RULES)
        after = patch_lines(src, CPP_RULES)
        for marker, replacement, count in CPP_RULES:
            self.assertNotIn(marker, after)
            self.assertEqual(after.count(replacement), count)
        self.assertEqual(patch_lines(after, CPP_RULES), after)

    def test_redacts_all_kotlin_model_and_error_log_sites(self):
        src = fixture(KT_RULES)
        after = patch_lines(src, KT_RULES)
        for marker, replacement, count in KT_RULES:
            self.assertNotIn(marker, after)
            self.assertEqual(after.count(replacement), count)
        self.assertEqual(patch_lines(after, KT_RULES), after)

    def test_upstream_drift_fails_the_build_closed(self):
        src = fixture(CPP_RULES).replace(
            "System prompt received:", "This logger was renamed upstream"
        )
        with self.assertRaisesRegex(RuntimeError, "Sensitive upstream logger changed"):
            patch_lines(src, CPP_RULES)

    def test_extra_sensitive_logger_fails_instead_of_silent_shipping(self):
        src = fixture(CPP_RULES).replace(
            "User prompt received:", "User prompt received: duplicated", 1
        )
        src += "    User prompt received: another copy\n"
        with self.assertRaisesRegex(RuntimeError, "appeared 2 times"):
            patch_lines(src, CPP_RULES)

    def test_verify_requires_actual_hardened_native_sources(self):
        with tempfile.TemporaryDirectory() as base:
            upstream = Path(base)
            for relative, rules in ((CPP_PATH, CPP_RULES), (KT_PATH, KT_RULES)):
                path = upstream / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(fixture(rules), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "was not hardened"):
                apply_privacy_patch(upstream, verify_only=True)
            apply_privacy_patch(upstream)
            apply_privacy_patch(upstream, verify_only=True)


if __name__ == "__main__":
    unittest.main()
