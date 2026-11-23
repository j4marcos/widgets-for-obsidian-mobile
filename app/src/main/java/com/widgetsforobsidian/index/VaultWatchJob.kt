package com.widgetsforobsidian.index

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.widgets.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wakes up when files in shared storage change (Obsidian saving, Syncthing pulling a change…),
 * even with this app's process dead: MediaStore notifies on every file write and JobScheduler
 * starts this service for the content-URI trigger. The vault is rescanned incrementally and
 * the widgets redrawn.
 *
 * This is a plain JobService rather than a WorkManager worker on purpose: when the process is
 * killed and restarted, WorkManager reschedules its jobs on startup, which swallows the very
 * trigger that started the process.
 */
class VaultWatchJob : JobService() {

    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as WidgetsApp).container
        work = container.appScope.launch {
            try {
                runCatching { container.indexer.index() }
                // Redraw even when no note changed: the change may be in .obsidian (the note
                // open in Obsidian, the theme), which the index skips but the widgets read.
                WidgetUpdater.refreshAll(applicationContext)
            } finally {
                jobFinished(params, false)
                // Content-trigger jobs fire once; arm the next one.
                schedule(applicationContext)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        schedule(applicationContext)
        return false
    }

    companion object {
        private const val JOB_ID = 4201

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, VaultWatchJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                )
                // Batch bursts of writes (a sync touching many files) into one run.
                .setTriggerContentUpdateDelay(2_000)
                .setTriggerContentMaxDelay(15_000)
                .build()
            scheduler.schedule(job)
        }

        /** Arms the watcher unless it is already waiting (re-arming would drop a pending trigger). */
        fun ensureScheduled(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) == null) schedule(context)
        }
    }
}
