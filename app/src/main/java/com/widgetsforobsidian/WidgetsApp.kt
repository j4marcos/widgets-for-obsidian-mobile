package com.widgetsforobsidian

import android.app.Application
import android.content.Context
import com.widgetsforobsidian.bases.BaseRepository
import com.widgetsforobsidian.index.AppDatabase
import com.widgetsforobsidian.index.IndexWorker
import com.widgetsforobsidian.index.Indexer
import com.widgetsforobsidian.settings.SettingsStore
import com.widgetsforobsidian.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val vault = VaultRepository(context.contentResolver)
    val database = AppDatabase.create(context)
    val indexer = Indexer(vault, database.notes(), settings)
    val bases = BaseRepository(vault, database.notes(), settings)
    /** Outlives activities: used for saves that must finish after a popup closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class WidgetsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        IndexWorker.schedulePeriodic(this)
    }
}
