package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.vault.FrontmatterEditor

private val Context.container get() = (applicationContext as WidgetsApp).container

/** Marks a task as done, or reopens it as `todo`, by rewriting its `status` property. */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val path = parameters[TasksWidgetKeys.TASK_PATH] ?: return
        val wasDone = parameters[TasksWidgetKeys.TASK_DONE] ?: false
        context.container.indexer.editNote(path) {
            FrontmatterEditor.set(it, "status", if (wasDone) "todo" else "done")
        }
        TasksWidgetUpdater.refreshAll(context)
    }
}

/** Rescans the vault, then reloads every tasks widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.indexer.index()
        TasksWidgetUpdater.refreshAll(context)
    }
}

/** Switches the widget to the next view of its base that can be shown as a list. */
class CycleViewAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, glanceId)
        val basePath = prefs[TasksWidgetKeys.BASE_PATH] ?: return
        val current = prefs[TasksWidgetKeys.VIEW_INDEX] ?: 0
        val views = runCatching { context.container.bases.load(basePath) }.getOrNull()
            ?.views?.filter { it.isListLike }.orEmpty()
        if (views.isEmpty()) return
        val next = views.firstOrNull { it.index > current } ?: views.first()
        updateAppWidgetState(context, glanceId) { it[TasksWidgetKeys.VIEW_INDEX] = next.index }
        TasksWidget().update(context, glanceId)
    }
}

object TasksWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val widget = TasksWidget()
        GlanceAppWidgetManager(context).getGlanceIds(TasksWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { it[TasksWidgetKeys.REFRESH] = System.nanoTime() }
            widget.update(context, id)
        }
    }
}
