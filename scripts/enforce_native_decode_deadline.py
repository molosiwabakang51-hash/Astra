#!/usr/bin/env python3
"""CPU decode deadline for the pinned llama.cpp b12248 Android runtime.

Kotlin's withTimeoutOrNull is cooperative: once an Android thread enters a
long llama_process() call, cancelling its coroutine does not interrupt that
native compute. The pinned ggml/llama headers expose a CPU abort callback.
Install a monotonic, per-turn native deadline checked by that callback.

The deadline covers both native prompt prefill and output decoding. A prompt
decode that aborts returns a nonzero code; token decode aborts use the
content-free JNI exception installed by fix_native_decode_error.py. Both
routes transition to Kotlin State.Error and require a fresh native model
session. This does not stop model loading, a deadlocked callback, GPU
execution, or a native process crash. No new thread or force-unlock is used.

Strictly patch only reviewed, pinned signatures. Fail closed on drift.
"""
from __future__ import annotations

import argparse
from pathlib import Path

CPP = Path("examples/llama.android/lib/src/main/cpp/ai_chat.cpp")
INTERFACE = Path("examples/llama.android/lib/src/main/java/com/arm/aichat/InferenceEngine.kt")
IMPL = Path(
    "examples/llama.android/lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt"
)

CPP_RULES = (
    (
        "#include <string>\n#include <unistd.h>",
        "#include <string>\n#include <atomic>\n#include <chrono>\n#include <cstdint>\n#include <unistd.h>",
    ),
    (
        "static common_sampler                   * g_sampler;",
        """static common_sampler                   * g_sampler;

// Only native model ownership writes this deadline. The llama CPU backend
// may read it on worker threads during llama_process(), so it must be atomic.
// A zero deadline disables abort for model setup and system prompt creation.
static std::atomic<int64_t> astra_decode_deadline_ns{0};
static std::atomic<bool> astra_cancel_requested{false};

static bool astra_abort_expired_decode(void * /*unused*/) {
    // The owner can signal this from another thread without waiting for
    // the single-threaded llama dispatcher to return from native decode.
    if (astra_cancel_requested.load(std::memory_order_relaxed)) return true;
    const auto deadline = astra_decode_deadline_ns.load(std::memory_order_relaxed);
    if (deadline <= 0) return false;
    const auto now = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    return now >= deadline;
}

// A content-free native atomic setter is safe even when the JNI decoder
// is occupying the llama dispatcher. It never calls llama APIs or frees
// a model; only the worker-owned cleanup can do that.
extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_requestNativeDecodeAbort(
        JNIEnv * /*env*/, jobject /*unused*/) {
    astra_cancel_requested.store(true, std::memory_order_relaxed);
}""",
    ),
    (
        """    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);""",
        """    ctx_params.n_threads_batch = n_threads;
    // Pinned llama.cpp b12248 supports abort polling during CPU decode.
    // The callback never accesses JNI, private text, or native model memory.
    ctx_params.abort_callback = astra_abort_expired_decode;
    ctx_params.abort_callback_data = nullptr;
    auto *context = llama_init_from_model(g_model, ctx_params);""",
    ),
    (
        """Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    auto *context = init_context(g_model);""",
        """Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    // An expired earlier request must never abort a newly prepared context.
    astra_decode_deadline_ns.store(0, std::memory_order_relaxed);
    astra_cancel_requested.store(false, std::memory_order_relaxed);
    auto *context = init_context(g_model);""",
    ),
    (
        """Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();""",
        """Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict,
        jlong decode_timeout_ms
) {
    // Fail closed if an older or incorrect Kotlin binding passes no timeout.
    if (decode_timeout_ms <= 0 || decode_timeout_ms > 600000) return 3;
    const auto now = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    astra_decode_deadline_ns.store(
        now + static_cast<int64_t>(decode_timeout_ms) * 1000000LL,
        std::memory_order_relaxed
    );
    // Never clear an owner's Stop signal here: the UI may signal abort just
    // after ProcessingUserPrompt is published but before this JNI entry.
    // A cancelled session is invalidated and prepare() resets the flag.
    // Includes both prompt prefill and the subsequent assistant generation.
    // Reset short-term states
    reset_short_term_states();""",
    ),
)

INTERFACE_RULES = (
    (
        "fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String>",
        "fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH, decodeTimeoutMs: Long = 300_000L): Flow<String>",
    ),
    (
        "    fun cleanUp()",
        "    /** Signal native CPU decode immediately without touching model memory. */\n    fun requestAbort()\n\n    /** Read only after generation completes under exclusive native ownership. */\n    fun generationLimitReached(): Boolean\n\n    fun cleanUp()",
    ),
)

IMPL_RULES = (
    (
        "    private external fun generateNextToken(): String?",
        "    private external fun generateNextToken(): String?\n\n    private external fun requestNativeDecodeAbort()\n\n    private external fun generationLimitReachedNative(): Boolean",
    ),
    (
        "private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int",
        "private external fun processUserPrompt(userPrompt: String, predictLength: Int, decodeTimeoutMs: Long): Int",
    ),
    (
        """    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
    ): Flow<String> = flow {
        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }""",
        """    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
        decodeTimeoutMs: Long,
    ): Flow<String> = flow {
        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }
        require(decodeTimeoutMs in 1L..600_000L) {
            "Native decode deadline must be positive and bounded."
        }""",
    ),
    (
        "processUserPrompt(message, predictLength).let { result ->",
        "processUserPrompt(message, predictLength, decodeTimeoutMs).let { result ->",
    ),
    (
        "    override fun cleanUp() {",
        "    override fun requestAbort() {\n        _cancelGeneration = true\n        requestNativeDecodeAbort()\n    }\n\n    override fun generationLimitReached(): Boolean = generationLimitReachedNative()\n\n    override fun cleanUp() {",
    ),
)


def rewrite(source: str, rules: tuple[tuple[str, str], ...], label: str) -> str:
    result = source
    for before, after in rules:
        old_hits, fixed_hits = result.count(before), result.count(after)
        if fixed_hits == 1 and old_hits in (0, 1):
            continue  # Exact reviewed result already present.
        if old_hits == 1 and fixed_hits == 0:
            result = result.replace(before, after, 1)
        else:
            raise RuntimeError(
                f"Pinned native deadline {label} drift/duplicate; "
                f"original={old_hits}, patched={fixed_hits}; "
                + repr(before[:90])
            )
    return result


def hardened_cpp(source: str) -> str:
    result = rewrite(source, CPP_RULES, "C++")
    if result.count("astra_abort_expired_decode") != 2:
        raise RuntimeError("CPU abort callback must be declared and registered exactly once")
    if result.count("astra_decode_deadline_ns.store(") != 2:
        raise RuntimeError("Native deadline must reset on prepare and set on user request")
    if result.count("decode_timeout_ms > 600000") != 1:
        raise RuntimeError("Native deadline safety bound is missing")
    if result.count("astra_cancel_requested.store(false,") != 1 or (
        result.count("astra_cancel_requested.store(true,") != 1
    ):
        raise RuntimeError("Native cancel flag must reset per session and request")
    if result.count("requestNativeDecodeAbort(") != 1:
        raise RuntimeError("Native abort signal must be exported once")
    return result


def hardened_interface(source: str) -> str:
    return rewrite(source, INTERFACE_RULES, "Kotlin interface")


def hardened_impl(source: str) -> str:
    result = rewrite(source, IMPL_RULES, "Kotlin implementation")
    if result.count("decodeTimeoutMs in 1L..600_000L") != 1:
        raise RuntimeError("Kotlin must reject unbounded native decode requests")
    if result.count("requestNativeDecodeAbort()") != 2:
        raise RuntimeError("Kotlin immediate abort must call the JNI atomic setter")
    return result


def apply(upstream: Path, verify_only: bool = False) -> None:
    for relative, patch in (
        (CPP, hardened_cpp),
        (INTERFACE, hardened_interface),
        (IMPL, hardened_impl),
    ):
        path = upstream / relative
        if not path.is_file():
            raise RuntimeError("Pinned native deadline source missing: " + str(relative))
        original = path.read_text(encoding="utf-8")
        patched = patch(original)
        if verify_only and patched != original:
            raise RuntimeError("Native CPU deadline not installed: " + str(relative))
        if patched != original:
            path.write_text(patched, encoding="utf-8")
    print("PASS: native CPU decoding checks a bounded monotonic deadline", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    parser.add_argument(
        "--upstream", type=Path,
        default=Path(__file__).resolve().parent.parent / "third_party/llama.cpp"
    )
    opts = parser.parse_args()
    apply(opts.upstream, opts.verify)
