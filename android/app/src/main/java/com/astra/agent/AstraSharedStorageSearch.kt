package com.astra.agent

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Locale

/**
 * Strictly read-only, bounded metadata search of Android shared storage.
 *
 * The Android layer must verify the owner's one-time All Files Access grant
 * immediately before calling search(). No media bytes are copied, parsed,
 * uploaded, or persisted. This index cannot access other apps' private data.
 */
internal object AstraSharedStorageSearch {
    private const val MAX_QUERY = 100
    private const val MAX_RESULTS = 20
    private const val MAX_VISITED = 20_000
    private const val MAX_DEPTH = 9
    private const val MAX_DURATION_MS = 4_000L

    private val command = Regex(
        """(?i)^(?:search (?:my )?phone (?:for )?|find (?:a )?file (?:on (?:my )?phone )?|find (?:on|in) (?:my )?phone )(.+)$"""
    )

    data class Hit(val relativePath: String, val bytes: Long)
    data class Result(
        val hits: List<Hit>,
        val visited: Int,
        val limited: Boolean
    ) {
        fun render(query: String): String {
            val header = "ON-DEVICE SHARED FILE SEARCH: " + query.take(MAX_QUERY)
            if (hits.isEmpty()) return header + "\nNo matching accessible files found." +
                if (limited) "\nScan limit reached; this is not a complete search." else ""
            return buildString {
                appendLine(header)
                hits.forEach { hit ->
                    appendLine("• " + hit.relativePath + " (" +
                        (hit.bytes / 1024L) + " KiB)")
                }
                append("These are filenames only, not extracted document or media contents.")
                if (limited) append("\nScan limit reached; some files were not checked.")
            }
        }
    }

    fun parse(raw: String): String? {
        val match = command.matchEntire(raw.trim().trimEnd('?', '.', '!'))
            ?: return null
        val query = match.groupValues[1].trim()
        return query.takeIf {
            it.length in 2..MAX_QUERY &&
                it.none { c -> c.code < 32 || c == '/' || c == '\\' }
        }
    }

    fun search(root: File, term: String): Result {
        require(term.length in 2..MAX_QUERY)
        require(root.isDirectory) { "Shared storage is unavailable." }
        val wanted = term.lowercase(Locale.ROOT)
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.add(root to 0)
        val matches = ArrayList<Hit>()
        val started = System.nanoTime()
        var visited = 0
        var limited = false
        while (stack.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted ||
                (System.nanoTime() - started) / 1_000_000L > MAX_DURATION_MS ||
                visited >= MAX_VISITED
            ) {
                limited = true
                break
            }
            val (directory, depth) = stack.removeLast()
            if (Files.isSymbolicLink(directory.toPath())) continue
            val entries = directory.listFiles() ?: continue
            for (entry in entries) {
                visited++
                if (visited > MAX_VISITED ||
                    matches.size >= MAX_RESULTS ||
                    (System.nanoTime() - started) / 1_000_000L > MAX_DURATION_MS
                ) {
                    limited = true
                    break
                }
                if (Files.isSymbolicLink(entry.toPath())) continue
                val name = entry.name
                if (name.startsWith('.') || name == "Android" ||
                    name.equals("obb", true) || name.equals("data", true)
                ) continue
                if (entry.isDirectory) {
                    if (depth < MAX_DEPTH) stack.add(entry to depth + 1)
                    continue
                }
                if (!entry.isFile || !name.lowercase(Locale.ROOT).contains(wanted)) {
                    continue
                }
                val relative = root.toPath().relativize(entry.toPath()).toString()
                    .replace(Regex("""[\r\n\t]+"""), " ").take(210)
                matches.add(Hit(relative, entry.length().coerceAtLeast(0L)))
            }
            if (limited) break
        }
        return Result(matches, visited, limited)
    }
}
