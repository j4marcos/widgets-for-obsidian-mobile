package com.widgetsforobsidian.widgets.tasks

import com.widgetsforobsidian.bases.Evaluator
import com.widgetsforobsidian.bases.QueryResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TaskUi(
    val path: String,
    val title: String,
    val subtitle: String?,
    val dueLabel: String?,
    val overdue: Boolean,
    val priority: String?,
    val done: Boolean,
    /** Formatted values of [TaskSection]'s table columns, in order. */
    val cells: List<String> = emptyList(),
    /** Group label, shown on cards since the grid has no section headers. */
    val group: String? = null,
)

data class TableColumn(val id: String, val label: String)

enum class TaskLayout { LIST, TABLE, CARDS;
    companion object {
        /** `auto` follows the view type: tables stay tables, galleries become cards, boards become lists. */
        fun resolve(preference: String?, viewType: String): TaskLayout = when (preference) {
            "list" -> LIST
            "table" -> TABLE
            "cards" -> CARDS
            else -> when {
                "table" in viewType -> TABLE
                "gallery" in viewType || "cards" in viewType -> CARDS
                else -> LIST
            }
        }
    }
}

data class TaskSection(val label: String?, val tasks: List<TaskUi>) {
    /** Stable id used to remember whether the section is collapsed. */
    val key: String get() = label ?: ""
}

/** How the widget splits tasks into sections. */
enum class Grouping {
    /** One section per `project`, tasks without one under [TaskLabels.inbox]. */
    PROJECT,
    /** The view's own grouping (`pbGroup` / `pbRows` / `groupBy`). */
    VIEW,
}

/** Pure formatting rules for task notes (`title`, `status`, `priority`, `project`, `due`). */
object TaskFormat {


    /** `"SRY-1 — mídia não pode sumir"` → `"mídia não pode sumir"`; falls back to the file name. */
    fun displayTitle(title: Any?, basename: String): String {
        val raw = Evaluator.display(title).trim()
        if (raw.isEmpty()) return basename
        val stripped = raw.removePrefix(basename).trimStart(' ', '—', '–', '-', ':').trim()
        return stripped.ifEmpty { raw }
    }

    /** `[[UFPB/big-data/big-data|Big Data]]` → `Big Data`; `[[projects/siryus/siryus]]` → `siryus`. */
    fun linkLabel(value: Any?): String {
        val s = Evaluator.display(if (value is List<*>) value.firstOrNull() else value).trim()
        if (!s.startsWith("[[")) return s
        val inner = s.removePrefix("[[").removeSuffix("]]")
        return if ('|' in inner) inner.substringAfter('|') else inner.substringAfterLast('/').removeSuffix(".md")
    }

    fun groupLabel(value: Any?, propertyId: String?, labels: TaskLabels = TaskLabels.ENGLISH): String {
        if (Evaluator.isEmpty(value)) return labels.noValue
        val key = Evaluator.display(value).lowercase()
        return when (propertyId?.substringAfterLast('.')) {
            "status" -> labels.status(key)
            "priority" -> labels.priority(key)
            else -> null
        } ?: linkLabel(value)
    }

    fun dueDate(value: Any?): LocalDate? =
        runCatching { LocalDate.parse(Evaluator.display(value).take(10)) }.getOrNull()

    fun dueLabel(due: LocalDate, today: LocalDate, labels: TaskLabels = TaskLabels.ENGLISH): String = when (due) {
        today -> labels.today
        today.plusDays(1) -> labels.tomorrow
        today.minusDays(1) -> labels.yesterday
        else -> due.format(DateTimeFormatter.ofPattern(labels.shortDatePattern))
    }

    /** Header title for a base: `UFPB/tasks/tasks.base` → `UFPB`, `projects/tasks/tasks.base` → `projects`. */
    fun baseTitle(path: String): String {
        val parts = path.split('/')
        val name = parts.last().substringBeforeLast('.')
        return parts.dropLast(1).lastOrNull { !it.equals(name, ignoreCase = true) } ?: name
    }

    /** Table columns for a view: its `order` without the title, which always comes first. */
    fun columns(order: List<String>, displayName: (String) -> String): List<TableColumn> =
        order.filter { it.substringAfterLast('.') !in setOf("title", "name", "basename") }
            .map { TableColumn(it, displayName(it)) }

    fun cell(value: Any?, propertyId: String, today: LocalDate, labels: TaskLabels = TaskLabels.ENGLISH): String {
        if (Evaluator.isEmpty(value)) return ""
        val key = propertyId.substringAfterLast('.')
        if (key == "status" || key == "priority") return groupLabel(value, propertyId, labels)
        dueDate(value)?.takeIf { Evaluator.display(value).length >= 10 }?.let { return dueLabel(it, today, labels) }
        return if (value is List<*>) value.joinToString(", ") { linkLabel(it) } else linkLabel(value)
    }

    fun sections(
        result: QueryResult,
        today: LocalDate,
        columns: List<TableColumn> = emptyList(),
        grouping: Grouping = Grouping.VIEW,
        hideDone: Boolean = false,
        labels: TaskLabels = TaskLabels.ENGLISH,
    ): List<TaskSection> {
        val view = result.view
        val byProject = grouping == Grouping.PROJECT
        val grouped = byProject || view.groupBy != null || view.rowsBy != null
        val groupedProps = if (byProject) listOf("project")
        else setOfNotNull(view.groupBy, view.rowsBy).map { it.substringAfterLast('.') }

        val viewSections = result.groups.map { group ->
            val label = if (!grouped || byProject) null else listOfNotNull(
                view.rowsBy?.let { groupLabel(group.row, it, labels) },
                view.groupBy?.let { groupLabel(group.group, it, labels) },
            ).joinToString(" · ")
            label to group.items.map { item ->
                val due = dueDate(item.value("due"))
                val status = Evaluator.display(item.value("status")).lowercase()
                val done = status == "done"
                val project = linkLabel(item.value("project")).ifEmpty { null }
                TaskUi(
                    path = item.row.path,
                    title = displayTitle(item.value("title"), item.row.basename),
                    subtitle = when {
                        "project" !in groupedProps -> listOfNotNull(project, labels.statusBadge(status)).joinToString(" · ")
                        else -> labels.statusBadge(status)
                    }?.ifEmpty { null },
                    dueLabel = due?.let { dueLabel(it, today, labels) },
                    overdue = !done && due != null && due < today,
                    priority = Evaluator.display(item.value("priority")).lowercase().ifEmpty { null },
                    done = done,
                    cells = columns.map { cell(item.value(it.id), it.id, today, labels) },
                    group = if (byProject) project ?: labels.inbox else label,
                )
            }
        }

        val sections = if (!byProject) {
            viewSections.map { (label, tasks) -> TaskSection(label, tasks) }
        } else {
            // Keep the view's sort inside each project; inbox first, then projects A–Z.
            viewSections.flatMap { it.second }
                .groupBy { it.group ?: labels.inbox }
                .toSortedMap(compareBy<String> { it != labels.inbox }.thenBy { it.lowercase() })
                .map { (label, tasks) -> TaskSection(label, tasks) }
        }
        return sections
            .map { if (hideDone) it.copy(tasks = it.tasks.filterNot(TaskUi::done)) else it }
            .filter { it.tasks.isNotEmpty() || it.label == null }
    }
}
