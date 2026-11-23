package com.widgetsforobsidian.index

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.widgets.tasks.TasksWidgetUpdater
import java.util.concurrent.TimeUnit

class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val indexer = (applicationContext as WidgetsApp).container.indexer
        return when (val result = runCatching { indexer.index() }.getOrNull()) {
            is IndexResult.Done -> {
                if (result.changed + result.removed > 0) TasksWidgetUpdater.refreshAll(applicationContext)
                Result.success()
            }
            IndexResult.NoVault, IndexResult.PermissionLost -> Result.success()
            null -> Result.retry()
        }
    }

    companion object {
        const val NOW = "index-now"
        private const val PERIODIC = "index-periodic"

        /** Every 15 minutes, the shortest interval WorkManager allows. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<IndexWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<IndexWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
