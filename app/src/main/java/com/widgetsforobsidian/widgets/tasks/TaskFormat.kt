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
)

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

    fun sections(result: QueryResult, today: LocalDate): List<TaskSection> {
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
                )
            }
            TaskSection(label, tasks)
        }
    }
}
