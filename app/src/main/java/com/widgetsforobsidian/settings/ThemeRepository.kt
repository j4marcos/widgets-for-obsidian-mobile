package com.widgetsforobsidian.settings

import com.widgetsforobsidian.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the vault's `.obsidian/appearance.json`, re-parsing only when it changed. */
class ThemeRepository(private val vault: VaultRepository, private val settings: SettingsStore) {

    @Volatile private var cached: Pair<Long, ObsidianTheme>? = null

    suspend fun current(): ObsidianTheme = withContext(Dispatchers.IO) {
        runCatching {
            val tree = settings.current().vault?.treeUri ?: return@runCatching ObsidianTheme()
            val path = ".obsidian/appearance.json"
            val id = vault.findDocumentId(tree, path) ?: return@runCatching ObsidianTheme()
            val mtime = vault.stat(tree, path, id)?.lastModified ?: 0L
            cached?.takeIf { it.first == mtime }?.second
                ?: ObsidianTheme.parse(vault.readText(tree, id)).also { cached = mtime to it }
        }.getOrDefault(ObsidianTheme())
    }
}
