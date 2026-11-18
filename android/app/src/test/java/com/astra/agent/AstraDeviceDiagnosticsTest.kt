package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDeviceDiagnosticsTest {
    @Test fun passStatusIncludesDurationButNotModelGeneratedText() {
        val summary = AstraDeviceDiagnostics.summarizeSelfTest(
            "OFFLINE TEST VERIFIED in 1337 ms: ASTRA OFFLINE"
        )
        assertEquals("PASS (1337 ms)", summary)
        assertFalse(summary.contains("arbitrary"))
    }

    @Test fun failureSummaryOmitsFullExceptionAndLocalPaths() {
        val status = "OFFLINE TEST FAILED: Model integrity check at " +
            "/data/user/0/com.astra.agent/secret failed"
        val summary = AstraDeviceDiagnostics.summarizeSelfTest(status)
        assertEquals("FAIL (model integrity)", summary)
        assertFalse(summary.contains("/data"))
        assertEquals(
            "FAIL (inference error (see on-screen message))",
            AstraDeviceDiagnostics.summarizeSelfTest(
                "OFFLINE TEST FAILED: private key = should_not_share"
            )
        )
    }

    @Test fun unverifiedModelOutputIsNotReportedAsPass() {
        val summary = AstraDeviceDiagnostics.summarizeSelfTest(
            "OFFLINE TEST UNVERIFIED: Model generated text in 500 ms but did not follow challenge."
        )
        assertEquals("UNVERIFIED (challenge not followed)", summary)
    }

    @Test fun testNotRunOrInProgressIsExplicit() {
        assertEquals("NOT RUN", AstraDeviceDiagnostics.summarizeSelfTest(""))
        assertEquals("NOT RUN", AstraDeviceDiagnostics.summarizeSelfTest("unknown text"))
        assertEquals(
            "IN PROGRESS",
            AstraDeviceDiagnostics.summarizeSelfTest(
                "LOADING AND TESTING LOCAL MODEL..."
            )
        )
    }

    @Test fun reportContainsOnlyDiagnosticMetadata() {
        val snapshot = AstraDeviceDiagnostics.Snapshot(
            version = "0.10.3",
            manufacturer = "Example",
            model = "TestDevice",
            androidRelease = "16",
            sdk = 36,
            supportedAbis = listOf("arm64-v8a"),
            freeStorageBytes = 5L * 1024 * 1024 * 1024,
            availableRamBytes = 3L * 1024 * 1024 * 1024,
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            maxJavaHeapBytes = 256L * 1024 * 1024,
            bundledModelBytes = 484L * 1024 * 1024,
            extractedModelBytes = null,
            selfTestResult = "NOT RUN",
            installedBaseApkBytes = 65_090_210L
        )
        val report = AstraDeviceDiagnostics.render(snapshot)
        assertTrue(report.contains("Astra version: 0.10.3"))
        assertTrue(report.contains("Device: Example TestDevice"))
        assertTrue(report.contains("Installed base APK: 65.09 MB"))
        assertTrue(report.contains("LITE downloadable APK maximum: 300.00 MB"))
        assertTrue(report.contains("The LITE cap excludes separately imported GGUF models"))
        assertTrue(report.contains("Installed base APK size may exclude Android split APKs"))
        assertFalse(report.contains("/data/user/0"))
        assertTrue(report.contains("Supported ABIs: arm64-v8a"))
        assertTrue(report.contains("Free app storage: 5.00 GiB"))
        assertTrue(report.contains("Private compact candidate: Unavailable"))
        assertTrue(report.contains("Private 1.7B candidate: Unavailable"))
        assertTrue(report.contains("Model last loaded: Not verified in this session"))
        assertTrue(report.contains("Language spot checks: NOT RUN"))
        assertTrue(report.contains("Offline AI self-test: NOT RUN"))
        assertFalse(report.contains("history"))
        assertFalse(report.contains("Bearer "))
        assertFalse(report.contains("saved text:"))
    }

    @Test fun reportNeverPretendsAdvancedSessionUsedCompactModel() {
        val snapshot = AstraDeviceDiagnostics.Snapshot(
            version = "0.12.1", manufacturer = "Example", model = "ARM64",
            androidRelease = "16", sdk = 36,
            supportedAbis = listOf("arm64-v8a"),
            freeStorageBytes = null, availableRamBytes = null,
            totalRamBytes = null, maxJavaHeapBytes = null,
            bundledModelBytes = 520_000_000L,
            extractedModelBytes = 520_000_000L,
            selfTestResult = "PASS (1500 ms)",
            loadedModelName = "Qwen3 1.7B · Q4_K_M",
            advancedCandidateBytes = 1_275_000_000L,
            languageBenchmarkResult = "RECORDED (5/7 narrow checks; not a general AI score)"
        )
        val report = AstraDeviceDiagnostics.render(snapshot)
        assertTrue(report.contains("Model last loaded: Qwen3 1.7B"))
        assertTrue(report.contains("Compact model bundled in APK:"))
        assertTrue(report.contains("Private 1.7B candidate:"))
        assertTrue(report.contains("Language spot checks: RECORDED (5/7"))
        assertTrue(report.contains("sizes are not integrity or inference proof"))
    }

    @Test fun onlyKnownModelLabelsMayBeShared() {
        assertEquals("Qwen3 1.7B · Q4_K_M",
            AstraDeviceDiagnostics.verifiedLoadedName("Qwen3 1.7B · Q4_K_M"))
        assertEquals("Qwen3 0.6B · Q4_K_M",
            AstraDeviceDiagnostics.verifiedLoadedName("Qwen3 0.6B · Q4_K_M"))
        assertEquals(null, AstraDeviceDiagnostics.verifiedLoadedName(
            "/data/user/0/com.astra.agent/secrets/private.gguf"))
        assertEquals(null, AstraDeviceDiagnostics.verifiedLoadedName("Custom model"))
        assertEquals(null, AstraDeviceDiagnostics.verifiedLoadedName(null))
    }

    @Test fun languageScoresAreSanitizedWithoutArbitraryModelOutput() {
        val total = AstraLanguageBenchmark.Probe.entries.size
        val passes = total / 2
        val raw = "ON-DEVICE LANGUAGE SPOT CHECKS\n" +
            "Model loaded: Qwen3 1.7B · Q4_K_M\n" +
            "$passes/$total checks passed.\n" +
            "Model output: MY_PRIVATE_SECRET_OR_TOKEN"
        val safe = AstraDeviceDiagnostics.summarizeLanguageBenchmark(raw)
        assertEquals("RECORDED ($passes/$total narrow checks; not a general AI score)", safe)
        assertFalse(safe.contains("PRIVATE_SECRET"))
        assertEquals("NOT RUN / UNVERIFIED",
            AstraDeviceDiagnostics.summarizeLanguageBenchmark(
                "$passes/$total checks passed.\nMY_PRIVATE_SECRET_OR_TOKEN"))
        assertEquals("NOT RUN / UNVERIFIED",
            AstraDeviceDiagnostics.summarizeLanguageBenchmark(
                "ON-DEVICE LANGUAGE SPOT CHECKS\n" +
                    (total + 1) + "/$total checks passed."))
        assertEquals("IN PROGRESS",
            AstraDeviceDiagnostics.summarizeLanguageBenchmark("Running Russian translation..."))
        assertEquals("INTERRUPTED",
            AstraDeviceDiagnostics.summarizeLanguageBenchmark(
                "Conversation checks interrupted: /data/user/0/secret"))
        assertEquals("CANCELLED",
            AstraDeviceDiagnostics.summarizeLanguageBenchmark(
                "Conversation checks cancelled. Partial results are not scored."))
    }

    @Test fun decimalApkSizesAndUnknownValuesAreClearlyReported() {
        assertEquals("Unavailable", AstraDeviceDiagnostics.formatDecimalMb(null))
        assertEquals("Unavailable", AstraDeviceDiagnostics.formatDecimalMb(-1L))
        assertEquals("0.00 MB", AstraDeviceDiagnostics.formatDecimalMb(0L))
        assertEquals("299.99 MB", AstraDeviceDiagnostics.formatDecimalMb(299_990_000L))
        assertEquals("300.00 MB", AstraDeviceDiagnostics.formatDecimalMb(300_000_000L))
        assertEquals("300.00 MB", AstraDeviceDiagnostics.formatDecimalMb(300_000_001L))
    }

    @Test fun bytesAreFormattedWithoutDeviceAccess() {
        assertEquals("Unavailable", AstraDeviceDiagnostics.formatBytes(null))
        assertEquals("Unavailable", AstraDeviceDiagnostics.formatBytes(-1))
        assertEquals("0.0 MiB", AstraDeviceDiagnostics.formatBytes(0))
        assertEquals("512.0 MiB", AstraDeviceDiagnostics.formatBytes(512L * 1024 * 1024))
        assertEquals("2.50 GiB", AstraDeviceDiagnostics.formatBytes(
            2684354560L
        ))
    }
}
