package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDeviceDiagnosticsTest {
    @Test fun passStatusIncludesDurationButNotModelGeneratedText() {
        val summary = AstraDeviceDiagnostics.summarizeSelfTest(
            "MODEL RESPONDED OFFLINE in 1337 ms: an arbitrary model response"
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
            selfTestResult = "NOT RUN"
        )
        val report = AstraDeviceDiagnostics.render(snapshot)
        assertTrue(report.contains("Astra version: 0.10.3"))
        assertTrue(report.contains("Device: Example TestDevice"))
        assertTrue(report.contains("Supported ABIs: arm64-v8a"))
        assertTrue(report.contains("Free app storage: 5.00 GiB"))
        assertTrue(report.contains("Model extracted privately: Unavailable"))
        assertTrue(report.contains("Offline AI self-test: NOT RUN"))
        assertFalse(report.contains("history"))
        assertFalse(report.contains("Bearer "))
        assertFalse(report.contains("saved text:"))
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
