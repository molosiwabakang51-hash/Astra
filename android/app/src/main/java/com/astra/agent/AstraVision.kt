package com.astra.agent

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Opt-in image-to-text recognition. The bundled Latin-script ML Kit model
 * performs recognition on-device; no recognized text or image is uploaded
 * by Astra. Selected photos are not cached in app storage.
 *
 * This is OCR, not image understanding or a multimodal language model.
 */
internal class AstraVision {
    suspend fun recognize(context: Context, selectedPhoto: Uri): String =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, selectedPhoto)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val raw = suspendCancellableCoroutine<String> { continuation ->
                try {
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            if (continuation.isActive) continuation.resume(result.text)
                        }
                        .addOnFailureListener { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                        .addOnCompleteListener { recognizer.close() }
                } catch (error: Exception) {
                    recognizer.close()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            VisionText.clean(raw)
        }
}

/** Extracted text is deliberately bounded to protect the chat and note store. */
internal object VisionText {
    const val MAX_CHARACTERS = 8_000

    fun clean(raw: String): String {
        val trimmed = raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "")
            .trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.length > MAX_CHARACTERS) {
            trimmed.take(MAX_CHARACTERS) + "\n[Text truncated to 8,000 characters]"
        } else trimmed
    }
}
