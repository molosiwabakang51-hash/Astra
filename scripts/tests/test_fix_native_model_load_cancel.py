"""Pinned 1.7B model load must respond to owner Stop without marking good GGUF bad."""
import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fix_native_model_load_cancel import (
    CPP, IMPL, CPP_RULES, KT_RULES, apply, hardened_cpp, hardened_impl,
)

ROOT = Path(__file__).resolve().parents[2]


def original(rules):
    return "\n\n".join(before for before, _ in rules)


class NativeModelLoadCancellationTest(unittest.TestCase):
    def test_load_progress_callback_polls_native_atomic_stop(self):
        result = hardened_cpp(original(CPP_RULES))
        self.assertIn("model_params.progress_callback = astra_continue_loading_model;", result)
        self.assertIn("model_params.progress_callback_user_data = nullptr;", result)
        self.assertIn("return !astra_cancel_requested.load(std::memory_order_relaxed);", result)
        self.assertNotIn("llama_model_free(", result)
        self.assertNotIn("Thread.stop(", result)
        self.assertNotIn("model_path", result.split("static bool astra_continue_loading_model(")[1].split("}")[0])

    def test_native_abort_flag_rearms_before_loading_state_visible(self):
        kotlin = hardened_impl(original(KT_RULES))
        start = kotlin.index("// Re-arm the native flag")
        state = kotlin.index("_state.value = InferenceEngine.State.LoadingModel", start)
        self.assertLess(kotlin.index("resetNativeAbort()", start), state)
        self.assertLess(kotlin.index("_cancelGeneration = false", start), state)
        self.assertIn("private external fun resetNativeAbort()", kotlin)

    def test_cancelled_load_distinguished_from_unsupported_model(self):
        kotlin = hardened_impl(original(KT_RULES))
        load = kotlin.index("if (it != 0) {")
        self.assertLess(kotlin.index("if (_cancelGeneration)", load), kotlin.index("throw UnsupportedArchitectureException()", load))
        self.assertIn("throw CancellationException(\"Offline model load cancelled by owner.\")", kotlin)
        assert kotlin.count('CancellationException("Offline model load cancelled by owner.")') == 2

    def test_finished_but_cancelled_model_is_unloaded_before_exception(self):
        kotlin = hardened_impl(original(KT_RULES))
        start = kotlin.index("_nativeModelPrepared = true")
        finish = kotlin.index('Log.i(TAG, "Model loaded!")', start)
        interrupted = kotlin[start:finish]
        self.assertIn("if (_cancelGeneration)", interrupted)
        self.assertLess(interrupted.index("unload()"), interrupted.index("throw CancellationException("))
        self.assertLess(interrupted.index("_nativeModelPrepared = false"), interrupted.index("throw CancellationException("))
        self.assertNotIn("_cancelGeneration = false\n                _state.value = InferenceEngine.State.ModelReady", kotlin)

    def test_owner_stop_covers_model_load_and_system_prompt(self):
        brain = (ROOT / "android/app/src/main/java/com/astra/agent/AstraOnDeviceBrain.kt").read_text()
        start = brain.index("fun requestGenerationStop()")
        end = brain.index("/** Timing only", start)
        stop = brain[start:end]
        self.assertIn("state !is InferenceEngine.State.LoadingModel", stop)
        self.assertIn("state !is InferenceEngine.State.ProcessingSystemPrompt", stop)
        self.assertIn("current.requestAbort()", stop)
        self.assertIn("catch (_: LinkageError)", stop)

    def test_model_loading_cancellation_is_not_quarantined_as_broken_weights(self):
        brain = (ROOT / "android/app/src/main/java/com/astra/agent/AstraOnDeviceBrain.kt").read_text()
        start = brain.index("current.loadModel(path.absolutePath)")
        error = brain.index("modelHealth.recordFailure(path)", start)
        self.assertIn("catch (cancel: kotlinx.coroutines.CancellationException)", brain[start:error])
        self.assertIn("throw cancel", brain[start:error])

    def test_patch_idempotence_and_pinned_source_drift_guards(self):
        for patch, rules in ((hardened_cpp, CPP_RULES), (hardened_impl, KT_RULES)):
            old = original(rules)
            new = patch(old)
            self.assertEqual(new, patch(new))
            with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
                patch(old.replace(rules[0][0], "unrecognised future upstream", 1))
            with self.assertRaisesRegex(RuntimeError, "drift or duplicate"):
                patch(old + "\n" + rules[0][0])

    def test_verify_only_never_rewrites_unpatched_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            for path, rules in ((CPP, CPP_RULES), (IMPL, KT_RULES)):
                target = Path(tmp) / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(original(rules), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "not installed"):
                apply(Path(tmp), verify_only=True)
            self.assertEqual(original(CPP_RULES), (Path(tmp) / CPP).read_text())
            apply(Path(tmp))
            apply(Path(tmp), verify_only=True)
            self.assertEqual(hardened_cpp(original(CPP_RULES)), (Path(tmp) / CPP).read_text())

    def test_actual_prepare_pipeline_installs_load_cancel_after_decode_abort(self):
        prepare = (ROOT / "scripts/prepare_offline_android.py").read_text()
        self.assertIn("patch_native_model_load_cancel(UPSTREAM)", prepare)
        self.assertLess(
            prepare.index("patch_native_decode_deadline(UPSTREAM)"),
            prepare.index("patch_native_model_load_cancel(UPSTREAM)"),
        )


if __name__ == "__main__":
    unittest.main()
