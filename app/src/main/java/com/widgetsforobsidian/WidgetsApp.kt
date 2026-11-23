package com.widgetsforobsidian

import android.app.Application
import android.content.Context
import com.widgetsforobsidian.bases.BaseRepository
import com.widgetsforobsidian.index.AppDatabase
import com.widgetsforobsidian.index.IndexWorker
import com.widgetsforobsidian.index.Indexer
import androidx.work.WorkManager
import com.widgetsforobsidian.index.VaultWatchJob
import com.widgetsforobsidian.settings.SettingsStore
import com.widgetsforobsidian.settings.ThemeRepository
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
    val theme = ThemeRepository(vault, settings)
    val bases = BaseRepository(vault, database.notes(), settings, indexer)
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
        VaultWatchJob.ensureScheduled(this)
        // Replaced by VaultWatchJob in 0.2; drop the WorkManager version left by older installs.
        WorkManager.getInstance(this).cancelUniqueWork("vault-watch")
    }
}
