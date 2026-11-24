#!/usr/bin/env python3
"""Cooperative owner Stop during pinned Qwen3 1.7B native model loading.

The llama.cpp b12248 model loader accepts a progress callback: returning
false requests cancellation while it reads/initializes model weights. Astra
already exposes a thread-safe native abort flag for CPU decode (#335). This
patch reuses it during model loading; a Stop signal need not wait for the
single-threaded llama dispatcher.

A cancelled model load must throw CancellationException, not quarantine the
correct 1.7B weights as an incompatible model. If the load has just finished
and Stop arrives before state ModelReady, prepare and immediately unload the
context so no partially prepared model is reused.

Native loader progress callbacks and CPU abort polling are cooperative, not
real-time guarantees. Neither can terminate a deadlocked loader or process.
Strict pinned source markers; fail closed if upstream changes.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")
IMPL = Path(
    "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
)

CPP_RULES = (
    (
        """Java_com_arm_aichat_internal_InferenceEngineImpl_requestNativeDecodeAbort(
        JNIEnv * /*env*/, jobject /*unused*/) {
    astra_cancel_requested.store(true, std::memory_order_relaxed);
}""",
        """Java_com_arm_aichat_internal_InferenceEngineImpl_requestNativeDecodeAbort(
        JNIEnv * /*env*/, jobject /*unused*/) {
    astra_cancel_requested.store(true, std::memory_order_relaxed);
}

// A reset is only called by Kotlin while the native dispatcher is idle and
// BEFORE changing state to LoadingModel. Resetting at JNI load entry would
// erase a Stop delivered just after state publication.
extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_resetNativeAbort(
        JNIEnv * /*env*/, jobject /*unused*/) {
    astra_cancel_requested.store(false, std::memory_order_relaxed);
}

static bool astra_continue_loading_model(float /*progress*/, void * /*unused*/) {
    return !astra_cancel_requested.load(std::memory_order_relaxed);
}""",
    ),
    (
        """Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();""",
        """Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();
    model_params.progress_callback = astra_continue_loading_model;
    model_params.progress_callback_user_data = nullptr;""",
    ),
)

KT_RULES = (
    (
        """    private external fun requestNativeDecodeAbort()""",
        """    private external fun requestNativeDecodeAbort()
    private external fun resetNativeAbort()""",
    ),
    (
        """                _readyForSystemPrompt = false
                _nativeModelPrepared = false
                _state.value = InferenceEngine.State.LoadingModel""",
        """                // Re-arm the native flag on the native dispatcher before
                // publishing LoadingModel. Stop after this point is never
                // erased at the JNI model-load entry.
                resetNativeAbort()
                _cancelGeneration = false
                _readyForSystemPrompt = false
                _nativeModelPrepared = false
                _state.value = InferenceEngine.State.LoadingModel""",
    ),
    (
        """                    if (it != 0) throw UnsupportedArchitectureException()""",
        """                    if (it != 0) {
                        if (_cancelGeneration) {
                            throw CancellationException("Offline model load cancelled by owner.")
                        }
                        throw UnsupportedArchitectureException()
                    }""",
    ),
    (
        """                _nativeModelPrepared = true
                Log.i(TAG, "Model loaded!")""",
        """                _nativeModelPrepared = true
                // A model can finish loading just before the progress callback
                // observes Stop. Free its fully prepared native resources
                // rather than exposing a cancelled model as ModelReady.
                if (_cancelGeneration) {
                    unload()
                    _nativeModelPrepared = false
                    throw CancellationException("Offline model load cancelled by owner.")
                }
                Log.i(TAG, "Model loaded!")""",
    ),
    (
        """                _readyForSystemPrompt = true

                _cancelGeneration = false
                _state.value = InferenceEngine.State.ModelReady""",
        """                _readyForSystemPrompt = true

                // Never erase Stop after loading: it may have been signalled
                // between the cancellation check and state publication.
                _state.value = InferenceEngine.State.ModelReady""",
    ),
)


def rewrite(source: str, rules: tuple[tuple[str, str], ...], kind: str) -> str:
    result = source
    for before, after in rules:
        a, b = result.count(before), result.count(after)
        if a == 1 and b == 0:
            result = result.replace(before, after, 1)
        elif a == 0 and b == 1:
            pass
        else:
            raise RuntimeError(
                f"Pinned native load-cancel {kind} drift or duplicate: "
                f"original={a}, patched={b}; " + repr(before[:90])
            )
    return result


def hardened_cpp(source: str) -> str:
    out = rewrite(source, CPP_RULES, "C++")
    if out.count("model_params.progress_callback = astra_continue_loading_model;") != 1:
        raise RuntimeError("Native loading progress callback is missing")
    if out.count("Java_com_arm_aichat_internal_InferenceEngineImpl_resetNativeAbort") != 1:
        raise RuntimeError("Native abort reset entrypoint is missing")
    return out


def hardened_impl(source: str) -> str:
    out = rewrite(source, KT_RULES, "Kotlin")
    if out.count('CancellationException("Offline model load cancelled by owner.")') != 2:
        raise RuntimeError("Cancellation must cover mid-load and post-load races")
    if out.count("resetNativeAbort()") != 2:
        raise RuntimeError("Model loader reset must occur once before LoadingModel")
    return out


def apply(upstream: Path, verify_only: bool = False) -> None:
    for relative, patch in ((CPP, hardened_cpp), (IMPL, hardened_impl)):
        path = upstream / relative
        if not path.is_file():
            raise RuntimeError("Pinned native model-load source missing: " + str(relative))
        original = path.read_text(encoding="utf-8")
        updated = patch(original)
        if verify_only and original != updated:
            raise RuntimeError("Native model-load Stop callback not installed: " + str(relative))
        if updated != original:
            path.write_text(updated, encoding="utf-8")
    print("PASS: pinned native model loader checks owner Stop without quarantining valid GGUF", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--upstream", type=Path, default=(
        Path(__file__).resolve().parent.parent / "third_party/llama.cpp"
    ))
    args = parser.parse_args()
    apply(args.upstream, args.verify)
