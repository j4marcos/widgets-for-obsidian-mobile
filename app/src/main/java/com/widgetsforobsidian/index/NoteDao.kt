package com.widgetsforobsidian.index

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT path, mtime FROM notes")
    suspend fun mtimes(): List<PathMtime>

    @Upsert
    suspend fun upsert(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE path IN (:paths)")
    suspend fun delete(paths: List<String>)

    @Query("DELETE FROM notes")
    suspend fun clear()

    @Transaction
    suspend fun apply(changed: List<NoteEntity>, removed: List<String>) {
        // SQLite caps bound parameters, so delete in chunks.
        removed.chunked(500).forEach { delete(it) }
        changed.chunked(500).forEach { upsert(it) }
    }

    @Query("SELECT * FROM notes WHERE path = :path")
    suspend fun get(path: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE extension = 'md' AND name LIKE '%' || :query || '%' ORDER BY mtime DESC LIMIT :limit")
    suspend fun searchNotes(query: String, limit: Int = 50): List<NoteEntity>

    /** `LIKE` treats `_` as a wildcard, so this can over-match; the base filters run afterwards anyway. */
    @Query("SELECT * FROM notes WHERE extension = 'md' AND (folder = :folder OR folder LIKE :folder || '/%')")
    suspend fun notesInFolder(folder: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE extension = 'md'")
    suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE extension = 'base' ORDER BY path")
    suspend fun bases(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE extension = :extension")
    fun countFlow(extension: String): Flow<Int>
}
