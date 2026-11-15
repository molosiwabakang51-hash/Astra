package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class AstraVocabularyBundleTest {
    private fun newStore() = AstraVocabularyStore(
        Files.createTempDirectory("astra-bundle-test").toFile()
    )
    private fun input(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    @Test fun portableBackupRestoresMultipleLanguagesOfflineAndPreservesUnicode() {
        val original = newStore()
        original.importTsv("setswana", input("Maru\tClouds\nPula\tRain\n"))
        original.importTsv("russian", input("Счастье\tHappiness\n"))
        original.importTsv("japanese", input("青空\tBlue sky\n"))
        val backup = ByteArrayOutputStream()
        assertEquals(4, AstraVocabularyBundle.export(original, backup))
        assertTrue(backup.toString("UTF-8").startsWith("language\tphrase\ttranslation\n"))

        val restored = newStore()
        val result = AstraVocabularyBundle.restore(
            restored, ByteArrayInputStream(backup.toByteArray())
        )
        assertEquals(4, result.added)
        assertEquals(0, result.skipped)
        assertEquals(3, result.languages)
        for (language in listOf("setswana", "russian", "japanese")) {
            assertEquals(original.list(language), restored.list(language))
        }
        assertTrue(restored.list("french").isEmpty())
    }

    @Test fun repeatedRestoreIsNonDestructiveAndPreservesPriorTranslations() {
        val source = newStore()
        source.importTsv("german", input("Essen\tFood\n"))
        val backup = ByteArrayOutputStream()
        AstraVocabularyBundle.export(source, backup)
        val target = newStore()
        target.importTsv("german", input("Essen\tOriginal meaning\nessen\tTo eat\n"))
        val result = AstraVocabularyBundle.restore(
            target, ByteArrayInputStream(backup.toByteArray())
        )
        assertEquals(0, result.added)
        assertEquals(1, result.skipped)
        assertEquals("Original meaning", target.list("german")[0].translation)
        assertEquals(2, target.list("german").size)
    }

    @Test fun malformedLastRowDoesNotPartiallyRestoreFirstLanguage() {
        val target = newStore()
        target.importTsv("french", input("Bonjour\tHello\n"))
        assertThrows(IllegalArgumentException::class.java) {
            AstraVocabularyBundle.restore(target, input(
                "language\tphrase\ttranslation\n" +
                    "setswana\tMaru\tClouds\n" +
                    "russian\tbroken-without-translation\n"
            ))
        }
        assertTrue(target.list("setswana").isEmpty())
        assertEquals(1, target.list("french").size)
    }

    @Test fun exceedingLanguageCapacityRejectsBeforeAnyWrite() {
        val target = newStore()
        val already = (1..10_000).joinToString("\n") { "item$it\tMeaning$it" }
        target.importTsv("spanish", input(already))
        val source = newStore()
        source.importTsv("setswana", input("Maru\tClouds\n"))
        source.importTsv("spanish", input("new_item\tNew meaning\n"))
        val archive = ByteArrayOutputStream()
        AstraVocabularyBundle.export(source, archive)
        assertThrows(IllegalArgumentException::class.java) {
            AstraVocabularyBundle.restore(target, ByteArrayInputStream(archive.toByteArray()))
        }
        assertTrue(target.list("setswana").isEmpty())
        assertEquals(10_000, target.list("spanish").size)
    }

    @Test fun rejectsInvalidHeadersUnknownLanguagesAndMalformedUtf8() {
        val target = newStore()
        for (data in listOf(
            "phrase\ttranslation\nПривет\tHello\n",
            "language\tphrase\ttranslation\nunsupported\tWord\tMeaning\n",
            "language\tphrase\ttranslation\nfrench\tHello\tBad\textra\n"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                AstraVocabularyBundle.restore(target, input(data))
            }
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            AstraVocabularyBundle.restore(target,
                ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0x0A)))
        }
        assertTrue(target.list("french").isEmpty())
    }

    @Test fun checksumRejectsTruncatedAndTamperedArchivesBeforeRestoring() {
        val source = newStore()
        source.importTsv("french", input("Bonjour\tHello\nMerci\tThank you\n"))
        val full = ByteArrayOutputStream()
        AstraVocabularyBundle.export(source, full)
        val archive = full.toString("UTF-8")
        assertTrue(archive.contains("#astra-private-vocabulary-v1-end"))

        val target = newStore()
        val truncated = archive.substringBefore("#astra-private-vocabulary-v1-end")
        assertThrows(IllegalArgumentException::class.java) {
            AstraVocabularyBundle.restore(target, input(truncated))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AstraVocabularyBundle.restore(
                target, input(archive.replace("Bonjour\tHello", "Bonjour\tGoodbye"))
            )
        }
        assertTrue(target.list("french").isEmpty())
    }

    @Test fun largeExpandedDeckRoundTripsAllTenThousandRussianCards() {
        val source = newStore()
        source.importTsv("russian", input(
            (1..10_000).joinToString("\n") { "слово_$it\tword $it" }
        ))
        val saved = ByteArrayOutputStream()
        assertEquals(10_000, AstraVocabularyBundle.export(source, saved))
        val destination = newStore()
        assertEquals(10_000, AstraVocabularyBundle.restore(
            destination, ByteArrayInputStream(saved.toByteArray())
        ).added)
        assertEquals(source.list("russian"), destination.list("russian"))
    }
}
