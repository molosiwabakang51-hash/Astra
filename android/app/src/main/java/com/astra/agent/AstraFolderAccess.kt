package com.astra.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque
import java.util.Locale

/**
 * An owner-chosen directory, not unrestricted device storage.
 *
 * Android grants the tree once through ACTION_OPEN_DOCUMENT_TREE and Astra
 * retains only READ access. The folder is never scanned in the background:
 * importing books requires an explicit tap in Settings every time.
 */
internal object AstraFolderAccess {
    private const val PREFS = "astra"
    private const val TREE_KEY = "astra_authorized_library_tree"
    private const val MAX_INSPECTED = 500
    private const val MAX_IMPORTS = 30
    data class Document(val name: String, val uri: Uri, val size: Long?)
    data class Result(
        val imported: Int,
        val alreadyPresent: Int,
        val inspected: Int,
        val failures: List<String>,
        val atCapacity: Boolean
    ) {
        fun message(): String = buildString {
            append("Linked-folder book import: ")
            append(imported)
            append(" added, ")
            append(alreadyPresent)
            append(" already indexed")
            if (failures.isNotEmpty()) {
                append(", ")
                append(failures.size)
                append(" failed")
                append(". ")
                append(failures.take(2).joinToString(" | "))
            } else append(".")
            if (atCapacity) append(" Astra's private book library is full (30 books).")
            if (inspected == 0) append(" No supported offline reading documents were found.")
        }
    }

    fun linked(context: Context): Boolean = treeUri(context) != null

    fun treeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TREE_KEY, null) ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
            ?.takeIf { it.scheme == "content" } ?: return null
        return uri.takeIf { hasReadGrant(context, it) }
    }

    private fun hasReadGrant(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

    /** Persist only READ. Replacing the folder releases the previous grant. */
    fun link(context: Context, uri: Uri) {
        require(uri.scheme == "content") { "Choose a folder with Android Files." }
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        check(hasReadGrant(context, uri)) {
            "Android did not retain read access; select the folder again."
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = treeUri(context)
        check(prefs.edit().putString(TREE_KEY, uri.toString()).commit()) {
            "Could not remember authorized folder."
        }
        if (previous != null && previous != uri) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    previous, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun unlink(context: Context) {
        val old = treeUri(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().remove(TREE_KEY).commit()) { "Could not forget folder." }
        if (old != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    /**
     * Bounded breadth-first scan of the owner-authorized tree.
     * Previously only the top-level directory was checked, so a course
     * library organised into subject folders appeared to have zero books.
     * No background scan, extra permission or write access is introduced.
     */
    fun books(context: Context): List<Document> {
        val folder = treeUri(context)
            ?: throw IllegalStateException(
                "Linked folder access was revoked or moved. Choose the folder again."
            )
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.addLast(DocumentsContract.getTreeDocumentId(folder) to 0)
        val visitedFolders = mutableSetOf<String>()
        val candidates = mutableListOf<Document>()
        var inspected = 0
        while (pending.isNotEmpty() && inspected < MAX_INSPECTED) {
            val (folderId, depth) = pending.removeFirst()
            if (!visitedFolders.add(folderId)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, folderId)
            val cursor = resolver.query(children, projection, null, null, null)
                ?: throw IllegalStateException(
                    "Cannot read a selected library subfolder; check its authorization."
                )
            cursor.use { rows ->
                val idIndex = rows.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameIndex = rows.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val mimeIndex = rows.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                val sizeIndex = rows.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE
                )
                while (inspected < MAX_INSPECTED && rows.moveToNext()) {
                    inspected++
                    val id = rows.getString(idIndex) ?: continue
                    val mime = if (rows.isNull(mimeIndex)) "" else rows.getString(mimeIndex)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (AstraFolderRules.canDescend(
                                depth, visitedFolders.size + pending.size
                            ) && id !in visitedFolders
                        ) pending.addLast(id to (depth + 1))
                        continue
                    }
                    val name = rows.getString(nameIndex) ?: continue
                    val size = if (rows.isNull(sizeIndex)) null else rows.getLong(sizeIndex)
                    if (!AstraFolderRules.bookNameAllowed(name, size)) continue
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folder, id)
                    candidates.add(Document(name, fileUri, size))
                }
            }
        }
        return candidates.sortedWith(
            compareBy<Document> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.uri.toString() }
        )
    }

    /** Explicit bulk import; sources are read-only, copied into private indexes. */
    fun importBooks(context: Context, library: AstraBookLibrary): Result {
        val eligible = books(context)
        var existingCount = library.listBooks().size
        var imported = 0
        var duplicates = 0
        var inspected = 0
        val errors = mutableListOf<String>()
        // Scan up to the bounded eligible list: duplicate books must not
        // prevent later new books from reaching the 30-book capacity.
        for (entry in eligible) {
            if (existingCount >= MAX_IMPORTS) break
            inspected++
            try {
                val stream = context.contentResolver.openInputStream(entry.uri)
                    ?: error("Could not open selected document.")
                stream.use { library.importBook(entry.name, it) }
                val newCount = library.listBooks().size
                if (newCount > existingCount) {
                    imported += newCount - existingCount
                    existingCount = newCount
                } else duplicates++
            } catch (failure: Exception) {
                errors.add(entry.name.take(55) + ": " +
                    (failure.message ?: "Import failed.").take(90))
            }
        }
        return Result(
            imported, duplicates, inspected, errors,
            atCapacity = existingCount >= MAX_IMPORTS
        )
    }
}

/** JVM-testable minimum-privilege filters; never trust provider names/sizes. */
internal object AstraFolderRules {
    private const val MAX_BYTES = 10L * 1024 * 1024
    private const val MAX_FOLDER_DEPTH = 4
    private const val MAX_FOLDERS = 64

    fun canDescend(parentDepth: Int, knownFolderCount: Int): Boolean =
        parentDepth in 0 until MAX_FOLDER_DEPTH &&
            knownFolderCount in 0 until MAX_FOLDERS
    fun bookNameAllowed(filename: String, size: Long?): Boolean {
        if (filename.isBlank() || filename.length > 180 ||
            filename.any { it.isISOControl() || it == '/' || it == '\\' }) return false
        if (size != null && size !in 1..MAX_BYTES) return false
        val format = AstraDocumentText.format(filename) ?: return false
        // PDF input is deliberately more conservative than other documents.
        // Reject a known oversized file before opening a SAF content stream.
        if (format == "pdf" && size != null &&
            !AstraPdfText.sizeAllowed(size)
        ) return false
        return true
    }
}
