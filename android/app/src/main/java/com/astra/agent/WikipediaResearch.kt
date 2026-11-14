package com.astra.agent

import android.text.Html
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/**
 * Only explicit commands trigger an online request.
 *
 * This feature sends the user's requested public topic to the Wikimedia APIs;
 * it never uploads local notes, tasks, conversations or financial information.
 */
internal object ResearchCommand {
    // A request to research and *learn* is an assistant conversation, not
    // permission to send the remaining words as a Wikimedia search query.
    // Explicit Wikipedia commands still work without relying on the LLM.
    private val wikipedia = Regex(
        """(?i)^(?:wikipedia|search wikipedia for|look up on wikipedia)\\s+(.+)$"""
    )
    private val research = Regex("""(?i)^research\\s+(.+)$""")
    private val chainedInstruction = Regex(
        """(?i)^(?:and|then|to)\\s+(?:learn|study|understand|explore|research|investigate|read|remember)\\b.*$"""
    )
    private val personalContext = Regex(
        """(?i)^(?:(?:my|our|private|personal|saved)\\s+).*$"""
    )
    private val unboundedTopics = setOf(
        "everything", "anything", "all knowledge", "all topics",
        "every subject", "the entirety of human knowledge"
    )

    fun extract(raw: String): String? {
        val text = raw.trim().replace(Regex("""\\s+"""), " ")
        wikipedia.matchEntire(text)?.groupValues?.getOrNull(1)?.trim()?.let {
            return it.takeIf(String::isNotBlank)
        }
        val topic = research.matchEntire(text)?.groupValues?.getOrNull(1)
            ?.trim() ?: return null
        // Treat broad goals and requests involving the user's personal records
        // as conversation. Do not silently upload them to Wikipedia.
        if (topic.isBlank() || chainedInstruction.matches(topic) ||
            personalContext.matches(topic) ||
            topic.lowercase(java.util.Locale.ROOT) in unboundedTopics
        ) return null
        return topic
    }
}

internal class WikipediaResearch {
    suspend fun search(topic: String): String = withContext(Dispatchers.IO) {
        if (topic.length > 160) {
            return@withContext "Please shorten your research topic to 160 characters."
        }

        try {
            queryWikipedia(topic)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            "Wikipedia could not be reached. Check your internet connection and try again."
        } catch (_: Exception) {
            "Wikipedia returned an unexpected response. Please try a more specific topic."
        }
    }

    private fun queryWikipedia(topic: String): String {
        val query = URLEncoder.encode(topic, "UTF-8")
        val url = URL(
            "https://en.wikipedia.org/w/rest.php/v1/search/page?q=" +
                query + "&limit=2"
        )
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 9000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "AstraAndroid/0.9.2 (https://github.com/molosiwabakang51-hash/Astra)"
            )
        }

        try {
            val status = connection.responseCode
            if (status == 429) {
                return "Wikipedia is rate limiting requests. Try again later."
            }
            if (status != 200) {
                return "Wikipedia could not complete the search (HTTP " + status + ")."
            }

            val body = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val pages = JSONObject(body).optJSONArray("pages")
                ?: return "Wikipedia did not return any search results."
            if (pages.length() == 0) {
                return "No Wikipedia articles matched '" + topic + "'. Try a different search."
            }

            val sections = mutableListOf<String>()
            for (index in 0 until minOf(pages.length(), 2)) {
                val page = pages.optJSONObject(index) ?: continue
                val title = page.optString("title").trim()
                if (title.isBlank()) continue
                val key = page.optString("key", title)
                val description = page.optString("description")
                    .takeUnless { it == "null" }
                    .orEmpty()
                    .trim()
                val excerptHtml = page.optString("excerpt")
                val excerpt = Html.fromHtml(
                    excerptHtml,
                    Html.FROM_HTML_MODE_COMPACT
                ).toString().replace(Regex("""\s+"""), " ").trim().take(450)
                val summary = if (excerpt.isNotBlank()) excerpt else description

                // Key is URL encoded and the hostname is fixed, so search results
                // cannot inject an arbitrary destination.
                val articlePath = URLEncoder.encode(
                    key.replace(' ', '_'), "UTF-8"
                ).replace("+", "%20")

                sections.add(
                    (index + 1).toString() + ". " + title + "\n" +
                        (if (summary.isNotBlank()) summary + "\n" else "") +
                        "Source: https://en.wikipedia.org/wiki/" + articlePath
                )
            }

            if (sections.isEmpty()) {
                return "Wikipedia returned no readable articles for '" + topic + "'."
            }

            return "Wikipedia search — " + topic + "\n\n" +
                sections.joinToString("\n\n") +
                "\n\nSearch excerpts are not full answers and may be outdated. " +
                "Source: Wikipedia; article text is generally CC BY-SA. " +
                "Open the linked articles to check sources, context and licensing."
        } finally {
            connection.disconnect()
        }
    }
}
