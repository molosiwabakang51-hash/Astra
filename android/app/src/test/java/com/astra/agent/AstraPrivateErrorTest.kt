package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AstraPrivateErrorTest {
    private val operations = AstraPrivateError.Operation.entries

    @Test fun privatePathsUrisAndSignedUrlsAreNeverReturnedForAnyOperation() {
        val secrets = listOf(
            "/storage/emulated/0/Books/Private_Pension_Notes.pdf",
            "content://com.android.providers.downloads.documents/document/secret",
            "https://files.example/model?signature=private-password-token",
            "My secret portfolio allocation: 70% equities"
        )
        operations.forEach { operation ->
            secrets.forEach { secret ->
                val message = AstraPrivateError.forOwner(
                    operation, IOException("Failed to read $secret")
                )
                assertTrue("$operation produced an empty error", message.isNotBlank())
                assertTrue("$operation produced a long error", message.length < 350)
                assertFalse("$operation exposed raw details", message.contains(secret))
                assertFalse(message.contains("private-password-token"))
                assertFalse(message.contains("Private_Pension_Notes"))
            }
        }
    }

    @Test fun storageAndAndroidFilePermissionHaveDistinctRemediation() {
        val outOfSpace = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.BACKUP_EXPORT,
            IOException("ENOSPC /data/user/0/private")
        )
        assertTrue(outOfSpace.contains("storage may be full"))
        assertTrue(outOfSpace.contains("writable destination"))
        assertFalse(outOfSpace.contains("/data/user"))
        val denied = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.BOOK_IMPORT,
            SecurityException("Permission denied: content://private-provider/secret")
        )
        assertTrue(denied.contains("Android did not grant"))
        assertTrue(denied.contains("supported book"))
        assertFalse(denied.contains("private-provider"))
    }

    @Test fun invalidVocabularyOrBackupMessagesRemainActionable() {
        val invalid = IOException("Malformed archive: /sdcard/account-passwords.txt")
        val backup = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.BACKUP_READ, invalid
        )
        assertTrue(backup.contains("selected backup"))
        assertTrue(backup.contains("unsupported or damaged"))
        assertTrue(backup.contains("supported Astra backup"))
        val vocab = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.VOCAB_IMPORT, invalid
        )
        assertTrue(vocab.contains("TSV"))
        assertFalse(vocab.contains("account-passwords"))
    }

    @Test fun inferenceFailureRetainsOnDeviceRecoveryAdviceWithoutPrivatePrompts() {
        val nativeFailure = IllegalStateException(
            "Qwen3 1.7B native model loading failed for " +
                "You asked: my medical records; " +
                "/data/data/com.astra.agent/no_backup/offline-models/private.gguf"
        )
        for (operation in listOf(
            AstraPrivateError.Operation.MODEL_CHAT,
            AstraPrivateError.Operation.MODEL_SELF_TEST,
            AstraPrivateError.Operation.LANGUAGE_CHECKS
        )) {
            val visible = AstraPrivateError.forOwner(operation, nativeFailure)
            assertTrue(visible.contains("verified Qwen3 1.7B"))
            assertFalse(visible.contains("my medical records"))
            assertFalse(visible.contains("private.gguf"))
            assertFalse(visible.contains("You asked"))
        }
        assertTrue(
            AstraPrivateError.forOwner(
                AstraPrivateError.Operation.MODEL_CHAT, nativeFailure
            ).contains("Tasks and notes remain available")
        )
    }

    @Test fun restoreAndClearFailuresMustNotPromiseThatNoDataWasChanged() {
        val failure = IOException("Secret error while writing /private/location")
        for (operation in listOf(
            AstraPrivateError.Operation.BACKUP_RESTORE,
            AstraPrivateError.Operation.VOCAB_RESTORE,
            AstraPrivateError.Operation.VOCAB_CLEAR,
            AstraPrivateError.Operation.BOOK_CLEAR
        )) {
            val message = AstraPrivateError.forOwner(operation, failure)
            assertFalse(message.contains("no changes"))
            assertFalse(message.contains("not deleted"))
            assertFalse(message.contains("all data was preserved"))
            assertFalse(message.contains("/private/"))
        }
        assertTrue(AstraPrivateError.forOwner(
            AstraPrivateError.Operation.BACKUP_RESTORE, failure
        ).contains("Do not clear app data"))
    }

    @Test fun lowMemoryAndPublicNetworkFailuresAreRecognizedWithoutExposingUrl() {
        val memory = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.MODEL_SELF_TEST,
            IllegalStateException("Android low memory: available=42 at /private")
        )
        assertTrue(memory.contains("not have enough available RAM"))
        val timeout = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.LIBRARY_SYNC,
            IOException("Connection timed out at https://host.invalid/?token=private")
        )
        assertTrue(timeout.contains("connection may have timed out"))
        assertFalse(timeout.contains("host.invalid"))
        assertTrue(timeout.contains("public learning resources"))
    }

    @Test fun nestedCausesAreClassifiedButNeverEchoed() {
        val nested = IOException("Private URI content://secret",
            IllegalArgumentException("Insufficient storage; file personal-data.pdf"))
        val text = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.VOCAB_EXPORT, nested
        )
        assertTrue(text.contains("storage may be full"))
        assertFalse(text.contains("personal-data.pdf"))
        assertFalse(text.contains("content://secret"))
    }

    @Test fun workflowAndLinkedFolderErrorsDoNotLeakOwnerContent() {
        val privateUri = "content://private.provider/tree/finance-family-documents"
        val workflow = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.WORKFLOW_SAVE,
            IOException("Unable to commit notes: household income and $privateUri")
        )
        assertTrue(workflow.contains("Workflow could not be saved"))
        assertTrue(workflow.contains("Review the current tasks and notes"))
        assertFalse(workflow.contains("household income"))
        assertFalse(workflow.contains(privateUri))

        val link = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.FOLDER_LINK,
            SecurityException("Denied: $privateUri")
        )
        assertTrue(link.contains("read-only access"))
        assertFalse(link.contains(privateUri))

        val import = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.FOLDER_IMPORT,
            IOException("Cannot read $privateUri")
        )
        assertTrue(import.contains("linked folder"))
        assertFalse(import.contains(privateUri))

        val revoke = AstraPrivateError.forOwner(
            AstraPrivateError.Operation.FOLDER_REVOKE,
            IOException("Could not unlink $privateUri")
        )
        assertTrue(revoke.contains("Review folder authorization"))
        assertFalse(revoke.contains(privateUri))
    }

    @Test fun operationCoverageIsCompleteAndOutputsAreDeterministic() {
        assertEquals(17, operations.size)
        for (operation in operations) {
            val same = IllegalStateException("Unexpected internal failure: /secret/path")
            val first = AstraPrivateError.forOwner(operation, same)
            val second = AstraPrivateError.forOwner(operation, same)
            assertEquals(first, second)
            assertTrue(first.contains("cause could not be identified safely"))
            assertFalse(first.contains("secret"))
        }
    }
}
