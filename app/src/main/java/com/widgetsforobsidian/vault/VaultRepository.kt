package com.widgetsforobsidian.vault

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** A file inside the vault, addressed by its path relative to the vault root. */
data class VaultFile(
    val path: String,
    val documentId: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * Reads and writes vault files through the Storage Access Framework.
 *
 * Uses [DocumentsContract] queries directly instead of DocumentFile, which issues one
 * query per property and is far too slow for vaults with thousands of notes.
 */
class VaultRepository(private val resolver: ContentResolver) {

    fun takePermission(treeUri: Uri) {
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun hasPermission(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    /** Display name of the picked folder, which Obsidian uses as the vault name. */
    fun vaultName(treeUri: Uri): String {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        resolver.query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return rootId.substringAfterLast(':').substringAfterLast('/')
    }

    /** Lists every `.md` and `.base` file, skipping hidden folders such as `.obsidian` and `.trash`. */
    fun listFiles(treeUri: Uri): List<VaultFile> {
        val result = mutableListOf<VaultFile>()
        val queue = ArrayDeque<Pair<String, String>>() // documentId to relative folder path
        queue.add(DocumentsContract.getTreeDocumentId(treeUri) to "")

        while (queue.isNotEmpty()) {
            val (parentId, parentPath) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    if (c.getString(2) == Document.MIME_TYPE_DIR) {
                        queue.add(id to path)
                    } else if (VaultPaths.isIndexable(name)) {
                        result.add(VaultFile(path, id, c.getLong(3), c.getLong(4)))
                    }
                }
            }
        }
        return result
    }

    fun readText(treeUri: Uri, documentId: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Cannot open $uri")
    }

    fun writeText(treeUri: Uri, documentId: String, text: String) {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        // "wt" truncates; some providers only accept "rwt". Plain "w" may not truncate on all of them.
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "rwt")
            ?: error("Cannot write $uri")
        stream.use { it.write(text.encodeToByteArray()) }
    }

    /** Current metadata of one known file, or null if it no longer exists. */
    fun stat(treeUri: Uri, path: String, documentId: String): VaultFile? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return runCatching {
            resolver.query(uri, PROJECTION, null, null, null)?.use { c ->
                if (c.moveToFirst()) VaultFile(path, documentId, c.getLong(3), c.getLong(4)) else null
            }
        }.getOrNull()
    }

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
    }
}

object VaultPaths {
    fun isIndexable(name: String) = name.endsWith(".md") || name.endsWith(".base")
    fun extension(path: String) = path.substringAfterLast('.', "")
    fun folder(path: String) = path.substringBeforeLast('/', "")
    fun basename(path: String) = path.substringAfterLast('/').substringBeforeLast('.')
}
