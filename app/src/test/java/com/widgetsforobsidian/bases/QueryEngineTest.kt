package com.widgetsforobsidian.bases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QueryEngineTest {

    private val today = LocalDate.of(2026, 11, 22)

    private fun task(
        id: String,
        status: String?,
        priority: String? = null,
        project: String = "[[siryus]]",
        due: String? = null,
        folder: String = "projects/tasks",
        type: String = "Task",
    ) = NoteRow(
        path = "$folder/$id.md", basename = id, folder = folder, extension = "md", mtime = 0, size = 0,
        properties = buildMap {
            put("type", type)
            put("title", "$id — título")
            status?.let { put("status", it) }
            priority?.let { put("priority", it) }
            put("project", project)
            due?.let { put("due", it) }
        },
        tags = listOf("task"),
        links = listOf(project.removePrefix("[[").removeSuffix("]]")),
    )

    private val base = BaseParser.parse(javaClass.getResource("/projects-tasks.base")!!.readText())

    private val rows = listOf(
        task("SRY-1", "doing", "high"),
        task("SRY-2", "todo", "low", due = "2026-11-20"),
        task("SRY-3", "todo", "high", due = "2026-11-25"),
        task("SRY-4", "todo", "high", due = "2026-11-23"),
        task("SRY-5", "done", "medium"),
        task("PDI-1", "todo", null, project = "[[projects/projeto-pdi/projeto-pdi]]"),
        task("OTHER-1", "todo", "high", folder = "notes"),
        task("IDX", null, type = "Directory Index"),
    )

    private fun ids(result: QueryResult) = result.groups.map { g -> g.items.map { it.row.basename } }

    @Test
    fun `parses the real projects base`() {
        assertEquals(9, base.views.size)
        assertEquals("projects/tasks", QueryEngine.folderHint(base.filters))
        val board = base.views[0]
        assertEquals("powerbases-board", board.type)
        assertEquals("note.status", board.groupBy)
        assertEquals(listOf("formula.prio_rank", "due"), board.sort.map { it.property })
        assertEquals("note.project", base.views[1].rowsBy)
        assertEquals("Disciplina", BaseParser.parse("properties:\n  project:\n    displayName: Disciplina\n").displayName("project"))
        assertFalse(base.views.first { it.type == "powerbases-chart" }.isListLike)
    }

    @Test
    fun `board view filters by folder and type, groups by status and sorts by priority then due`() {
        val result = QueryEngine.run(base, 0, rows, today)
        assertEquals(listOf("doing", "todo", "done"), result.groups.map { it.group })
        assertEquals(
            listOf(listOf("SRY-1"), listOf("SRY-4", "SRY-3", "SRY-2", "PDI-1"), listOf("SRY-5")),
            ids(result),
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `view filters hide done tasks and pbRows adds swimlanes`() {
        val result = QueryEngine.run(base, 1, rows, today)
        assertTrue(result.groups.none { it.group == "done" })
        assertEquals(setOf("[[siryus]]", "[[projects/projeto-pdi/projeto-pdi]]"), result.groups.map { it.row }.toSet())
    }

    @Test
    fun `hasLink matches property links by basename`() {
        val siryus = base.views.first { it.name == "Siryus" }
        val result = QueryEngine.run(base, siryus.index, rows, today)
        assertEquals(setOf("SRY-1", "SRY-2", "SRY-3", "SRY-4", "SRY-5"), result.groups.flatMap { g -> g.items.map { it.row.basename } }.toSet())
    }

    @Test
    fun `unsupported expressions keep rows and report a warning`() {
        val b = BaseParser.parse("filters:\n  and:\n    - file.inFolder(\"projects/tasks\")\n    - file.unknownThing()\n")
        val result = QueryEngine.run(b, 0, rows, today)
        assertEquals(7, result.total)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `expressions`() {
        val row = rows[1]
        fun eval(src: String) = Evaluator(row, base.formulas, today).eval(ExprParser.parse(src))
        assertEquals(3.0, eval("formula.prio_rank"))
        assertEquals(true, eval("status == \"todo\" && !(priority == 'high')"))
        assertEquals(true, eval("due < today()"))
        assertEquals(true, eval("file.hasTag(\"task\") || false"))
        assertEquals(false, eval("file.inFolder(\"projects/task\")"))
        assertEquals(7.0, eval("1 + 2 * 3"))
        assertEquals(true, eval("[1, 2].contains(2)"))
        assertEquals("SRY-2.md", eval("file.name"))
        assertNull(eval("missing"))
        assertEquals(true, eval("missing != \"done\""))
    }
}
