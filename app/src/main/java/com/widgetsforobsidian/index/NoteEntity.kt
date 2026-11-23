package com.widgetsforobsidian.index

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One indexed vault file (`.md` note or `.base` file).
 *
 * [tags] is stored as `|tag1|tag2|` so a tag can be matched with `LIKE '%|tag|%'`.
 * [properties] holds the frontmatter as a JSON object.
 */
@Entity(tableName = "notes", indices = [Index("folder"), Index("extension")])
data class NoteEntity(
    @PrimaryKey val path: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    val name: String,
    val folder: String,
    val extension: String,
    val mtime: Long,
    val size: Long,
    val properties: String,
    val tags: String,
    val links: String,
)

data class PathMtime(val path: String, val mtime: Long)
