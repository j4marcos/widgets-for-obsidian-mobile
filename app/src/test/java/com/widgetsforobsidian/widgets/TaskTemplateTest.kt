package com.widgetsforobsidian.widgets

import com.widgetsforobsidian.bases.BaseRepository
import com.widgetsforobsidian.bases.NoteRow
import com.widgetsforobsidian.vault.NoteParser
import com.widgetsforobsidian.widgets.tasks.NewTask
import com.widgetsforobsidian.widgets.tasks.TaskFormat
import com.widgetsforobsidian.widgets.tasks.TaskLayout
import com.widgetsforobsidian.widgets.tasks.TaskTemplate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskTemplateTest {

    private fun row(id: String, project: String?, owner: String = "j4marcos", tags: List<String> = listOf("task")) = NoteRow(
        "projects/tasks/$id.md", id, "projects/tasks", "md", 0, 0,
        buildMap { project?.let { put("project", it) }; put("owner", owner) }, tags, emptyList(),
    )

    private val rows = listOf(
        row("SRY-1", "[[siryus]]", tags = listOf("siryus", "task", "media")),
        row("SRY-20", "[[projects/siryus/siryus]]", tags = listOf("siryus", "task")),
        row("SRY-3", "[[siryus]]", tags = listOf("task", "siryus")),
        row("PDI-4", "[[projects/projeto-pdi/projeto-pdi]]", owner = "yuri"),
        row("index", null),
    )

    @Test
    fun `next id uses the project's prefix and the highest number`() {
        assertEquals("SRY-21", TaskTemplate.nextId("[[siryus]]", rows))
        assertEquals("PDI-5", TaskTemplate.nextId("[[projects/projeto-pdi/projeto-pdi]]", rows))
        assertEquals("NOVO-1", TaskTemplate.nextId("[[novo-projeto]]", rows))
        assertEquals("INBOX-1", TaskTemplate.nextId(null, emptyList()))
        assertEquals("SRY-22", TaskTemplate.bump("SRY-21"))
    }

    @Test
    fun `defaults and shared tags`() {
        val d = TaskTemplate.defaults(rows)
        assertEquals("[[siryus]]", d.projects.first())
        assertEquals("j4marcos", d.owner)
        assertEquals(listOf("task", "siryus"), TaskTemplate.commonTags("[[siryus]]", rows))
    }

    @Test
    fun `rendered note parses back with the expected properties`() {
        val text = TaskTemplate.render(
            "SRY-21",
            NewTask("corrigir \"login\"", "[[siryus]]", "high", LocalDate.of(2026, 12, 1)),
            "j4marcos", listOf("task", "siryus"), LocalDate.of(2026, 11, 22),
        )
        val parsed = NoteParser.parse(text)
        val props = parsed.properties.mapValues { BaseRepository.toValue(it.value) }
        assertEquals("Task", props["type"])
        assertEquals("SRY-21 — corrigir \"login\"", props["title"])
        assertEquals("todo", props["status"])
        assertEquals("[[siryus]]", props["project"])
        assertEquals("high", props["priority"])
        assertEquals("2026-12-01", props["due"])
        assertEquals(listOf("task", "siryus"), parsed.tags)
        assertEquals(listOf("siryus"), parsed.links)
        assertEquals("corrigir \"login\"", TaskFormat.displayTitle(props["title"], "SRY-21"))
    }

    @Test
    fun `layouts and cells`() {
        assertEquals(TaskLayout.LIST, TaskLayout.resolve("auto", "powerbases-board"))
        assertEquals(TaskLayout.TABLE, TaskLayout.resolve(null, "powerbases-table"))
        assertEquals(TaskLayout.CARDS, TaskLayout.resolve(null, "cards"))
        assertEquals(TaskLayout.CARDS, TaskLayout.resolve("cards", "table"))
        val today = LocalDate.of(2026, 11, 22)
        assertEquals("High", TaskFormat.cell("high", "priority", today))
        assertEquals("tomorrow", TaskFormat.cell("2026-11-23", "due", today))
        assertEquals("siryus", TaskFormat.cell("[[projects/siryus/siryus]]", "project", today))
        assertEquals("", TaskFormat.cell(null, "due", today))
        assertEquals(listOf("status", "due"), TaskFormat.columns(listOf("file.name", "title", "status", "due")) { it }.map { it.id })
    }
}
