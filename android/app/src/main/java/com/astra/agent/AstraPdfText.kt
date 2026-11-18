package com.astra.agent

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Writer

/**
 * Optional, bounded offline extraction of selectable text from a PDF picked
 * by the owner. This is NOT OCR: an image-only PDF cannot be decoded as text.
 *
 * No network request or execution of JavaScript/attachments; input is never
 * saved as an original PDF in Astra's library. Only bounded extracted text is
 * passed to the existing, private retrieval index.
 */
internal object AstraPdfText {
    private const val MAX_PDF_BYTES = 4 * 1024 * 1024
    private const val MAX_PAGES = 80
    private const val MAX_CHARS = 900_000

    fun sizeAllowed(bytes: Long): Boolean = bytes in 1..MAX_PDF_BYTES.toLong()
    fun pagesToRead(total: Int): Int = total.coerceIn(0, MAX_PAGES)

    fun extract(context: Context, source: InputStream): String {
        val bytes = boundedInput(source)
        require(bytes.size >= 5 &&
            bytes.sliceArray(0 until 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
        ) { "This file is not a standard PDF." }

        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            return PDDocument.load(bytes).use { pdf ->
                require(!pdf.isEncrypted) {
                    "Password-protected or encrypted PDFs are unsupported."
                }
                val totalPages = pdf.numberOfPages
                require(totalPages > 0) { "PDF contains no pages." }
                val result = StringBuilder()
                val count = pagesToRead(totalPages)
                var partial = count < totalPages

                for (page in 1..count) {
                    if (result.length >= MAX_CHARS - 80) {
                        partial = true
                        break
                    }
                    val remaining = MAX_CHARS - result.length - 80
                    val writer = BoundedTextWriter(remaining)
                    PDFTextStripper().apply {
                        startPage = page
                        endPage = page
                        sortByPosition = true
                    }.writeText(pdf, writer)
                    val text = writer.text().trim()
                    if (text.isNotBlank()) {
                        val header = "Page $page:\n"
                        if (result.length + header.length + text.length > MAX_CHARS) {
                            partial = true
                            break
                        }
                        result.append(header).append(text).append('\n')
                    }
                    if (writer.truncated) {
                        partial = true
                        break
                    }
                }
                require(result.isNotBlank()) {
                    "No selectable text found in this PDF. Scanned/image-only PDFs need OCR."
                }
                if (partial) {
                    result.append("[PDF index is partial; later pages or text were not scanned.]")
                }
                result.toString().take(MAX_CHARS).trim()
            }
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException(
                "Password-protected or encrypted PDFs are unsupported.", error
            )
        } catch (error: IOException) {
            throw IllegalArgumentException(
                "The selected PDF could not be read as text. It may be corrupt or unsupported.",
                error
            )
        }
    }

    private fun boundedInput(source: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val block = ByteArray(32 * 1024)
        while (true) {
            val n = source.read(block)
            if (n < 0) break
            require(out.size() + n <= MAX_PDF_BYTES) {
                "PDF exceeds the 4 MiB local text-extraction safety limit."
            }
            out.write(block, 0, n)
        }
        require(out.size() > 0) { "The selected PDF is empty." }
        return out.toByteArray()
    }

    /** Avoid unbounded StringWriter allocation on pages with huge text streams. */
    internal class BoundedTextWriter(private val capacity: Int) : Writer() {
        private val buffer = StringBuilder()
        var truncated: Boolean = false
            private set
        override fun write(chars: CharArray, off: Int, len: Int) {
            if (len <= 0) return
            if (capacity <= buffer.length) {
                truncated = true
                return
            }
            val count = minOf(len, capacity - buffer.length)
            buffer.append(chars, off, count)
            if (count < len) truncated = true
        }
        override fun write(str: String, off: Int, len: Int) {
            if (len <= 0) return
            if (capacity <= buffer.length) {
                truncated = true
                return
            }
            val count = minOf(len, capacity - buffer.length)
            buffer.append(str, off, off + count)
            if (count < len) truncated = true
        }
        override fun flush() {}
        override fun close() {}
        fun text(): String = buffer.toString()
    }
}
