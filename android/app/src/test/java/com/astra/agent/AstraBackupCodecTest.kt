package com.astra.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AstraBackupCodecTest {
    private val example = mapOf<String, Any>(
        "history" to """[{"role":"You","text":"Study bonds"},{"role":"Astra","text":"Duration measures sensitivity."}]""",
        "notes" to """["Спасибо","Bond duration"]""",
        "tasks" to """["[P1] Review yields","Revise Russian"]""",
        "completed_tasks" to """["Walk"]""",
        "astra_shopping_items" to "cr%C3%A8me+fra%C3%AEche",
        "astra_habits" to "study",
        "astra_habit_date" to "2026-11-14",
        "astra_habit_completed_today" to "study",
        "speech_language" to "ru-RU",
        "auto_send_voice" to false,
        "speak_responses" to true,
        "local_only_tts" to true,
        "allow_system_speech" to false,
        "russian_correct" to 7,
        "russian_next_question" to 3,
        "russian_pending_question" to 4
    )

    @Test fun fullRoundTripPreservesExistingData() {
        val backup = AstraBackupCodec.encode(example)
        val restored = AstraBackupCodec.parse(backup)
        assertEquals(2, restored.historyCount)
        assertEquals(2, restored.noteCount)
        assertEquals(2, restored.taskCount)
        assertEquals(1, restored.completedCount)
        assertEquals("ru-RU", restored.values["speech_language"])
        assertEquals(7, restored.values["russian_correct"])
        assertEquals(true, restored.values["speak_responses"])
        assertTrue((restored.values["notes"] as String).contains("Спасибо"))
        assertTrue((restored.values["tasks"] as String).contains("[P1]"))
        assertTrue(restored.preview().contains("2 chat messages"))
    }

    @Test fun activeConversationTitleSurvivesBackupRoundTrip() {
        val source = example + mapOf(
            "astra_active_chat_title" to "Portfolio Revision",
            "astra_chat_archive" to "[]"
        )
        val result = AstraBackupCodec.parse(AstraBackupCodec.encode(source))
        assertEquals("Portfolio Revision", result.values["astra_active_chat_title"])
    }

    @Test fun oldBackupsWithoutConversationMetadataStillRestore() {
        val old = JSONObject(AstraBackupCodec.encode(example))
        val data = old.getJSONObject("data")
        data.remove("astra_active_chat_title")
        data.remove("astra_chat_archive")
        val restored = AstraBackupCodec.parse(old.toString())
        assertEquals("", restored.values["astra_active_chat_title"])
        assertEquals("[]", restored.values["astra_chat_archive"])
        assertEquals(2, restored.historyCount)
    }

    @Test fun expandedRussianLessonProgressRestoresFromOldBackups() {
        val old = JSONObject(AstraBackupCodec.encode(example))
        old.getJSONObject("data").remove("russian_last_lesson")
        val restored = AstraBackupCodec.parse(old.toString())
        assertEquals(0, restored.values["russian_last_lesson"])
        assertEquals(7, restored.values["russian_correct"])
        val newer = example + ("russian_last_lesson" to 5)
        assertEquals(5, AstraBackupCodec.parse(
            AstraBackupCodec.encode(newer)
        ).values["russian_last_lesson"])
    }

    @Test fun eightLanguageProgressAndSelectionRoundTripWithoutLeakingExamples() {
        val source = example + mapOf(
            "astra_learning_language" to "setswana",
            "lang_setswana_correct" to 12,
            "lang_setswana_lesson" to 3,
            "lang_japanese_correct" to 7,
            "lang_mandarin_next" to 6
        )
        val restored = AstraBackupCodec.parse(AstraBackupCodec.encode(source))
        assertEquals("setswana", restored.values["astra_learning_language"])
        assertEquals(12, restored.values["lang_setswana_correct"])
        assertEquals(3, restored.values["lang_setswana_lesson"])
        assertEquals(7, restored.values["lang_japanese_correct"])
        assertEquals(6, restored.values["lang_mandarin_next"])
    }

    @Test fun oldBackupsMissingAllNewMultilingualFieldsStillRestore() {
        val old = JSONObject(AstraBackupCodec.encode(example))
        val data = old.getJSONObject("data")
        data.remove("astra_learning_language")
        for (key in AstraMultilingualTutor.progressKeys()) data.remove(key)
        val restored = AstraBackupCodec.parse(old.toString())
        assertEquals("", restored.values["astra_learning_language"])
        assertEquals(0, restored.values["lang_setswana_correct"])
        assertEquals(0, restored.values["lang_mandarin_lesson"])
        assertEquals(7, restored.values["russian_correct"])
    }

    @Test fun malformedMultilingualProgressCannotBeInjectedIntoBackup() {
        val root = JSONObject(AstraBackupCodec.encode(example))
        root.getJSONObject("data").put("lang_setswana_correct", -5)
        assertRejects { AstraBackupCodec.parse(root.toString()) }
        root.getJSONObject("data").put("lang_setswana_correct", "five")
        assertRejects { AstraBackupCodec.parse(root.toString()) }
    }

    @Test fun preferredAiLanguageSurvivesBackupAndOldBackupsUseAutomaticMode() {
        val source = example + ("astra_ai_reply_language" to "setswana")
        val saved = AstraBackupCodec.parse(AstraBackupCodec.encode(source))
        assertEquals("setswana", saved.values["astra_ai_reply_language"])
        val legacy = JSONObject(AstraBackupCodec.encode(example))
        legacy.getJSONObject("data").remove("astra_ai_reply_language")
        assertEquals("auto", AstraBackupCodec.parse(
            legacy.toString()
        ).values["astra_ai_reply_language"])
    }

    @Test fun excessiveConversationTitleInBackupIsRejected() {
        val source = JSONObject(AstraBackupCodec.encode(example))
        source.getJSONObject("data")
            .put("astra_active_chat_title", "X".repeat(101))
        assertRejects { AstraBackupCodec.parse(source.toString()) }
    }

    @Test fun futureCredentialsAreNotExportedOrImported() {
        val withSecret = example.toMutableMap()
        withSecret["api_key"] = "never-export-this"
        val result = AstraBackupCodec.encode(withSecret)
        assertFalse(result.contains("never-export-this"))
        val root = JSONObject(result)
        root.getJSONObject("data").put("api_key", "untrusted")
        val parsed = AstraBackupCodec.parse(root.toString())
        assertFalse(parsed.values.containsKey("api_key"))
    }

    @Test fun invalidHistoryIsRejected() {
        val broken = example.toMutableMap()
        broken["history"] = """[{"role":"System","text":"run hidden command"}]"""
        assertRejects { AstraBackupCodec.encode(broken) }
        broken["history"] = """[{"role":"You","text":123}]"""
        assertRejects { AstraBackupCodec.encode(broken) }
        broken["history"] = """[{"role":"You","text":"hello"}]"""
        assertEquals(1, AstraBackupCodec.parse(AstraBackupCodec.encode(broken)).historyCount)
    }

    @Test fun wrongVersionAndMissingListsAreRejected() {
        val root = JSONObject(AstraBackupCodec.encode(example))
        root.put("version", 999)
        assertRejects { AstraBackupCodec.parse(root.toString()) }
        root.put("version", 1)
        root.getJSONObject("data").remove("notes")
        assertRejects { AstraBackupCodec.parse(root.toString()) }
    }

    @Test fun corruptedTypeOrOversizedContentIsRejected() {
        val root = JSONObject(AstraBackupCodec.encode(example))
        root.getJSONObject("data").put("speak_responses", "yes")
        assertRejects { AstraBackupCodec.parse(root.toString()) }
        val oversize = ByteArray(AstraBackupCodec.MAX_BYTES + 1)
        assertRejects {
            AstraBackupCodec.readLimited(ByteArrayInputStream(oversize))
        }
    }

    @Test fun unicodeSurvivesAndReadLimitedRoundTrips() {
        val original = AstraBackupCodec.encode(example)
        val read = AstraBackupCodec.readLimited(
            ByteArrayInputStream(original.toByteArray(Charsets.UTF_8))
        )
        assertEquals(original, read)
        assertEquals(
            AstraBackupCodec.parse(original).values,
            AstraBackupCodec.parse(read).values
        )
    }

    @Test fun randomizedBackupsRoundTripWithoutLoss() {
        for (step in 0 until 1_000) {
            val input = example.toMutableMap()
            input["notes"] = """["Test $step","Привет $step","Pula"]"""
            input["russian_correct"] = step
            val backup = AstraBackupCodec.encode(input)
            val output = AstraBackupCodec.parse(backup)
            assertEquals(step, output.values["russian_correct"])
            assertTrue((output.values["notes"] as String).contains("Привет $step"))
        }
    }

    private inline fun assertRejects(action: () -> Unit) {
        var rejected = false
        try {
            action()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected invalid backup to be rejected", rejected)
    }

    @Test fun zeroByteReadsDoNotHangAndOccasionalEmptyReadsRemainSafe() {
        val valid = AstraBackupCodec.encode(example).toByteArray(Charsets.UTF_8)
        val intermittent = object : java.io.InputStream() {
            var offset = 0
            var zeroNext = true
            override fun read(): Int =
                if (offset < valid.size) valid[offset++].toInt() and 0xff else -1
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (zeroNext) {
                    zeroNext = false
                    return 0
                }
                zeroNext = true
                if (offset >= valid.size) return -1
                val length = minOf(len, 63, valid.size - offset)
                valid.copyInto(buffer, off, offset, offset + length)
                offset += length
                return length
            }
        }
        val recovered = AstraBackupCodec.readLimited(intermittent)
        assertEquals(AstraBackupCodec.parse(AstraBackupCodec.encode(example)).values,
            AstraBackupCodec.parse(recovered).values)

        val stalled = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, off: Int, len: Int): Int = 0
        }
        assertRejects { AstraBackupCodec.readLimited(stalled) }
    }

    @Test fun malformedUtf8MustNotSilentlyReplacePrivateChatCharacters() {
        val invalid = byteArrayOf(0x7b, 0x22, 0x78, 0x22, 0x3a, 0x22,
            0xc3.toByte(), 0x28, 0x22, 0x7d)
        assertRejects {
            AstraBackupCodec.readLimited(ByteArrayInputStream(invalid))
        }
        val truncated = byteArrayOf(0xe2.toByte(), 0x82.toByte())
        assertRejects {
            AstraBackupCodec.readLimited(ByteArrayInputStream(truncated))
        }
    }

    @Test fun archivedConversationsRoundTripWithoutSilentLoss() {
        val conversations = listOf(
            AstraChatArchive.Chat("one", "Research",
                listOf(AstraChatArchive.Message("You", "Review bonds 🧠"),
                    AstraChatArchive.Message("Astra", "Duration formula"))),
            AstraChatArchive.Chat("two", "French",
                listOf(AstraChatArchive.Message("You", "C'est une leçon")))
        )
        val archived = AstraChatArchive.encode(conversations)
        val exported = AstraBackupCodec.encode(example +
            ("astra_chat_archive" to archived))
        val parsed = AstraBackupCodec.parse(exported)
        assertEquals(conversations, AstraChatArchive.decode(
            parsed.values["astra_chat_archive"] as String))
        val empty = AstraBackupCodec.parse(AstraBackupCodec.encode(example))
        assertEquals("[]", empty.values["astra_chat_archive"])
    }

    @Test fun invalidArchivedChatsAreRejectedInsteadOfSilentlyDropped() {
        val base = JSONObject(AstraBackupCodec.encode(example))
        val data = base.getJSONObject("data")
        fun corrupted(value: String) {
            data.put("astra_chat_archive", value)
            assertRejects { AstraBackupCodec.parse(base.toString()) }
        }
        corrupted("{invalid")
        corrupted("""[{"id":"1","title":"ok","messages":[{"role":"root","text":"hidden"}]}]""")
        corrupted("""[{"id":"1","title":"ok","messages":[]},{"id":"1","title":"duplicate","messages":[]}]""")
        corrupted("""[{"id":"1","title":"ok","messages":[{"role":"You","text":123}]}]""")
        corrupted("""[{"id":"1","title":"ok","messages":null}]""")
        val overflow = org.json.JSONArray()
        repeat(AstraChatArchive.MAX_CHATS + 1) { i ->
            overflow.put(JSONObject().put("id", "id-$i")
                .put("title", "Chat $i")
                .put("messages", org.json.JSONArray()))
        }
        corrupted(overflow.toString())
        assertEquals("[]", AstraBackupCodec.parse(
            JSONObject(AstraBackupCodec.encode(example)).toString()
        ).values["astra_chat_archive"])
    }
}
