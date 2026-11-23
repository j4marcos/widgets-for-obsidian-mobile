package com.widgetsforobsidian.bases

import com.widgetsforobsidian.index.NoteDao
import com.widgetsforobsidian.index.NoteEntity
import com.widgetsforobsidian.settings.SettingsStore
import com.widgetsforobsidian.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.concurrent.ConcurrentHashMap

/** Loads `.base` files from the vault and runs their views over the index. */
class BaseRepository(
    private val vault: VaultRepository,
    private val dao: NoteDao,
    private val settings: SettingsStore,
) {
    private data class Cached(val mtime: Long, val base: BaseDefinition)
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun load(basePath: String): BaseDefinition = withContext(Dispatchers.IO) {
        val entity = dao.get(basePath) ?: throw IllegalStateException("Base não encontrada: $basePath")
        cache[basePath]?.takeIf { it.mtime == entity.mtime }?.let { return@withContext it.base }
        val vaultConfig = settings.current().vault ?: throw IllegalStateException("Nenhum vault configurado")
        val base = BaseParser.parse(vault.readText(vaultConfig.treeUri, entity.documentId))
        cache[basePath] = Cached(entity.mtime, base)
        base
    }

    suspend fun query(basePath: String, viewIndex: Int): Pair<BaseDefinition, QueryResult> =
        withContext(Dispatchers.IO) {
            val base = load(basePath)
            val folder = QueryEngine.folderHint(base.filters)
            val entities = if (folder != null) dao.notesInFolder(folder) else dao.allNotes()
            base to QueryEngine.run(base, viewIndex, entities.map(::toRow))
        }

    companion object {
        fun toRow(e: NoteEntity) = NoteRow(
            path = e.path,
            basename = e.name,
            folder = e.folder,
            extension = e.extension,
            mtime = e.mtime,
            size = e.size,
            properties = (runCatching { Json.parseToJsonElement(e.properties) }.getOrNull() as? JsonObject)
                ?.mapValues { toValue(it.value) }.orEmpty(),
            tags = e.tags.split('|').filter { it.isNotEmpty() },
            links = e.links.split('\n').filter { it.isNotEmpty() },
        )

        fun toValue(e: JsonElement): Any? = when (e) {
            JsonNull -> null
            is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.doubleOrNull ?: e.content
            is JsonArray -> e.map(::toValue)
            is JsonObject -> e.mapValues { toValue(it.value) }
        }
    }
}
