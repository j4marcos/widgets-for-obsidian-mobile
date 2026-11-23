package com.widgetsforobsidian.index

import android.util.Log
import com.widgetsforobsidian.settings.SettingsStore
import com.widgetsforobsidian.vault.NoteParser
import com.widgetsforobsidian.vault.VaultFile
import com.widgetsforobsidian.vault.VaultPaths
import com.widgetsforobsidian.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface IndexResult {
    data object NoVault : IndexResult
    data object PermissionLost : IndexResult
    data class Done(val total: Int, val changed: Int, val removed: Int) : IndexResult
}

/** Incrementally syncs the Room index with the vault, re-reading only files whose mtime changed. */
class Indexer(
    private val vault: VaultRepository,
    private val dao: NoteDao,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()

    suspend fun index(): IndexResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val config = settings.current().vault ?: return@withContext IndexResult.NoVault
            if (!vault.hasPermission(config.treeUri)) return@withContext IndexResult.PermissionLost

            val files = vault.listFiles(config.treeUri)
            val known = dao.mtimes().associate { it.path to it.mtime }
            val changed = files
                .filter { known[it.path] != it.lastModified }
                .map { toEntity(it) { vault.readText(config.treeUri, it.documentId) } }
            val present = files.mapTo(HashSet()) { it.path }
            val removed = known.keys.filterNot(present::contains)

            dao.apply(changed, removed)
            settings.setLastIndexedAt(System.currentTimeMillis())
            IndexResult.Done(files.size, changed.size, removed.size)
        }
    }

    /**
     * Rewrites one note through [transform] and updates its index entry right away, without a
     * full vault scan. The file is read fresh from disk, never from the index, so edits made
     * elsewhere since the last scan are not lost.
     */
    suspend fun editNote(path: String, transform: (String) -> String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val config = settings.current().vault ?: return@withContext false
            val entity = dao.get(path) ?: return@withContext false
            val current = vault.readText(config.treeUri, entity.documentId)
            val updated = transform(current)
            if (updated != current) vault.writeText(config.treeUri, entity.documentId, updated)
            val file = vault.stat(config.treeUri, path, entity.documentId) ?: return@withContext false
            dao.upsert(listOf(toEntity(file) { updated }))
            true
        }
    }

    suspend fun reset() = mutex.withLock { dao.clear() }

    companion object {
        fun toEntity(file: VaultFile, read: () -> String): NoteEntity {
            val ext = VaultPaths.extension(file.path)
            val parsed = if (ext == "md") {
                runCatching { NoteParser.parse(read()) }
                    .onFailure { Log.w("Indexer", "Failed to parse ${file.path}", it) }
                    .getOrNull()
            } else {
                null
            }
            return NoteEntity(
                path = file.path,
                documentId = file.documentId,
                name = VaultPaths.basename(file.path),
                folder = VaultPaths.folder(file.path),
                extension = ext,
                mtime = file.lastModified,
                size = file.size,
                properties = parsed?.properties?.toString() ?: "{}",
                tags = parsed?.tags?.joinToString("|", prefix = "|", postfix = "|") ?: "",
                links = parsed?.links?.joinToString("\n") ?: "",
            )
        }
    }
}
