package com.astra.agent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class AstraModelStorageTest {
    @get:Rule val folder = TemporaryFolder()

    private val name = "qwen-test.gguf"
    private fun write(path: File, data: String) {
        path.writeBytes(data.toByteArray(Charsets.UTF_8))
    }

    @Test fun firstModelInstallationMovesVerifiedFileIntoPrivateDestination() {
        val dir = folder.newFolder("first")
        val temp = File(dir, "new.importing")
        write(temp, "new-verified-model")
        val target = AstraModelStorage.activeModel(dir, name)
        AstraModelStorage.installVerified(temp, target)
        assertFalse(temp.exists())
        assertTrue(target.isFile)
        assertArrayEquals("new-verified-model".toByteArray(), target.readBytes())
    }

    @Test fun replacingModelKeepsOldCopyUntilNewOneIsInstalled() {
        val dir = folder.newFolder("replace")
        val target = File(dir, name)
        write(target, "previous-model")
        val verified = File(dir, "importing")
        write(verified, "new-verified-model")
        AstraModelStorage.installVerified(verified, target)
        assertArrayEquals("new-verified-model".toByteArray(), target.readBytes())
        assertFalse(File(dir, "$name.before-import").exists())
    }

    @Test fun appRestoresOldModelAfterCrashBetweenRenames() {
        val dir = folder.newFolder("recover")
        val backup = File(dir, "$name.before-import")
        write(backup, "previous-model")
        val target = AstraModelStorage.activeModel(dir, name)
        assertTrue(target.isFile)
        assertArrayEquals("previous-model".toByteArray(), target.readBytes())
        assertFalse(backup.exists())
    }

    @Test fun existingInstalledModelIsNeverOverwrittenByOldBackup() {
        val dir = folder.newFolder("installed")
        val target = File(dir, name)
        write(target, "current-good-model")
        val backup = File(dir, "$name.before-import")
        write(backup, "stale-previous-model")
        val resolved = AstraModelStorage.activeModel(dir, name)
        assertArrayEquals("current-good-model".toByteArray(), resolved.readBytes())
    }

    @Test fun missingVerifiedTemporaryFileCannotDestroyInstalledModel() {
        val dir = folder.newFolder("missing")
        val target = File(dir, name)
        write(target, "working")
        val missing = File(dir, "missing.importing")
        val error = runCatching {
            AstraModelStorage.installVerified(missing, target)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertArrayEquals("working".toByteArray(), target.readBytes())
    }

    @Test fun refusesToInstallRecoveryBackupOverCurrentModel() {
        val dir = folder.newFolder("backup-alias")
        val target = File(dir, name)
        val backup = File(dir, "$name.before-import")
        write(target, "working-current-model")
        write(backup, "recoverable-previous-model")

        val error = runCatching {
            AstraModelStorage.installVerified(backup, target)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertArrayEquals("working-current-model".toByteArray(), target.readBytes())
        assertArrayEquals("recoverable-previous-model".toByteArray(), backup.readBytes())
    }

    @Test fun canonicalPathAliasesCannotReplaceInstalledModelWithItself() {
        val dir = folder.newFolder("canonical-alias")
        val target = File(dir, name)
        write(target, "working-current-model")
        val alias = File(dir, "nested/../$name")
        File(dir, "nested").mkdir()

        val error = runCatching {
            AstraModelStorage.installVerified(alias, target)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertArrayEquals("working-current-model".toByteArray(), target.readBytes())
        assertFalse(File(dir, "$name.before-import").exists())
    }

    @Test fun canonicalPathAliasesCannotConsumeRecoveryBackup() {
        val dir = folder.newFolder("backup-path-alias")
        val target = File(dir, name)
        val backup = File(dir, "$name.before-import")
        write(target, "working-current-model")
        write(backup, "recoverable-previous-model")
        File(dir, "nested").mkdir()
        val alias = File(dir, "nested/../$name.before-import")

        val error = runCatching {
            AstraModelStorage.installVerified(alias, target)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertArrayEquals("working-current-model".toByteArray(), target.readBytes())
        assertArrayEquals("recoverable-previous-model".toByteArray(), backup.readBytes())
    }

    @Test fun refusesToOverwriteTheModelWithItself() {
        val dir = folder.newFolder("same")
        val target = File(dir, name)
        write(target, "working")
        val error = runCatching {
            AstraModelStorage.installVerified(target, target)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertArrayEquals("working".toByteArray(), target.readBytes())
    }
}
