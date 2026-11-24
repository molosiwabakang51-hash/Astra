"""Regression contracts for Qwen3 CPU decode's monotonic deadline."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from enforce_native_decode_deadline import (
    CPP, IMPL, INTERFACE, CPP_RULES, IMPL_RULES, INTERFACE_RULES,
    apply, hardened_cpp, hardened_impl, hardened_interface,
)

ROOT = Path(__file__).resolve().parents[2]


def fixture(rules):
    # Exact pinned structural boundaries are independently verified against
    # real source when prepare_offline_android.py applies the build patch.
    return "\n\n".join(before for before, _ in rules)


class NativeDecodeDeadlineTest(unittest.TestCase):
    def test_cpu_abort_callback_is_thread_safe_and_uses_monotonic_time(self):
        source = hardened_cpp(fixture(CPP_RULES))
        self.assertIn("#include <atomic>", source)
        self.assertIn("#include <chrono>", source)
        self.assertIn("std::atomic<int64_t> astra_decode_deadline_ns{0};", source)
        self.assertIn("std::chrono::steady_clock::now().time_since_epoch()", source)
        self.assertIn("std::memory_order_relaxed", source)
        self.assertIn("ctx_params.abort_callback = astra_abort_expired_decode;", source)
        self.assertIn("ctx_params.abort_callback_data = nullptr;", source)
        self.assertIn("if (deadline <= 0) return false;", source)

    def test_deadline_covers_native_prompt_and_token_decode(self):
        source = hardened_cpp(fixture(CPP_RULES))
        start = source.index("Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(")
        request = source[start:]
        self.assertIn("jlong decode_timeout_ms", request)
        self.assertIn("if (decode_timeout_ms <= 0 || decode_timeout_ms > 600000) return 3;", request)
        self.assertLess(
            request.index("astra_decode_deadline_ns.store("),
            request.index("reset_short_term_states();"),
        )
        self.assertIn("now + static_cast<int64_t>(decode_timeout_ms) * 1000000LL", request)
        prepare = source.index("Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(")
        self.assertLess(
            source.index("astra_decode_deadline_ns.store(0,", prepare),
            source.index("auto *context = init_context(g_model);", prepare),
        )

    def test_owner_stop_writes_only_atomic_flag_without_waiting_for_jni(self):
        cpp = hardened_cpp(fixture(CPP_RULES))
        start = cpp.index("Java_com_arm_aichat_internal_InferenceEngineImpl_requestNativeDecodeAbort(")
        end = cpp.index("\n}", start) + 2
        setter = cpp[start:end]
        self.assertIn("astra_cancel_requested.store(true, std::memory_order_relaxed)", setter)
        self.assertNotIn("llama_process(", setter)
        self.assertNotIn("llama_free(", setter)
        self.assertNotIn("JNIEnv::", setter)
        self.assertIn("if (astra_cancel_requested.load(std::memory_order_relaxed)) return true;", cpp)
        self.assertEqual(cpp.count("astra_cancel_requested.store(false,"), 1)
        user_entry = cpp[cpp.index("Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt("):]
        self.assertNotIn("astra_cancel_requested.store(false,", user_entry)
        self.assertIn("Never clear an owner's Stop signal here", user_entry)

    def test_owner_stop_is_wired_to_all_four_chat_cancel_actions(self):
        brain = (ROOT / "android/app/src/main/java/com/astra/agent/AstraOnDeviceBrain.kt").read_text()
        ui = (ROOT / "android/app/src/main/java/com/astra/agent/MainActivity.kt").read_text()
        interface = hardened_interface(fixture(INTERFACE_RULES))
        impl = hardened_impl(fixture(IMPL_RULES))
        self.assertIn("fun requestAbort()", interface)
        self.assertIn("override fun requestAbort()", impl)
        self.assertIn("_cancelGeneration = true", impl)
        self.assertIn("requestNativeDecodeAbort()", impl)
        self.assertIn("fun requestGenerationStop()", brain)
        self.assertIn("current.requestAbort()", brain)
        self.assertIn("state is InferenceEngine.State.Generating", brain)
        self.assertIn("state is InferenceEngine.State.ProcessingUserPrompt", brain)
        self.assertEqual(4, ui.count("onDeviceBrain.requestGenerationStop()"))
        self.assertEqual(4, ui.count("offlineJob?.cancel()"))

    def test_kotlin_api_passes_exact_owner_selected_timeout_to_jni(self):
        interface = hardened_interface(fixture(INTERFACE_RULES))
        impl = hardened_impl(fixture(IMPL_RULES))
        self.assertIn("decodeTimeoutMs: Long = 300_000L", interface)
        self.assertIn("decodeTimeoutMs in 1L..600_000L", impl)
        self.assertIn("processUserPrompt(message, predictLength, decodeTimeoutMs)", impl)
        brain = (ROOT / "android/app/src/main/java/com/astra/agent/AstraOnDeviceBrain.kt").read_text()
        self.assertIn("val nativeDeadlineMs = if (carefulReasoning) 600_000L else 300_000L", brain)
        self.assertIn("withTimeoutOrNull(nativeDeadlineMs)", brain)
        self.assertIn("requestPrompt, answerBudget, nativeDeadlineMs", brain)

    def test_patch_is_idempotent_and_rejects_unknown_upstream_drift(self):
        for patched, rules in (
            (hardened_cpp, CPP_RULES),
            (hardened_interface, INTERFACE_RULES),
            (hardened_impl, IMPL_RULES),
        ):
            original = fixture(rules)
            fixed = patched(original)
            self.assertEqual(patched(fixed), fixed)
            broken = original.replace(rules[0][0], "unexpected native upstream change", 1)
            with self.assertRaisesRegex(RuntimeError, "drift/duplicate"):
                patched(broken)
            duplicated = original + "\n" + rules[0][0]
            with self.assertRaisesRegex(RuntimeError, "drift/duplicate"):
                patched(duplicated)

    def test_verify_only_does_not_mutate_and_real_apply_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            for path, rules in (
                (CPP, CPP_RULES),
                (INTERFACE, INTERFACE_RULES),
                (IMPL, IMPL_RULES),
            ):
                target = Path(tmp) / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(fixture(rules), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "not installed"):
                apply(Path(tmp), verify_only=True)
            self.assertEqual(fixture(CPP_RULES), (Path(tmp) / CPP).read_text())
            apply(Path(tmp))
            apply(Path(tmp), verify_only=True)
            once = (Path(tmp) / CPP).read_text()
            apply(Path(tmp))
            self.assertEqual(once, (Path(tmp) / CPP).read_text())

    def test_pinned_build_installs_abortion_after_error_recovery(self):
        script = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertIn("from enforce_native_decode_deadline import apply as patch_native_decode_deadline", script)
        self.assertIn("patch_native_decode_deadline(UPSTREAM)", script)
        self.assertLess(
            script.index("patch_native_decode_error(UPSTREAM)"),
            script.index("patch_native_decode_deadline(UPSTREAM)"),
        )


if __name__ == "__main__":
    unittest.main()
