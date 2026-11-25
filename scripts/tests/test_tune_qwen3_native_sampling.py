"""Verify pinned sampling integration; this is not a Qwen quality benchmark."""
import sys
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from tune_qwen3_native_sampling import CPP, RULES, apply, hardened


def fixture():
    return "\n\n".join(before for before, _ in RULES) + "\n"


class Qwen3NativeSamplingTests(unittest.TestCase):
    def test_official_mode_profiles(self):
        source = hardened(fixture())
        self.assertIn("sparams.temp = thinking ? 0.6f : 0.7f;", source)
        self.assertIn("sparams.top_p = thinking ? 0.95f : 0.8f;", source)
        self.assertIn("sparams.top_k = 20;", source)
        self.assertIn("sparams.min_p = 0.0f;", source)
        self.assertNotIn("DEFAULT_SAMPLER_TEMP", source)
        self.assertNotIn("sparams.penalty_present =", source)

    def test_per_turn_suffix_and_sampler_lifecycle(self):
        source = hardened(fixture())
        self.assertIn('const std::string suffix = "\\n/think";', source)
        self.assertIn("prompt.compare(prompt.size() - suffix.size(), suffix.size(), suffix) == 0", source)
        start = source.index("auto * next_sampler")
        self.assertLess(source.index("if (next_sampler == nullptr)", start),
                        source.index("common_sampler_free(g_sampler)", start))
        self.assertLess(source.index("env->ReleaseStringUTFChars(juser_prompt, user_prompt);", start),
                        source.index("return 4;", start))
        self.assertIn("g_sampler = next_sampler;", source)
        self.assertIn("g_sampler = new_sampler(false);", source)

    def test_pinned_drift_duplicate_and_idempotence(self):
        original = fixture()
        fixed = hardened(original)
        self.assertEqual(fixed, hardened(fixed))
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(original.replace("0.3f", "0.1f"))
        with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
            hardened(original + RULES[1][0])

    def test_build_and_current_mode_wiring(self):
        prep = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertIn("patch_qwen3_sampling(UPSTREAM)", prep)
        self.assertLess(prep.index("patch_native_partial_cleanup(UPSTREAM)"),
                        prep.index("patch_qwen3_sampling(UPSTREAM)"))
        policy = (ROOT / "android/app/src/main/java/com/astra/agent/AstraOfflinePolicy.kt").read_text()
        self.assertIn(r'if (thinkingRequested) "\n/think" else "\n/no_think"', policy)

    def test_verify_only_rejects_unpatched_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / CPP
            file.parent.mkdir(parents=True)
            file.write_text(fixture())
            with self.assertRaisesRegex(RuntimeError, "not installed"):
                apply(Path(tmp), verify_only=True)
            apply(Path(tmp))
            apply(Path(tmp), verify_only=True)
            self.assertEqual(hardened(file.read_text()), file.read_text())


if __name__ == "__main__":
    unittest.main()
