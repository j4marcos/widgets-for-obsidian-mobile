package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import com.widgetsforobsidian.settings.ObsidianTheme
import com.widgetsforobsidian.widgets.LocalPalette
import com.widgetsforobsidian.widgets.WidgetPalette
import java.time.LocalDate

object TasksWidgetKeys {
    val BASE_PATH = stringPreferencesKey("base_path")
    val VIEW_INDEX = intPreferencesKey("view_index")
    /** `auto`, `list`, `table` or `cards`. */
    val LAYOUT = stringPreferencesKey("layout")
    /** `project` (default) or `view`. */
    val GROUPING = stringPreferencesKey("grouping")
    val HIDE_DONE = booleanPreferencesKey("hide_done")
    val COLLAPSED = stringSetPreferencesKey("collapsed_sections")
    /** Bumped to force a reload; Glance only recomposes when the widget state changes. */
    val REFRESH = longPreferencesKey("refresh")

    val TASK_PATH = ActionParameters.Key<String>("task_path")
    val TASK_DONE = ActionParameters.Key<Boolean>("task_done")
    val SECTION = ActionParameters.Key<String>("section")
}

sealed interface TasksState {
    val theme: ObsidianTheme

    data class Loading(override val theme: ObsidianTheme = ObsidianTheme()) : TasksState
    data class Error(val message: String, override val theme: ObsidianTheme = ObsidianTheme()) : TasksState
    data class Ready(
        val viewName: String,
        val layout: TaskLayout,
        val columns: List<TableColumn>,
        val sections: List<TaskSection>,
        val total: Int,
        val vaultName: String,
        override val theme: ObsidianTheme,
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
            val options = Options(
                basePath = prefs[TasksWidgetKeys.BASE_PATH],
                viewIndex = prefs[TasksWidgetKeys.VIEW_INDEX] ?: 0,
                layout = prefs[TasksWidgetKeys.LAYOUT],
                grouping = if (prefs[TasksWidgetKeys.GROUPING] == "view") Grouping.VIEW else Grouping.PROJECT,
                hideDone = prefs[TasksWidgetKeys.HIDE_DONE] ?: true,
            )
            val collapsed = prefs[TasksWidgetKeys.COLLAPSED].orEmpty()
            val refresh = prefs[TasksWidgetKeys.REFRESH] ?: 0L

            val state by produceState<TasksState>(TasksState.Loading(), options, refresh) {
                value = load(context, container, options)
            }
            CompositionLocalProvider(LocalPalette provides WidgetPalette(state.theme)) {
                Content(options.basePath, state, collapsed)
            }
        }
    }

    private data class Options(
        val basePath: String?,
        val viewIndex: Int,
        val layout: String?,
        val grouping: Grouping,
        val hideDone: Boolean,
    )

    private suspend fun load(context: Context, container: AppContainer, o: Options): TasksState {
        val theme = container.theme.current()
        if (o.basePath == null) return TasksState.Error(context.getString(R.string.widget_no_base), theme)
        val vault = container.settings.current().vault
            ?: return TasksState.Error(context.getString(R.string.open_app_pick_vault), theme)
        return try {
            val (base, result) = container.bases.query(o.basePath, o.viewIndex)
            val today = LocalDate.now()
            val columns = TaskFormat.columns(result.view.order, base::displayName)
            val sections = TaskFormat.sections(result, today, columns, o.grouping, o.hideDone, TaskLabels.from(context))
            TasksState.Ready(
                viewName = result.view.name,
                layout = TaskLayout.resolve(o.layout, result.view.type),
                columns = columns,
                sections = sections,
                total = sections.sumOf { it.tasks.size },
                vaultName = vault.name,
                theme = theme,
            )
        } catch (e: Exception) {
            TasksState.Error(e.message ?: e.toString(), theme)
        }
    }
}

private sealed interface Line {
    val id: Long
    data class Header(val section: TaskSection, val collapsed: Boolean) : Line {
        override val id = "h:${section.key}".hashCode().toLong()
    }
    data class Task(val task: TaskUi) : Line {
        override val id = task.path.hashCode().toLong() shl 1
    }
}

private fun lines(sections: List<TaskSection>, collapsed: Set<String>) = buildList {
    sections.forEach { section ->
        val isCollapsed = section.label != null && section.key in collapsed
        if (section.label != null) add(Line.Header(section, isCollapsed))
        if (!isCollapsed) section.tasks.forEach { add(Line.Task(it)) }
    }
}

@Composable
private fun str(id: Int): String = LocalContext.current.getString(id)

@Composable
private fun Content(basePath: String?, state: TasksState, collapsed: Set<String>) {
    val p = LocalPalette.current
    Column(
        GlanceModifier.fillMaxSize().background(p.background).cornerRadius(20.dp)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Header(
            basePath = basePath,
            subtitle = (state as? TasksState.Ready)?.let { "${it.viewName} · ${it.total}" },
        )
        Spacer(GlanceModifier.height(4.dp))
        when (state) {
            is TasksState.Loading -> Message(str(R.string.loading))
            is TasksState.Error -> Message(state.message)
            is TasksState.Ready -> when {
                state.total == 0 -> Message(str(R.string.no_pending_tasks))
                state.layout == TaskLayout.TABLE -> TaskTable(state, collapsed)
                state.layout == TaskLayout.CARDS -> TaskCards(state)
                else -> TaskList(state, collapsed)
            }
        }
    }
}

@Composable
private fun Header(basePath: String?, subtitle: String?) {
    val context = LocalContext.current
    val p = LocalPalette.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().clickable(actionRunCallback<CycleViewAction>())) {
            Text(
                (basePath?.let(TaskFormat::baseTitle) ?: "Tasks").uppercase(),
                style = TextStyle(color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (subtitle != null) {
                Text("$subtitle  ▾", style = TextStyle(color = p.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
        if (basePath != null) {
            IconButton(R.drawable.ic_add, str(R.string.new_task), actionStartActivity(QuickAddActivity.forBase(context, basePath)))
        }
        IconButton(R.drawable.ic_refresh, str(R.string.refresh), actionRunCallback<RefreshAction>())
    }
}

@Composable
private fun IconButton(icon: Int, description: String, action: Action) {
    Box(GlanceModifier.padding(7.dp).clickable(action)) {
        Image(
            ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(LocalPalette.current.muted),
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = LocalPalette.current.muted, fontSize = 13.sp), modifier = GlanceModifier.padding(vertical = 8.dp))
}

@Composable
private fun TaskList(state: TasksState.Ready, collapsed: Set<String>) {
    val compact = LocalSize.current.height < 160.dp
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(lines(state.sections, collapsed), itemId = { it.id }) { line ->
            when (line) {
                is Line.Header -> SectionHeader(line)
                is Line.Task -> TaskRow(line.task, state.vaultName, showSubtitle = !compact)
            }
        }
    }
}

/** Tapping a section header collapses or expands it, like TickTick's lists. */
@Composable
private fun SectionHeader(line: Line.Header) {
    val p = LocalPalette.current
    Row(
        GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 3.dp)
            .clickable(actionRunCallback<ToggleSectionAction>(actionParametersOf(TasksWidgetKeys.SECTION to line.section.key))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (line.collapsed) "▸" else "▾",
            style = TextStyle(color = p.muted, fontSize = 12.sp),
            modifier = GlanceModifier.width(14.dp),
        )
        Text(
            line.section.label.orEmpty().uppercase(),
            style = TextStyle(color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        Text("${line.section.tasks.size}", style = TextStyle(color = p.muted, fontSize = 11.sp))
    }
}

@Composable
private fun Checkbox(task: TaskUi, size: Int = 20) {
    val p = LocalPalette.current
    Box(
        GlanceModifier.padding(end = 8.dp, top = 6.dp, bottom = 6.dp).clickable(
            actionRunCallback<ToggleDoneAction>(
                actionParametersOf(TasksWidgetKeys.TASK_PATH to task.path, TasksWidgetKeys.TASK_DONE to task.done),
            ),
        ),
    ) {
        Image(
            ImageProvider(if (task.done) R.drawable.ic_task_done else R.drawable.ic_task_open),
            contentDescription = str(if (task.done) R.string.reopen_task else R.string.complete_task),
            modifier = GlanceModifier.size(size.dp),
            colorFilter = ColorFilter.tint(if (task.done) p.muted else p.priority(task.priority)),
        )
    }
}

private fun titleStyle(p: WidgetPalette, done: Boolean, size: Int = 14) = TextStyle(
    color = if (done) p.muted else p.text,
    fontSize = size.sp,
    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
)

@Composable
private fun TaskRow(task: TaskUi, vaultName: String, showSubtitle: Boolean) {
    val p = LocalPalette.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(task)
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openInObsidian(vaultName, task.path)))) {
            Text(task.title, style = titleStyle(p, task.done), maxLines = 1)
            if (showSubtitle) task.subtitle?.let {
                Text(it, style = TextStyle(color = p.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
        task.dueLabel?.let {
            Text(
                it,
                style = TextStyle(color = if (task.overdue) p.overdue else p.muted, fontSize = 11.sp),
                modifier = GlanceModifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Title plus as many of the view's columns as fit the widget width. */
@Composable
private fun TaskTable(state: TasksState.Ready, collapsed: Set<String>) {
    val p = LocalPalette.current
    val width = LocalSize.current.width
    val visible = ((width.value - 170f) / 72f).toInt().coerceIn(1, 4).coerceAtMost(state.columns.size)
    val columns = state.columns.take(visible)
    val cellStyle = TextStyle(color = p.muted, fontSize = 11.sp)

    Row(GlanceModifier.fillMaxWidth().padding(start = 28.dp, end = 6.dp, bottom = 2.dp)) {
        Text(str(R.string.column_title), style = cellStyle.copy(fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
        columns.forEach {
            Text(it.label, style = cellStyle.copy(fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(68.dp), maxLines = 1)
        }
    }
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(lines(state.sections, collapsed), itemId = { it.id }) { line ->
            when (line) {
                is Line.Header -> SectionHeader(line)
                is Line.Task -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(line.task, size = 18)
                    Text(
                        line.task.title,
                        style = titleStyle(p, line.task.done, 13),
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
    val p = LocalPalette.current
    val columns = if (LocalSize.current.width >= 300.dp) 2 else 1
    val tasks = state.sections.flatMap { it.tasks }
    LazyVerticalGrid(GridCells.Fixed(columns), GlanceModifier.fillMaxSize()) {
        items(tasks, itemId = { it.path.hashCode().toLong() }) { task ->
            // Fixed height: the grid sizes each row from one cell, so taller cards would be clipped.
            Box(GlanceModifier.height(CARD_HEIGHT).padding(end = 6.dp, bottom = 6.dp)) {
                Column(
                    GlanceModifier.fillMaxSize().background(p.card).cornerRadius(12.dp)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                        .clickable(actionStartActivity(openInObsidian(state.vaultName, task.path))),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(task, size = 18)
                        Text(task.group.orEmpty(), style = TextStyle(color = p.accent, fontSize = 10.sp), maxLines = 1)
                    }
                    Text(task.title, style = titleStyle(p, task.done, 13), maxLines = 2)
                    val details = listOfNotNull(task.subtitle, task.dueLabel).joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(
                            details,
                            style = TextStyle(color = if (task.overdue) p.overdue else p.muted, fontSize = 11.sp),
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
