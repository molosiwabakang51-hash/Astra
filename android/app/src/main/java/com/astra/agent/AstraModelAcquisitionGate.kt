package com.astra.agent

import kotlinx.coroutines.sync.Mutex

/**
 * One process-wide 1.7B model acquisition at a time.
 *
 * SAF imports and ranged HTTPS downloads use different partial filenames,
 * so per-operation locks alone permit both 1.3 GB staging copies to run at
 * once on a low-storage Android phone. They also race to install the same
 * verified target, wasting mobile data and invalidating native sessions.
 *
 * Keep this lightweight gate for the complete copy/hash/promotion lifecycle.
 * Native inference itself remains independent: model staging may happen
 * while Qwen replies, and AstraModelOperationGate.withPromotion still
 * serializes only the final verified GGUF rename with active JNI.
 */
internal object AstraModelAcquisitionGate {
    private val active = Mutex()

    suspend fun <T> singleFlight(acquire: suspend () -> T): T {
        check(active.tryLock()) {
            "A Qwen3 1.7B model import or download is already running. " +
                "Finish or cancel that operation before starting another."
        }
        try {
            return acquire()
        } finally {
            active.unlock()
        }
    }
}
