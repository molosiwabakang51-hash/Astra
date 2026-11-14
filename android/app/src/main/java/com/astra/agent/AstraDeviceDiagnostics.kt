package com.astra.agent

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File
import java.util.Locale

/**
 * A user-initiated, local-only troubleshooting report.
 *
 * No Android identifiers, installed-app lists, chat history, saved notes,
 * tokens, contacts, location or network requests are used here.
 * The result is copied/shared only after the owner explicitly taps a button.
 */
internal object AstraDeviceDiagnostics {
    data class Snapshot(
        val version: String,
        val manufacturer: String,
        val model: String,
        val androidRelease: String,
        val sdk: Int,
        val supportedAbis: List<String>,
        val freeStorageBytes: Long?,
        val availableRamBytes: Long?,
        val totalRamBytes: Long?,
        val maxJavaHeapBytes: Long?,
        val bundledModelBytes: Long?,
        val extractedModelBytes: Long?,
        val selfTestResult: String
    )

    fun capture(context: Context, version: String, selfTestStatus: String): Snapshot {
        val appContext = context.applicationContext
        val memory = runCatching {
            val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }.getOrNull()

        val freeStorage = runCatching {
            StatFs(appContext.noBackupFilesDir.absolutePath).availableBytes
        }.getOrNull()

        // The model is stored uncompressed inside the APK. openFd() reads its
        // metadata without allocating hundreds of megabytes in RAM.
        val assetBytes = runCatching {
            appContext.assets.openFd(AstraOnDeviceBrain.ASSET_NAME).use {
                it.length
            }
        }.getOrNull()?.takeIf { it > 0L }

        val privateModel = File(
            File(appContext.noBackupFilesDir, "offline-models"),
            AstraOnDeviceBrain.ASSET_NAME
        )
        val extractedBytes = if (privateModel.isFile) {
            privateModel.length()
        } else null

        return Snapshot(
            version = version,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            freeStorageBytes = freeStorage,
            availableRamBytes = memory?.availMem,
            totalRamBytes = memory?.totalMem,
            maxJavaHeapBytes = Runtime.getRuntime().maxMemory(),
            bundledModelBytes = assetBytes,
            extractedModelBytes = extractedBytes,
            selfTestResult = summarizeSelfTest(selfTestStatus)
        )
    }

    /** Never include arbitrary model text in the shareable diagnostic. */
    fun summarizeSelfTest(status: String): String {
        val elapsed = Regex("""^OFFLINE TEST VERIFIED in (\d+) ms:""")
            .find(status)?.groupValues?.getOrNull(1)
        if (elapsed != null) return "PASS (${elapsed.take(10)} ms)"
        if (status.startsWith("OFFLINE TEST UNVERIFIED:")) {
            return "UNVERIFIED (challenge not followed)"
        }
        if (status.startsWith("OFFLINE TEST FAILED:")) {
            // The error class is enough to identify most failure categories.
            // Avoid sharing raw exceptions containing device-local file paths.
            val message = status.substringAfter("OFFLINE TEST FAILED:").trim()
            val reason = when {
                "storage" in message.lowercase(Locale.ROOT) -> "storage unavailable"
                "integrity" in message.lowercase(Locale.ROOT) -> "model integrity"
                "timeout" in message.lowercase(Locale.ROOT) ||
                    "timed out" in message.lowercase(Locale.ROOT) -> "model startup timed out"
                "unsupported" in message.lowercase(Locale.ROOT) -> "unsupported model or CPU"
                "memory" in message.lowercase(Locale.ROOT) -> "memory allocation"
                else -> "inference error (see on-screen message)"
            }
            return "FAIL ($reason)"
        }
        if (status.startsWith("LOADING AND TESTING")) return "IN PROGRESS"
        return "NOT RUN"
    }

    fun render(snapshot: Snapshot): String = buildString {
        appendLine("ASTRA · OFFLINE AI DIAGNOSTICS")
        appendLine("Generated on the phone; never sent automatically.")
        appendLine()
        appendLine("Astra version: ${snapshot.version}")
        appendLine("Device: ${snapshot.manufacturer} ${snapshot.model}".trim())
        appendLine("Android: ${snapshot.androidRelease} (API ${snapshot.sdk})")
        appendLine("Supported ABIs: ${snapshot.supportedAbis.joinToString().ifBlank { "Unknown" }}")
        appendLine("Model: ${AstraOnDeviceBrain.MODEL_LABEL}")
        appendLine("Model bundled in APK: ${formatBytes(snapshot.bundledModelBytes)}")
        appendLine("Model extracted privately: ${formatBytes(snapshot.extractedModelBytes)}")
        appendLine("Free app storage: ${formatBytes(snapshot.freeStorageBytes)}")
        appendLine("Available device RAM: ${formatBytes(snapshot.availableRamBytes)}")
        appendLine("Total device RAM: ${formatBytes(snapshot.totalRamBytes)}")
        appendLine("Max Java heap (not native model RAM): ${formatBytes(snapshot.maxJavaHeapBytes)}")
        appendLine("Offline AI self-test: ${snapshot.selfTestResult}")
        appendLine()
        appendLine("No messages, notes, tasks, location, account identifiers,")
        appendLine("passwords or access tokens are included.")
        appendLine("Review this report before sharing it.")
    }.trimEnd()

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "Unavailable"
        val base = 1024.0
        return if (bytes >= 1024L * 1024L * 1024L) {
            String.format(Locale.US, "%.2f GiB", bytes / (base * base * base))
        } else {
            String.format(Locale.US, "%.1f MiB", bytes / (base * base))
        }
    }
}
