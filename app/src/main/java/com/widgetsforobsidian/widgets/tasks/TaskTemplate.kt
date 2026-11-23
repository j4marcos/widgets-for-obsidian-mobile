package com.widgetsforobsidian.widgets.tasks

import com.widgetsforobsidian.bases.Evaluator
import com.widgetsforobsidian.bases.NoteRow
import java.time.LocalDate

data class NewTask(
    val title: String,
    /** Raw `project` value as stored in existing tasks, e.g. `[[siryus]]`. */
    val project: String?,
    val priority: String,
    val due: LocalDate?,
)

/** What the new-task popup suggests, learned from the tasks already in the base. */
data class TaskDefaults(
    val projects: List<String>,
    val owner: String?,
)

/**
 * Builds new task notes that look like the existing ones: `PREFIX-n` file names and the same
 * frontmatter keys (`type`, `title`, `status`, `priority`, `project`, `owner`, `start`, `due`, `tags`).
 */
object TaskTemplate {

    private val ID = Regex("""^([A-Za-z][A-Za-z0-9]*)-(\d+)$""")

    fun defaults(rows: List<NoteRow>): TaskDefaults {
        val projects = rows.mapNotNull { projectOf(it) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
        val owner = rows.mapNotNull { Evaluator.display(it.properties["owner"]).ifEmpty { null } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return TaskDefaults(projects, owner)
    }

    fun projectOf(row: NoteRow): String? =
        Evaluator.display(row.properties["project"]).trim().ifEmpty { null }

    /**
     * Next free id for [project]: the prefix most used by that project's tasks (`SRY` for siryus),
     * or one derived from the project name, followed by the highest number in use plus one.
     */
    fun nextId(project: String?, rows: List<NoteRow>): String {
        val projectKey = project?.let(Evaluator::linkKey)
        val prefix = rows
            .filter { projectKey != null && projectOf(it)?.let(Evaluator::linkKey) == projectKey }
            .mapNotNull { ID.matchEntire(it.basename)?.groupValues?.get(1) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: derivePrefix(project)
        val max = rows.mapNotNull { row ->
            ID.matchEntire(row.basename)?.takeIf { it.groupValues[1].equals(prefix, ignoreCase = true) }
                ?.groupValues?.get(2)?.toIntOrNull()
        }.maxOrNull() ?: 0
        return "$prefix-${max + 1}"
    }

    /** `SRY-21` → `SRY-22`. */
    fun bump(id: String): String {
        val m = ID.matchEntire(id) ?: return "$id-2"
        return "${m.groupValues[1]}-${m.groupValues[2].toInt() + 1}"
    }

    fun derivePrefix(project: String?): String {
        val label = project?.let(TaskFormat::linkLabel).orEmpty()
        val letters = label.filter { it.isLetter() && it.code < 128 }.uppercase()
        return letters.take(4).ifEmpty { if (project == null) "INBOX" else "TASK" }
    }

    /** Tags every existing task of the project shares (besides `task`), so new tasks match them. */
    fun commonTags(project: String?, rows: List<NoteRow>): List<String> {
        val key = project?.let(Evaluator::linkKey)
        val same = rows.filter { key != null && projectOf(it)?.let(Evaluator::linkKey) == key }
        val shared = same.map { it.tags.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty()
        return listOf("task") + shared.filter { it != "task" }.sorted()
    }

    fun render(id: String, task: NewTask, owner: String?, tags: List<String>, today: LocalDate): String {
        val fullTitle = "$id — ${task.title.trim()}"
        return buildString {
            appendLine("---")
            appendLine("type: Task")
            appendLine("title: ${quote(fullTitle)}")
            appendLine("status: todo")
            task.project?.let { appendLine("project: ${quote(it)}") }
            owner?.let { appendLine("owner: $it") }
            appendLine("priority: ${task.priority}")
            appendLine("start: $today")
            appendLine("due: ${task.due ?: ""}".trimEnd())
            appendLine("depends_on: []")
            appendLine("tags: [${tags.joinToString(", ")}]")
            appendLine("---")
            appendLine()
            appendLine("# $fullTitle")
        }
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
