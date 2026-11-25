"""Structural checks for the pinned native context admission patch."""
import sys
import unittest
import shutil
import subprocess
import tempfile
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
        self.assertIn("system_prompt_position - static_cast<int64_t>(user_tokens.size())", source)
        self.assertIn("if (astra_available < astra_minimum) return 5;", source)
        self.assertIn("std::min<int64_t>(n_predict, astra_available)", source)
        self.assertIn("return 5;", source)
        self.assertIn("end > current_position", source)
        self.assertIn("!llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, end)", source)
        self.assertIn("llama_memory_seq_add(llama_get_memory(g_context), 0, end, current_position, -astra_discard);", source)
        self.assertLess(source.index("current_position -= astra_discard;"),
                        source.index("const int original_user_prompt_size"))

    @unittest.skipUnless(shutil.which("c++"), "Host C++ compiler required")
    def test_actual_cpp_budget_arithmetic(self):
        # Compile and execute the exact admission calculation, not a Python
        # reimplementation. Stub only context state; no model is loaded.
        fixed = hardened(self.fixture())
        start = fixed.index("    const int astra_context_capacity")
        end = fixed.index("    // Remove only preceding dialogue.", start)
        body = fixed[start:end]
        harness = """
#include <algorithm>
#include <cstdint>
#include <vector>
#include <cassert>
using jint = int32_t;
constexpr int DEFAULT_CONTEXT_SIZE = 4096;
constexpr int OVERFLOW_HEADROOM = 4;
int effective(int system_prompt_position, int current_position,
              size_t count, jint n_predict) {
    std::vector<int> user_tokens(count);
""" + body + """
    return n_predict;
}
int main() {
    // Capacity 4091; preserve exact preferred budgets when possible.
    assert(effective(1000, 1000, 100, 1792) == 1792);
    assert(effective(1000, 1000, 2000, 1792) == 1091);
    assert(effective(1000, 1000, 2323, 1792) == 768);
    assert(effective(1000, 1000, 2324, 1792) == 5);
    assert(effective(1000, 1000, 2700, 1152) == 391);
    assert(effective(1000, 1000, 2835, 768) == 256);
    assert(effective(1000, 1000, 2836, 768) == 5);
    assert(effective(1000, 1000, 2600, 512) == 5);
    assert(effective(1000, 1000, 2579, 512) == 512);
    assert(effective(1000, 1000, 2995, 96) == 96);
    assert(effective(1000, 1000, 2996, 96) == 5);
    assert(effective(1000, 999, 100, 768) == 5);
    assert(effective(1000, 4092, 100, 768) == 5);
    assert(effective(1000, 1000, 100, 0) == 5);
    assert(effective(1000, 1000, 4000, 768) == 5);
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "admission.cpp"
            exe = Path(tmp) / "admission"
            src.write_text(harness, encoding="utf-8")
            subprocess.run(["c++", "-std=c++17", "-Wall", "-Werror", str(src),
                            "-o", str(exe)], check=True, timeout=30)
            subprocess.run([str(exe)], check=True, timeout=10)

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
