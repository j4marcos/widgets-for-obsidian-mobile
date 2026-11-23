package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import com.widgetsforobsidian.AppContainer
import com.widgetsforobsidian.R
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.widgets.WidgetPalette as Palette
import java.time.LocalDate

object TasksWidgetKeys {
    val BASE_PATH = stringPreferencesKey("base_path")
    val VIEW_INDEX = intPreferencesKey("view_index")
    /** `auto`, `list`, `table` or `cards`. */
    val LAYOUT = stringPreferencesKey("layout")
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
        val layout: TaskLayout,
        val columns: List<TableColumn>,
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
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as WidgetsApp).container
        provideContent {
            val prefs = currentState<Preferences>()
            val basePath = prefs[TasksWidgetKeys.BASE_PATH]
            val viewIndex = prefs[TasksWidgetKeys.VIEW_INDEX] ?: 0
            val layout = prefs[TasksWidgetKeys.LAYOUT]
            val refresh = prefs[TasksWidgetKeys.REFRESH] ?: 0L

            val state by produceState<TasksState>(TasksState.Loading, basePath, viewIndex, layout, refresh) {
                value = load(container, basePath, viewIndex, layout)
            }
            Content(basePath, state)
        }
    }

    private suspend fun load(container: AppContainer, basePath: String?, viewIndex: Int, layout: String?): TasksState {
        if (basePath == null) return TasksState.Error("Widget sem base. Remova e adicione de novo.")
        val vault = container.settings.current().vault
            ?: return TasksState.Error("Abra o app e escolha o vault.")
        return try {
            val (base, result) = container.bases.query(basePath, viewIndex)
            val today = LocalDate.now()
            val columns = TaskFormat.columns(result.view.order, base::displayName)
            TasksState.Ready(
                viewName = result.view.name,
                layout = TaskLayout.resolve(layout, result.view.type),
                columns = columns,
                sections = TaskFormat.sections(result, today, columns),
                total = result.total,
                vaultName = vault.name,
            )
        } catch (e: Exception) {
            TasksState.Error(e.message ?: e.toString())
        }
    }
}

private sealed interface Line {
    val id: Long
    data class Header(val label: String, val count: Int) : Line {
        override val id = "h:$label".hashCode().toLong()
    }
    data class Task(val task: TaskUi) : Line {
        override val id = task.path.hashCode().toLong() shl 1
    }
}

@Composable
private fun Content(basePath: String?, state: TasksState) {
    Column(
        GlanceModifier.fillMaxSize().background(Palette.background).cornerRadius(20.dp)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Header(
            basePath = basePath,
            subtitle = (state as? TasksState.Ready)?.let { "${it.viewName} · ${it.total}" },
        )
        Spacer(GlanceModifier.height(4.dp))
        when (state) {
            TasksState.Loading -> Message("Carregando…")
            is TasksState.Error -> Message(state.message)
            is TasksState.Ready -> when {
                state.total == 0 -> Message("Nenhuma tarefa nesta view.")
                state.layout == TaskLayout.TABLE -> TaskTable(state)
                state.layout == TaskLayout.CARDS -> TaskCards(state)
                else -> TaskList(state)
            }
        }
    }
}

@Composable
private fun Header(basePath: String?, subtitle: String?) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().clickable(actionRunCallback<CycleViewAction>())) {
            Text(
                (basePath?.let(TaskFormat::baseTitle) ?: "Tasks").uppercase(),
                style = TextStyle(color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (subtitle != null) {
                Text("$subtitle  ▾", style = TextStyle(color = Palette.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
        if (basePath != null) {
            IconButton(R.drawable.ic_add, "Nova tarefa", actionStartActivity(NewTaskActivity.intent(context, basePath)))
        }
        IconButton(R.drawable.ic_refresh, "Atualizar", actionRunCallback<RefreshAction>())
    }
}

@Composable
private fun IconButton(icon: Int, description: String, action: androidx.glance.action.Action) {
    Box(GlanceModifier.padding(7.dp).clickable(action)) {
        Image(
            ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(Palette.muted),
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = Palette.muted, fontSize = 13.sp), modifier = GlanceModifier.padding(vertical = 8.dp))
}

@Composable
private fun TaskList(state: TasksState.Ready) {
    val compact = LocalSize.current.height < 160.dp
    val lines = buildList {
        state.sections.forEach { section ->
            section.label?.let { add(Line.Header(it, section.tasks.size)) }
            section.tasks.forEach { add(Line.Task(it)) }
        }
    }
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(lines, itemId = { it.id }) { line ->
            when (line) {
                is Line.Header -> SectionHeader(line.label, line.count)
                is Line.Task -> TaskRow(line.task, state.vaultName, showSubtitle = !compact)
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text(label.uppercase(), style = TextStyle(color = Palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.width(6.dp))
        Text("$count", style = TextStyle(color = Palette.muted, fontSize = 11.sp))
    }
}

@Composable
private fun Checkbox(task: TaskUi, size: Int = 20) {
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
            modifier = GlanceModifier.size(size.dp),
            colorFilter = ColorFilter.tint(if (task.done) Palette.muted else Palette.priority(task.priority)),
        )
    }
}

private fun titleStyle(done: Boolean, size: Int = 14) = TextStyle(
    color = if (done) Palette.muted else Palette.text,
    fontSize = size.sp,
    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
)

@Composable
private fun TaskRow(task: TaskUi, vaultName: String, showSubtitle: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(task)
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openInObsidian(vaultName, task.path)))) {
            Text(task.title, style = titleStyle(task.done), maxLines = 1)
            if (showSubtitle) task.subtitle?.let {
                Text(it, style = TextStyle(color = Palette.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
        task.dueLabel?.let {
            Text(
                it,
                style = TextStyle(color = if (task.overdue) Palette.overdue else Palette.muted, fontSize = 11.sp),
                modifier = GlanceModifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Title plus as many of the view's columns as fit the widget width. */
@Composable
private fun TaskTable(state: TasksState.Ready) {
    val width = LocalSize.current.width
    val visible = ((width.value - 170f) / 72f).toInt().coerceIn(1, 4).coerceAtMost(state.columns.size)
    val columns = state.columns.take(visible)
    val cellStyle = TextStyle(color = Palette.muted, fontSize = 11.sp)

    Row(GlanceModifier.fillMaxWidth().padding(start = 28.dp, end = 6.dp, bottom = 2.dp)) {
        Text("Título", style = cellStyle.copy(fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
        columns.forEach {
            Text(it.label, style = cellStyle.copy(fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(68.dp), maxLines = 1)
        }
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
                is Line.Header -> SectionHeader(line.label, line.count)
                is Line.Task -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(line.task, size = 18)
                    Text(
                        line.task.title,
                        style = titleStyle(line.task.done, 13),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                            .clickable(actionStartActivity(openInObsidian(state.vaultName, line.task.path))),
                    )
                    line.task.cells.take(visible).forEach {
                        Text(it, style = cellStyle, maxLines = 1, modifier = GlanceModifier.width(68.dp))
                    }
                }
            }
        }
    }
}

private val CARD_HEIGHT = 104.dp

@Composable
private fun TaskCards(state: TasksState.Ready) {
    val columns = if (LocalSize.current.width >= 300.dp) 2 else 1
    val tasks = state.sections.flatMap { it.tasks }
    LazyVerticalGrid(GridCells.Fixed(columns), GlanceModifier.fillMaxSize()) {
        items(tasks, itemId = { it.path.hashCode().toLong() }) { task ->
            // Fixed height: the grid sizes each row from one cell, so taller cards would be clipped.
            Box(GlanceModifier.height(CARD_HEIGHT).padding(end = 6.dp, bottom = 6.dp)) {
                Column(
                    GlanceModifier.fillMaxSize().background(Palette.card).cornerRadius(12.dp)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                        .clickable(actionStartActivity(openInObsidian(state.vaultName, task.path))),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(task, size = 18)
                        Text(task.group.orEmpty(), style = TextStyle(color = Palette.accent, fontSize = 10.sp), maxLines = 1)
                    }
                    Text(task.title, style = titleStyle(task.done, 13), maxLines = 2)
                    val details = listOfNotNull(task.subtitle, task.dueLabel).joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            details,
                            style = TextStyle(color = if (task.overdue) Palette.overdue else Palette.muted, fontSize = 11.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
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
