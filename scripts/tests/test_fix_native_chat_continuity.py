"""Regression tests for bounded on-device conversation continuity."""
import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_chat_continuity import CPP, REWRITES, apply, hardened


def upstream_fixture():
    # The second call is the original EOG branch; bounded generation must
    # add another copy without removing the EOG completion path.
    return "\n\n".join(before for before, _ in REWRITES) + (
        "\n    if (llama_vocab_is_eog(...)) {\n"
        "        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());\n"
        "        return nullptr;\n"
        "    }\n"
    )


class NativeChatContinuityTest(unittest.TestCase):
    def test_caps_generated_tokens_using_counter_not_kv_positions(self):
        source = hardened(upstream_fixture())
        self.assertIn("remaining_generation_tokens = std::max(0, static_cast<int>(n_predict));", source)
        self.assertIn("--remaining_generation_tokens;", source)
        self.assertIn("if (remaining_generation_tokens <= 0)", source)
        self.assertNotIn("stop_generation_position", source)

    def test_stores_max_length_answer_in_native_chat_history(self):
        source = hardened(upstream_fixture())
        self.assertEqual(
            source.count("chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());"), 2
        )
        cap = source.index("if (remaining_generation_tokens <= 0)")
        record = source.index("chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());", cap)
        stop = source.index("return nullptr;", cap)
        self.assertLess(cap, record)
        self.assertLess(record, stop)

    def test_exact_token_limit_is_distinct_from_eog_and_resets(self):
        source = hardened(upstream_fixture())
        self.assertIn("generationLimitReachedNative(", source)
        self.assertIn("return astra_generation_limit_reached ? JNI_TRUE : JNI_FALSE;", source)
        self.assertIn("remaining_generation_tokens = 0;\n    astra_generation_limit_reached = false;", source)
        cap = source.index("if (remaining_generation_tokens <= 0)")
        self.assertLess(cap, source.index("astra_generation_limit_reached = true;"))
        self.assertEqual(source.count("astra_generation_limit_reached = true;"), 1)
        self.assertNotIn("astra_generation_limit_reached = true;", source[source.index("if (llama_vocab_is_eog(...)"):])

    def test_checks_cap_before_context_shift(self):
        source = hardened(upstream_fixture())
        self.assertLess(
            source.index("if (remaining_generation_tokens <= 0)"),
            source.index("if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM)")
        )
        self.assertLess(
            source.index("--remaining_generation_tokens;"),
            source.index("// Stop if next token is EOG")
        )

    def test_is_idempotent_and_reports_unrecognised_upstream(self):
        once = hardened(upstream_fixture())
        self.assertEqual(hardened(once), once)
        broken = upstream_fixture().replace("stop_generation_position = current_position + n_predict;",
                                             "stop_generation_position = current_position + n_predict * 2;")
        with self.assertRaisesRegex(RuntimeError, "source drift"):
            hardened(broken)

    def test_duplicate_source_entry_points_fail_closed(self):
        source = upstream_fixture() + "\n" + REWRITES[2][0]
        with self.assertRaisesRegex(RuntimeError, "duplicated snippet"):
            hardened(source)

    def test_verify_fails_until_pinned_native_code_is_hardened(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / CPP
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(upstream_fixture(), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "has not been applied"):
                apply(Path(directory), verify_only=True)
            apply(Path(directory))
            apply(Path(directory), verify_only=True)
            self.assertEqual(hardened(path.read_text()), path.read_text())


if __name__ == "__main__":
    unittest.main()
