package com.widgetsforobsidian.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.index.IndexWorker
import com.widgetsforobsidian.settings.VaultConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val vaultName: String? = null,
    val permissionLost: Boolean = false,
    val noteCount: Int = 0,
    val baseCount: Int = 0,
    val lastIndexedAt: Long? = null,
    val indexing: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WidgetsApp).container
    private val workManager = WorkManager.getInstance(app)

    private val indexing = workManager.getWorkInfosForUniqueWorkFlow(IndexWorker.NOW)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    val state: StateFlow<MainUiState> = combine(
        container.settings.settings,
        container.database.notes().countFlow("md"),
        container.database.notes().countFlow("base"),
        indexing,
    ) { settings, notes, bases, running ->
        MainUiState(
            vaultName = settings.vault?.name,
            permissionLost = settings.vault?.let { !container.vault.hasPermission(it.treeUri) } ?: false,
            noteCount = notes,
            baseCount = bases,
            lastIndexedAt = settings.lastIndexedAt,
            indexing = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun onVaultPicked(treeUri: Uri) {
        viewModelScope.launch {
            container.vault.takePermission(treeUri)
            val name = container.vault.vaultName(treeUri)
            container.indexer.reset()
            container.settings.setVault(VaultConfig(treeUri, name))
            IndexWorker.runNow(getApplication())
        }
    }

    fun reindex() = IndexWorker.runNow(getApplication())
}
