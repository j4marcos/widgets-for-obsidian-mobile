package com.widgetsforobsidian.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class VaultConfig(val treeUri: Uri, val name: String)

data class AppSettings(
    val vault: VaultConfig?,
    val lastIndexedAt: Long?,
)

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val uri = prefs[VAULT_URI]?.let(Uri::parse)
        AppSettings(
            vault = uri?.let { VaultConfig(it, prefs[VAULT_NAME].orEmpty()) },
            lastIndexedAt = prefs[LAST_INDEXED_AT],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setVault(vault: VaultConfig) {
        context.dataStore.edit {
            it[VAULT_URI] = vault.treeUri.toString()
            it[VAULT_NAME] = vault.name
            it.remove(LAST_INDEXED_AT)
        }
    }

    suspend fun setLastIndexedAt(timestamp: Long) {
        context.dataStore.edit { it[LAST_INDEXED_AT] = timestamp }
    }

    private companion object {
        val VAULT_URI = stringPreferencesKey("vault_uri")
        val VAULT_NAME = stringPreferencesKey("vault_name")
        val LAST_INDEXED_AT = longPreferencesKey("last_indexed_at")
    }
}
