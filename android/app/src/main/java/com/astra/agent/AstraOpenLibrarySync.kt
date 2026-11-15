package com.astra.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection

/**
 * Owner-initiated zero-fee sync of FIXED PUBLIC Wikibooks and Wiktionary pages.
 * No chat, notes, learning weights, queries or device identifiers are uploaded.
 * Source metadata is preserved for CC BY-SA attribution.
 */
internal object AstraOpenLibrarySync {
    private data class Page(val title: String, val id: String, val keywords: String, val wiktionary: Boolean = false)
    private val pages = listOf(
        Page("Russian/Alphabet", "ru-alphabet", "russian alphabet cyrillic reading letters"),
        Page("Russian/Introduction", "ru-intro", "russian language introductory course beginner"),
        Page("Russian/Lesson 1", "ru-greetings", "russian greetings introduction names hello"),
        Page("Russian/Grammar/Cases", "ru-cases", "russian grammar cases nouns declension"),
        Page("Russian/Verbal Aspect", "ru-aspect", "russian verbs aspect perfective imperfective"),
        Page("Russian/Grammar", "ru-grammar", "russian grammar verbs cases gender"),
        Page("здравствуй", "ru-dictionary", "russian dictionary hello greetings formal informal", true),
        Page("Russian/Numbers", "ru-numbers", "russian numbers cardinal numerals counting"),
        Page("Russian/Lesson 2", "ru-pronunciation", "russian pronunciation vowels stress"),
        Page("Russian/Grammar/Verbs", "ru-verbs", "russian verbs conjugation present tense"),
        Page("Russian/Grammar/Prepositional case", "ru-prepositional", "russian prepositional case location"),
        Page("спасибо", "ru-thanks", "russian dictionary thank you thanks", true),
        Page("пока", "ru-bye", "russian dictionary informal goodbye bye", true)
    )
    private const val MAX_RESPONSE_BYTES = 90_000
    private const val MAX_EXCERPT = 1_200

    suspend fun refresh(
        context: Context,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val output = File(
            context.noBackupFilesDir, "knowledge/" + AstraOpenLibrary.SYNCED_NAME
        )
        val directory = output.parentFile ?: error("Knowledge storage unavailable.")
        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot prepare local knowledge directory."
        }
        val collected = ArrayList<String>()
        for ((index, item) in pages.withIndex()) {
            currentCoroutineContext().ensureActive()
            // A missing page must not replace an older, working public library.
            val entry = runCatching { download(item) }.getOrNull()
            if (entry != null) collected.add(entry.toString())
            onProgress(index + 1, pages.size)
        }
        check(collected.size >= 4) {
            "Could not retrieve enough freely licensed course pages. " +
                "Existing offline lessons remain available."
        }
        val content = collected.joinToString("\n", postfix = "\n")
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 160_000) { "Public knowledge update exceeds storage bound." }
        val temp = File(directory, output.name + ".partial")
        try {
            FileOutputStream(temp).use { raw ->
                raw.write(bytes)
                raw.fd.sync()
            }
            // Validate the complete new snapshot before swapping files.
            val confirmed = temp.inputStream().use(AstraOpenLibrary::load)
            check(confirmed.size >= 4)
            try {
                Files.move(temp.toPath(), output.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), output.toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
        collected.size
    }

    private fun download(item: Page): JSONObject? {
        val host = if (item.wiktionary) "en.wiktionary.org" else "en.wikibooks.org"
        val encoded = URLEncoder.encode(item.title, "UTF-8")
        val uri = "https://$host/w/api.php?action=query&format=json&formatversion=2" +
            "&prop=extracts%7Crevisions&explaintext=1&exchars=$MAX_EXCERPT" +
            "&rvprop=ids%7Ctimestamp&rvlimit=1&titles=$encoded"
        val connection = URL(uri).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent",
                "AstraAndroid/0.13 (https://github.com/molosiwabakang51-hash/Astra)")
            if (connection.responseCode != 200) return null
            val raw = connection.inputStream.use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
            if (raw.size > MAX_RESPONSE_BYTES) return null
            val response = JSONObject(String(raw, Charsets.UTF_8))
            val result = response.optJSONObject("query")
                ?.optJSONArray("pages")?.optJSONObject(0) ?: return null
            if (result.optBoolean("missing")) return null
            val excerpt = result.optString("extract").trim()
                .replace(Regex("""[\u0000-\u001f]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .take(MAX_EXCERPT)
            if (excerpt.length < 100) return null
            val url = "https://$host/wiki/" +
                item.title.replace(" ", "_").replace("/", "/")
            return JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("url", url)
                .put("text", excerpt)
                .put("keywords", item.keywords)
                .put("license", "CC BY-SA 4.0")
                .put("attribution", "Wikimedia contributors")
                .put("modified", true)
                .put("revision", result.optJSONArray("revisions")
                    ?.optJSONObject(0)?.optLong("revid", 0L) ?: 0L)
        } finally {
            connection.disconnect()
        }
    }
}
