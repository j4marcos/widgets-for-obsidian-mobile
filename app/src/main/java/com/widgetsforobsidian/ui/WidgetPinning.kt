package com.widgetsforobsidian.ui

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.widgetsforobsidian.R
import com.widgetsforobsidian.widgets.capture.CaptureWidgetReceiver
import com.widgetsforobsidian.widgets.note.NoteWidgetConfigActivity
import com.widgetsforobsidian.widgets.note.NoteWidgetProvider
import com.widgetsforobsidian.widgets.tasks.TasksWidgetConfigActivity
import com.widgetsforobsidian.widgets.tasks.TasksWidgetReceiver

/** Asks the launcher to place one of our widgets on the home screen, from inside the app. */
object WidgetPinning {

    /** Set on the config intent we hand the launcher, so the config screen knows it came from a pin request. */
    const val EXTRA_FROM_PIN = "com.widgetsforobsidian.FROM_PIN"

    enum class Kind(
        @StringRes val title: Int,
        @StringRes val description: Int,
        val provider: Class<*>,
        val config: Class<out Activity>?,
    ) {
        TASKS(R.string.tasks_widget_name, R.string.tasks_widget_description, TasksWidgetReceiver::class.java, TasksWidgetConfigActivity::class.java),
        NOTE(R.string.note_widget_name, R.string.note_widget_description, NoteWidgetProvider::class.java, NoteWidgetConfigActivity::class.java),
        CAPTURE(R.string.capture_widget_name, R.string.capture_widget_description, CaptureWidgetReceiver::class.java, null),
    }

    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /**
     * Shows the launcher's "add widget" prompt. Launchers don't reliably open a widget's configure activity for
     * pinned widgets, so the success callback opens it; the launcher fills in [AppWidgetManager.EXTRA_APPWIDGET_ID].
     */
    fun request(context: Context, kind: Kind): Boolean {
        val callback = kind.config?.let { config ->
            PendingIntent.getActivity(
                context,
                kind.ordinal,
                Intent(context, config)
                    .putExtra(EXTRA_FROM_PIN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                // Mutable so the launcher can add the new widget id.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }
        return AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, kind.provider), null, callback)
    }
}
