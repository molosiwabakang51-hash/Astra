"""Structural checks for the pinned native context admission patch."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from fix_native_context_admission import RULES, hardened


class NativeContextAdmissionTests(unittest.TestCase):
    def fixture(self):
        return "\n".join(old for old, _ in RULES)

    def test_no_mid_prefill_shift(self):
        source = hardened(self.fixture())
        self.assertNotIn("shift_context();", source)
        self.assertIn("start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM", source)
        self.assertIn("return 1;", source)

    def test_full_turn_is_reserved_and_only_old_dialogue_removed(self):
        source = hardened(self.fixture())
        self.assertIn("DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM - 1", source)
        self.assertIn("static_cast<int64_t>(user_tokens.size()) + n_predict > astra_context_capacity", source)
        self.assertIn("return 5;", source)
        self.assertIn("end > current_position", source)
        self.assertIn("!llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, end)", source)
        self.assertIn("llama_memory_seq_add(llama_get_memory(g_context), 0, end, current_position, -astra_discard);", source)
        self.assertLess(source.index("current_position -= astra_discard;"),
                        source.index("const int original_user_prompt_size"))

    def test_patch_is_idempotent_and_rejects_drift(self):
        fixed = hardened(self.fixture())
        self.assertEqual(fixed, hardened(fixed))
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(self.fixture() + RULES[0][0])
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(self.fixture().replace("start_pos + i + cur_batch_size", "start_pos + cur_batch_size"))

    def test_build_applies_after_token_accounting(self):
        source = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertLess(source.index("patch_native_generation_budget(UPSTREAM)"),
                        source.index("patch_native_context_admission(UPSTREAM)"))


if __name__ == "__main__":
    unittest.main()
