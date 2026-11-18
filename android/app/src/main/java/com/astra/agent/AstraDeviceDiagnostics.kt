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
        val selfTestResult: String,
        val loadedModelName: String? = null,
        val advancedCandidateBytes: Long? = null,
        val languageBenchmarkResult: String = "NOT RUN",
        val installedBaseApkBytes: Long? = null
    )

    fun capture(
        context: Context,
        version: String,
        selfTestStatus: String,
        loadedModelName: String? = null,
        languageBenchmarkStatus: String = ""
    ): Snapshot {
        val appContext = context.applicationContext
        val memory = runCatching {
            val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }.getOrNull()

        val freeStorage = runCatching {
            StatFs(appContext.noBackupFilesDir.absolutePath).availableBytes
        }.getOrNull()
        // Android may install split APKs; this is the on-device base APK only.
        // Never log or export its private filesystem path.
        val baseApkBytes = runCatching {
            File(appContext.applicationInfo.sourceDir).takeIf { it.isFile }?.length()
        }.getOrNull()?.takeIf { it > 0L }

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
        val advancedCandidate = File(
            File(appContext.noBackupFilesDir, "offline-models"),
            AstraModelImport.ADVANCED_NAME
        ).takeIf { it.isFile }?.length()

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
            selfTestResult = summarizeSelfTest(selfTestStatus),
            loadedModelName = verifiedLoadedName(loadedModelName),
            advancedCandidateBytes = advancedCandidate,
            languageBenchmarkResult = summarizeLanguageBenchmark(languageBenchmarkStatus),
            installedBaseApkBytes = baseApkBytes
        )
    }

    /**
     * Only static, known model labels are allowed in a shareable report.
     * Never interpolate model paths or arbitrary model output here.
     */
    fun verifiedLoadedName(candidate: String?): String? =
        candidate?.takeIf { it in setOf(
            "Qwen3 0.6B · Q4_K_M", "Qwen3 1.7B · Q4_K_M"
        ) }

    /** Parse the numerical summary, not raw model answers or exception text. */
    fun summarizeLanguageBenchmark(status: String): String {
        val count = Regex("""(?m)^(\d{1,2})/(\d{1,2}) checks passed\.$""")
            .find(status)
        if (status.startsWith("ON-DEVICE LANGUAGE SPOT CHECKS") && count != null) {
            val passed = count.groupValues[1].toInt()
            val total = count.groupValues[2].toInt()
            if (total == AstraLanguageBenchmark.Probe.entries.size && passed in 0..total) {
                return "RECORDED ($passed/$total narrow checks; not a general AI score)"
            }
        }
        return when {
            status.startsWith("Running ") -> "IN PROGRESS"
            status.startsWith("Conversation checks interrupted:") -> "INTERRUPTED"
            status.startsWith("Conversation checks cancelled.") -> "CANCELLED"
            else -> "NOT RUN / UNVERIFIED"
        }
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
        appendLine("Installed base APK: ${formatDecimalMb(snapshot.installedBaseApkBytes)}")
        appendLine("LITE downloadable APK maximum: 300.00 MB (300,000,000 bytes).")
        appendLine("The LITE cap excludes separately imported GGUF models,")
        appendLine("user data and other installed storage; a FULL APK is not LITE.")
        appendLine("Installed base APK size may exclude Android split APKs.")
        appendLine("Device: ${snapshot.manufacturer} ${snapshot.model}".trim())
        appendLine("Android: ${snapshot.androidRelease} (API ${snapshot.sdk})")
        appendLine("Supported ABIs: ${snapshot.supportedAbis.joinToString().ifBlank { "Unknown" }}")
        appendLine("Model last loaded: ${snapshot.loadedModelName ?: "Not verified in this session"}")
        appendLine("Compact model bundled in APK: ${formatBytes(snapshot.bundledModelBytes)}")
        appendLine("Private compact candidate: ${formatBytes(snapshot.extractedModelBytes)}")
        appendLine("Private 1.7B candidate: ${formatBytes(snapshot.advancedCandidateBytes)}")
        appendLine("Candidate file sizes are not integrity or inference proof.")
        appendLine("Free app storage: ${formatBytes(snapshot.freeStorageBytes)}")
        appendLine("Available device RAM: ${formatBytes(snapshot.availableRamBytes)}")
        appendLine("Total device RAM: ${formatBytes(snapshot.totalRamBytes)}")
        appendLine("Max Java heap (not native model RAM): ${formatBytes(snapshot.maxJavaHeapBytes)}")
        appendLine("Offline AI self-test: ${snapshot.selfTestResult}")
        appendLine("Language spot checks: ${snapshot.languageBenchmarkResult}")
        appendLine()
        appendLine("No messages, notes, tasks, location, account identifiers,")
        appendLine("passwords or access tokens are included.")
        appendLine("Review this report before sharing it.")
    }.trimEnd()

    /**
     * APK distribution uses decimal MB (1 MB = 1,000,000 bytes).
     * This value is not the package ZIP size and excludes separately imported
     * model weights and user files.
     */
    fun formatDecimalMb(bytes: Long?): String =
        if (bytes == null || bytes < 0L) "Unavailable"
        else String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)

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
