package com.astra.agent

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Guards every untrusted document-provider bulk read, including reads made
 * internally by ZIP, PDF and buffered streams.
 *
 * An InputStream may transiently return zero bytes from a nonempty request.
 * A broken SAF provider that returns zero forever must not spin on the UI
 * or import coroutine, waste battery, or hang private knowledge ingestion.
 * Existing byte/entry limits remain enforced by the document parsers.
 */
internal class AstraProgressInputStream(input: InputStream) : FilterInputStream(input) {
    private companion object {
        const val MAX_EMPTY_READS = 16
        const val STALLED_PROVIDER =
            "The selected document provider stopped returning data. " +
                "Copy the document to Downloads and retry the import."
    }

    private var consecutiveEmptyReads = 0

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // The InputStream API requires a zero-length read to return zero,
        // even when there are no bytes left. It is not a provider stall.
        if (length == 0) return 0
        while (true) {
            val read = super.read(buffer, offset, length)
            if (read != 0) {
                consecutiveEmptyReads = 0
                return read
            }
            if (++consecutiveEmptyReads > MAX_EMPTY_READS) {
                throw IOException(STALLED_PROVIDER)
            }
        }
    }

    override fun read(buffer: ByteArray): Int = read(buffer, 0, buffer.size)
}
