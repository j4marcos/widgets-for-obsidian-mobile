package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.widgetsforobsidian.R
import com.widgetsforobsidian.WidgetsApp
import java.time.LocalDate

object TasksWidgetKeys {
    val BASE_PATH = stringPreferencesKey("base_path")
    val VIEW_INDEX = intPreferencesKey("view_index")
    /** Bumped to force a reload; Glance only recomposes when the widget state changes. */
    val REFRESH = longPreferencesKey("refresh")

    val TASK_PATH = ActionParameters.Key<String>("task_path")
    val TASK_DONE = ActionParameters.Key<Boolean>("task_done")
}

sealed interface TasksState {
    data object Loading : TasksState
    data class Error(val message: String) : TasksState
    data class Ready(
        val viewName: String,
        val sections: List<TaskSection>,
        val total: Int,
        val vaultName: String,
    ) : TasksState
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TasksWidget()
}

class TasksWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as WidgetsApp).container
        provideContent {
            val prefs = currentState<Preferences>()
            val basePath = prefs[TasksWidgetKeys.BASE_PATH]
            val viewIndex = prefs[TasksWidgetKeys.VIEW_INDEX] ?: 0
            val refresh = prefs[TasksWidgetKeys.REFRESH] ?: 0L

            val state by produceState<TasksState>(TasksState.Loading, basePath, viewIndex, refresh) {
                value = load(container, basePath, viewIndex)
            }
            Content(basePath, state)
        }
    }

    private suspend fun load(container: com.widgetsforobsidian.AppContainer, basePath: String?, viewIndex: Int): TasksState {
        if (basePath == null) return TasksState.Error("Widget sem base. Remova e adicione de novo.")
        val vault = container.settings.current().vault
            ?: return TasksState.Error("Abra o app e escolha o vault.")
        return try {
            val (_, result) = container.bases.query(basePath, viewIndex)
            val today = LocalDate.now()
            TasksState.Ready(result.view.name, TaskFormat.sections(result, today), result.total, vault.name)
        } catch (e: Exception) {
            TasksState.Error(e.message ?: e.toString())
        }
    }
}

private object Palette {
    val background = ColorProvider(day = Color(0xF2F7F7F8), night = Color(0xE6262628))
    val text = ColorProvider(day = Color(0xFF1F1F1F), night = Color(0xFFEDEDED))
    val muted = ColorProvider(day = Color(0xFF7A7A7A), night = Color(0xFF9A9A9A))
    val accent = ColorProvider(day = Color(0xFF7C3AED), night = Color(0xFFA78BFA))
    val overdue = ColorProvider(Color(0xFFE5484D))

    fun priority(priority: String?) = ColorProvider(
        when (priority) {
            "high", "urgent" -> Color(0xFFE5484D)
            "medium" -> Color(0xFFF5A524)
            "low" -> Color(0xFF3E8EF7)
            else -> Color(0xFF8A8A8A)
        },
    )
}

private sealed interface Line {
    val id: Long
    data class Header(val label: String, val count: Int) : Line {
        override val id = ("h:$label").hashCode().toLong()
    }
    data class Task(val task: TaskUi) : Line {
        override val id = task.path.hashCode().toLong() shl 1
    }
}

@Composable
private fun Content(basePath: String?, state: TasksState) {
    Column(
        GlanceModifier.fillMaxSize().background(Palette.background).cornerRadius(20.dp)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Header(
            title = basePath?.let(TaskFormat::baseTitle) ?: "Tasks",
            subtitle = (state as? TasksState.Ready)?.let { "${it.viewName} · ${it.total}" },
        )
        Spacer(GlanceModifier.height(6.dp))
        when (state) {
            TasksState.Loading -> Message("Carregando…")
            is TasksState.Error -> Message(state.message)
            is TasksState.Ready -> TaskList(state)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().clickable(actionRunCallback<CycleViewAction>())) {
            Text(
                title.uppercase(),
                style = TextStyle(color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    "$subtitle  ▾",
                    style = TextStyle(color = Palette.muted, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
        Box(GlanceModifier.padding(8.dp).clickable(actionRunCallback<RefreshAction>())) {
            Image(
                ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Atualizar",
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(Palette.muted),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = Palette.muted, fontSize = 13.sp), modifier = GlanceModifier.padding(vertical = 8.dp))
}

@Composable
private fun TaskList(state: TasksState.Ready) {
    if (state.total == 0) {
        Message("Nenhuma tarefa nesta view.")
        return
    }
    val lines = buildList {
        state.sections.forEach { section ->
            section.label?.let { add(Line.Header(it, section.tasks.size)) }
            section.tasks.forEach { add(Line.Task(it)) }
        }
    }
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(lines, itemId = { it.id }) { line ->
            when (line) {
                is Line.Header -> SectionHeader(line)
                is Line.Task -> TaskRow(line.task, state.vaultName)
            }
        }
    }
}

@Composable
private fun SectionHeader(line: Line.Header) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            line.label.uppercase(),
            style = TextStyle(color = Palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        Text("${line.count}", style = TextStyle(color = Palette.muted, fontSize = 11.sp))
    }
}

@Composable
private fun TaskRow(task: TaskUi, vaultName: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.padding(end = 8.dp, top = 6.dp, bottom = 6.dp).clickable(
                actionRunCallback<ToggleDoneAction>(
                    actionParametersOf(TasksWidgetKeys.TASK_PATH to task.path, TasksWidgetKeys.TASK_DONE to task.done),
                ),
            ),
        ) {
            Image(
                ImageProvider(if (task.done) R.drawable.ic_task_done else R.drawable.ic_task_open),
                contentDescription = if (task.done) "Reabrir tarefa" else "Concluir tarefa",
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(if (task.done) Palette.muted else Palette.priority(task.priority)),
            )
        }
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openInObsidian(vaultName, task.path)))) {
            Text(
                task.title,
                style = TextStyle(
                    color = if (task.done) Palette.muted else Palette.text,
                    fontSize = 14.sp,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
            task.subtitle?.let {
                Text(it, style = TextStyle(color = Palette.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
        task.dueLabel?.let {
            Text(
                it,
                style = TextStyle(color = if (task.overdue) Palette.overdue else Palette.muted, fontSize = 11.sp),
                modifier = GlanceModifier.padding(start = 6.dp, end = 6.dp),
            )
        }
    }
}

fun openInObsidian(vaultName: String, path: String): Intent {
    val uri = Uri.Builder().scheme("obsidian").authority("open")
        .appendQueryParameter("vault", vaultName)
        .appendQueryParameter("file", path.removeSuffix(".md"))
        .build()
    return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
