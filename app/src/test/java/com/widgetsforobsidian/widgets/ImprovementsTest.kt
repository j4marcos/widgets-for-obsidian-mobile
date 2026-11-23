package com.widgetsforobsidian.widgets

import com.widgetsforobsidian.bases.BaseParser
import com.widgetsforobsidian.bases.NoteRow
import com.widgetsforobsidian.bases.QueryEngine
import com.widgetsforobsidian.settings.ObsidianTheme
import com.widgetsforobsidian.widgets.capture.QuickNote
import com.widgetsforobsidian.widgets.tasks.Grouping
import com.widgetsforobsidian.widgets.tasks.TaskFormat
import com.widgetsforobsidian.widgets.tasks.TaskLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ImprovementsTest {

    @Test
    fun `obsidian theme`() {
        assertEquals(ObsidianTheme(ObsidianTheme.Mode.DARK, 0xFF7C3AED.toInt()),
            ObsidianTheme.parse("""{"theme":"obsidian","accentColor":"#7c3aed","cssTheme":"Minimal"}"""))
        assertEquals(ObsidianTheme.Mode.LIGHT, ObsidianTheme.parse("""{"theme":"moonstone"}""").mode)
        assertEquals(ObsidianTheme(), ObsidianTheme.parse("""{"showRibbon":true}"""))
        assertEquals(ObsidianTheme(), ObsidianTheme.parse("broken"))
        assertEquals(0xFFAABBCC.toInt(), ObsidianTheme.parseHex("#abc"))
        assertNull(ObsidianTheme.parseHex(""))
    }

    @Test
    fun `quick note names`() {
        val now = LocalDateTime.of(2026, 11, 23, 9, 5)
        assertEquals("Ideia sobre app", QuickNote.baseName("  Ideia: sobre/app ", now))
        assertEquals("a b c", QuickNote.baseName("a#b[c]", now))
        assertEquals("Note 2026-11-23 0905", QuickNote.baseName("   ", now))
        assertEquals("Nota 2026-11-23 0905", QuickNote.baseName("", now, "Nota"))
        val taken = setOf("x.md", "x 1.md")
        assertEquals("x 2.md", QuickNote.uniqueFileName("x") { it in taken })
        assertEquals("---\ncreated: 2026-11-23T09:05\n---\n", QuickNote.render(now))
    }

    private fun task(id: String, status: String, project: String?) = NoteRow(
        "t/$id.md", id, "t", "md", 0, 0,
        buildMap {
            put("type", "Task"); put("status", status); put("title", "$id — $id")
            project?.let { put("project", it) }
        },
        emptyList(), emptyList(),
    )

    @Test
    fun `project sections with inbox first and hidden done`() {
        val base = BaseParser.parse("filters:\n  and:\n    - file.inFolder(\"t\")\nviews:\n  - type: powerbases-board\n    name: B\n    pbGroup: note.status\n")
        val rows = listOf(
            task("Z-1", "todo", "[[zeta]]"),
            task("A-1", "doing", "[[proj/alpha]]"),
            task("A-2", "done", "[[proj/alpha]]"),
            task("I-1", "todo", null),
            task("Z-2", "done", "[[zeta]]"),
        )
        val result = QueryEngine.run(base, 0, rows, LocalDate.of(2026, 11, 23))
        val sections = TaskFormat.sections(result, LocalDate.of(2026, 11, 23), grouping = Grouping.PROJECT, hideDone = true)
        assertEquals(listOf(TaskLabels.ENGLISH.inbox, "alpha", "zeta"), sections.map { it.label })
        assertEquals(listOf(listOf("I-1"), listOf("A-1"), listOf("Z-1")), sections.map { s -> s.tasks.map { it.title } })
        // In project sections the subtitle shows a non-default status instead of the project.
        assertEquals("In progress", sections[1].tasks.single().subtitle)

        val all = TaskFormat.sections(result, LocalDate.of(2026, 11, 23), grouping = Grouping.PROJECT, hideDone = false)
        assertEquals(2, all.first { it.label == "alpha" }.tasks.size)
        val byView = TaskFormat.sections(result, LocalDate.of(2026, 11, 23), grouping = Grouping.VIEW, hideDone = true)
        assertTrue(byView.none { it.label == "Done" })
    }
}
