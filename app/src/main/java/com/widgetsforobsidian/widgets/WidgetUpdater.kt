package com.widgetsforobsidian.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.widgetsforobsidian.widgets.note.NoteWidgetProvider
import com.widgetsforobsidian.widgets.tasks.TasksWidget
import com.widgetsforobsidian.widgets.tasks.TasksWidgetKeys

/** Reloads every widget instance. Widgets reload when their state changes, so a token is bumped. */
object WidgetUpdater {

    suspend fun refreshAll(context: Context) {
        refresh(context, TasksWidget())
        NoteWidgetProvider.updateAll(context)
    }

    private suspend fun refresh(context: Context, widget: GlanceAppWidget) {
        GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).forEach { id ->
            updateAppWidgetState(context, id) { it[TasksWidgetKeys.REFRESH] = System.nanoTime() }
            widget.update(context, id)
        }
    }
}
