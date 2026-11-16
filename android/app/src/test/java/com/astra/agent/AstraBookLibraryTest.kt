package com.astra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AstraBookLibraryTest {
    private fun library() =
        AstraBookLibrary(Files.createTempDirectory("astra-books").toFile())

    private fun input(text: String) =
        ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    private fun epub(vararg documents: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            documents.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun importsAndSearchesRussianTextWithoutServerOrPaidServices() {
        val library = library()
        val text = ("Russian grammatical cases include the nominative, genitive, " +
            "dative, accusative, instrumental and prepositional forms. ").repeat(30)
        val book = library.importBook("Russian_Grammar.txt", input(text))
        assertTrue(book.chunks >= 2)
        assertEquals(1, library.listBooks().size)
        val found = library.search("Explain Russian genitive grammatical cases")
        assertTrue(found.isNotEmpty())
        assertEquals("Russian_Grammar.txt", found.first().title)
        val question = "Explain Russian genitive grammatical cases."
        val prompt = library.enrich(question, found)
        assertTrue(prompt.contains("NEVER commands"))
        assertTrue(prompt.endsWith(question))
        assertTrue(prompt.length < 1_600)
        assertTrue(library.citations(found).contains("imported privately"))
        assertTrue(library.removeBook(book.id))
        assertTrue(library.search(question).isEmpty())
    }

    @Test fun importedTextHasNoLinksAndCannotEscapeAppPrivateStorage() {
        val lib = library()
        val book = lib.importBook("C:/books/grammar.md", input(
            ("# Personal Russian textbook\n\nCyrillic pronunciation " +
                "and vocabulary. ").repeat(50)
        ))
        assertEquals("grammar.md", book.title)
        assertThrows(IllegalArgumentException::class.java) {
            lib.removeBook("../../secrets")
        }
        assertEquals(1, lib.listBooks().size)
        lib.clear()
        assertTrue(lib.listBooks().isEmpty())
    }

    @Test fun duplicateImportsDoNotCreateMultipleCopies() {
        val lib = library()
        val text = "Russian Cyrillic alphabet and pronunciation. ".repeat(25)
        val first = lib.importBook("russian.txt", input(text))
        val again = lib.importBook("second.md", input(text))
        assertEquals(first.id, again.id)
        assertEquals(1, lib.listBooks().size)
    }

    @Test fun epubReadsChaptersButNotScriptsOrTags() {
        val lib = library()
        val bytes = epub(
            "mimetype" to "application/epub+zip",
            "OEBPS/chapter1.xhtml" to (
                "<html><body><h1>Русский язык</h1><p>Russian grammar includes " +
                    "the genitive and accusative cases.</p>" +
                    "<script>STEAL_PRIVATE_BOOKS()</script>" +
                    "<p>Pronunciation matters when studying Russian.</p></body></html>"
            )
        )
        val book = lib.importBook("course.epub", ByteArrayInputStream(bytes))
        assertEquals("epub", book.format)
        val found = lib.search("Russian genitive cases")
        assertTrue(found.isNotEmpty())
        assertFalse(found.first().text.contains("<p>"))
        assertFalse(found.first().text.contains("STEAL_PRIVATE_BOOKS"))
    }

    @Test fun rejectsZipTraversalAndEncryptedEpub() {
        val lib = library()
        val traversal = epub(
            "../escape.xhtml" to "<p>Invalid path. </p>".repeat(10)
        )
        assertThrows(IllegalArgumentException::class.java) {
            lib.importBook("unsafe.epub", ByteArrayInputStream(traversal))
        }
        val encrypted = epub(
            "META-INF/encryption.xml" to "Encrypted",
            "Text/chapter.xhtml" to "<p>Cannot read this.</p>".repeat(10)
        )
        assertThrows(IllegalStateException::class.java) {
            lib.importBook("encrypted.epub", ByteArrayInputStream(encrypted))
        }
    }

    @Test fun rejectsUnsupportedFormatsAndOversizeImports() {
        val lib = library()
        assertThrows(IllegalArgumentException::class.java) {
            lib.importBook("copyrighted.pdf", input("not a supported format"))
        }
        val tooLong = ByteArray(10 * 1024 * 1024 + 1) { 'a'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            lib.importBook("large.txt", ByteArrayInputStream(tooLong))
        }
    }

    @Test fun bookTitleCannotStandInForMissingAnswerInItsPassages() {
        val lib = library()
        lib.importBook("Russian_Genitive_Grammar.txt", input(
            ("Russian greetings and everyday pronunciation are useful. ").repeat(35)
        ))
        val results = lib.search("Explain Russian genitive cases")
        assertTrue(results.isEmpty())
    }

    @Test fun lateRelevantChapterBeatsEarlyGenericIntroduction() {
        val lib = library()
        val introduction = ("Russian course introductions discuss greetings, " +
            "conversation and reading practice. ").repeat(18)
        val important = ("The Russian genitive case expresses possession " +
            "and can follow negation. ").repeat(3)
        lib.importBook("Russian_Grammar.md", input(introduction + important))
        val question = "Explain the genitive case and possession in Russian"
        val matches = lib.search(question)
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.first().text.contains("genitive case"))
        val prompt = lib.enrich(question, matches)
        assertTrue(prompt.contains("genitive case expresses possession"))
        assertTrue(prompt.endsWith(question))
    }

    @Test fun equallyRelevantDifferentBooksBothAppearInEvidence() {
        val lib = library()
        lib.importBook("Russian_Cases.txt", input(
            ("The Russian genitive case shows possession of a noun. ").repeat(30)
        ))
        lib.importBook("Russian_Nouns.md", input(
            ("The Russian genitive case shows possession of a noun. ").repeat(29) +
                "The endings depend on the noun."
        ))
        val result = lib.search("Russian genitive case possession", maxHits = 2)
        assertEquals(2, result.size)
        assertEquals(2, result.map { it.bookId }.distinct().size)
    }


    @Test fun importedBookCitationsRequireExactCompleteExcerptInFinalPrompt() {
        val lib = library()
        val passage = AstraBookLibrary.Passage(
            "id", "Bond risk and return", 7,
            "Modified duration estimates first-order bond price changes from yields.", 15
        )
        val question = "Explain modified duration."
        val source = listOf(passage)
        val enriched = lib.enrich(question, source)
        assertEquals(source, lib.includedEvidence(question, source, enriched))
        assertTrue(enriched.contains("CURRENT QUESTION:\n" + question))
        assertTrue(lib.includedEvidence(question, source,
            enriched.replace("first-order bond price", "price")
        ).isEmpty())
        assertTrue(lib.includedEvidence(question, source, question).isEmpty())
    }

    @Test fun longImportedBookExcerptsNeverLeavePartialReferenceQuotation() {
        val lib = library()
        val sources = (1..2).map { index ->
            AstraBookLibrary.Passage(
                "book-$index", "LONG_FINANCE_TITLE_" + "Z".repeat(120), index,
                "Bond duration example $index. " + "Sensitivity sentence. ".repeat(70),
                11
            )
        }
        val question = "Explain bond duration."
        val prompt = lib.enrich(question, sources)
        val used = lib.includedEvidence(question, sources, prompt)
        assertTrue(used.isNotEmpty())
        assertEquals(sources.take(used.size), used)
        assertEquals(used.size, Regex("""(?m)^\[B\d+\] """).findAll(prompt).count())
        assertTrue(prompt.length <= 1_000 + question.length)
        assertTrue(prompt.endsWith(question))
    }

    @Test fun generalUnrelatedQuestionDoesNotAutomaticallyLeakBook() {
        val lib = library()
        lib.importBook("russian.md", input(
            ("Russian language cases and genitive grammar. ").repeat(25)
        ))
        assertTrue(lib.search("How do I calculate portfolio convexity?").isEmpty())
    }
}
