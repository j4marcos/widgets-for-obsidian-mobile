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

data class TaskSection(val label: String?, val tasks: List<TaskUi>)

/** Pure formatting rules for task notes (`title`, `status`, `priority`, `project`, `due`). */
object TaskFormat {

    private val STATUS_LABELS = mapOf(
        "todo" to "A fazer",
        "doing" to "Fazendo",
        "blocked" to "Bloqueada",
        "done" to "Concluídas",
    )
    private val PRIORITY_LABELS = mapOf("high" to "Alta", "medium" to "Média", "low" to "Baixa")
    private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM")

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

    fun groupLabel(value: Any?, propertyId: String?): String {
        if (Evaluator.isEmpty(value)) return "Sem valor"
        val key = Evaluator.display(value).lowercase()
        return when (propertyId?.substringAfterLast('.')) {
            "status" -> STATUS_LABELS[key]
            "priority" -> PRIORITY_LABELS[key]
            else -> null
        } ?: linkLabel(value)
    }

    fun dueDate(value: Any?): LocalDate? =
        runCatching { LocalDate.parse(Evaluator.display(value).take(10)) }.getOrNull()

    fun dueLabel(due: LocalDate, today: LocalDate): String = when (due) {
        today -> "hoje"
        today.plusDays(1) -> "amanhã"
        today.minusDays(1) -> "ontem"
        else -> due.format(SHORT_DATE)
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

    fun cell(value: Any?, propertyId: String, today: LocalDate): String {
        if (Evaluator.isEmpty(value)) return ""
        val key = propertyId.substringAfterLast('.')
        if (key == "status" || key == "priority") return groupLabel(value, propertyId)
        dueDate(value)?.takeIf { Evaluator.display(value).length >= 10 }?.let { return dueLabel(it, today) }
        return if (value is List<*>) value.joinToString(", ") { linkLabel(it) } else linkLabel(value)
    }

    fun sections(result: QueryResult, today: LocalDate, columns: List<TableColumn> = emptyList()): List<TaskSection> {
        val view = result.view
        val grouped = view.groupBy != null || view.rowsBy != null
        val groupedProps = setOfNotNull(view.groupBy, view.rowsBy).map { it.substringAfterLast('.') }
        return result.groups.map { group ->
            val label = if (!grouped) null else listOfNotNull(
                view.rowsBy?.let { groupLabel(group.row, it) },
                view.groupBy?.let { groupLabel(group.group, it) },
            ).joinToString(" · ")
            val tasks = group.items.map { item ->
                val due = dueDate(item.value("due"))
                val done = Evaluator.display(item.value("status")).equals("done", ignoreCase = true)
                TaskUi(
                    path = item.row.path,
                    title = displayTitle(item.value("title"), item.row.basename),
                    subtitle = if ("project" in groupedProps) null
                    else linkLabel(item.value("project")).ifEmpty { null },
                    dueLabel = due?.let { dueLabel(it, today) },
                    overdue = !done && due != null && due < today,
                    priority = Evaluator.display(item.value("priority")).lowercase().ifEmpty { null },
                    done = done,
                    cells = columns.map { cell(item.value(it.id), it.id, today) },
                    group = label,
                )
            }
            TaskSection(label, tasks)
        }
    }
}
