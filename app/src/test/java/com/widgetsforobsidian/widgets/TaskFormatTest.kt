package com.widgetsforobsidian.widgets

import com.widgetsforobsidian.widgets.tasks.TaskFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskFormatTest {

    @Test
    fun titles() {
        assertEquals("mídia não pode sumir", TaskFormat.displayTitle("SRY-1 — mídia não pode sumir", "SRY-1"))
        assertEquals("Tarefa 9: Neo4J", TaskFormat.displayTitle("BD-1 — Tarefa 9: Neo4J", "BD-1"))
        assertEquals("SRY-2", TaskFormat.displayTitle(null, "SRY-2"))
        assertEquals("Outro título", TaskFormat.displayTitle("Outro título", "SRY-3"))
    }

    @Test
    fun links() {
        assertEquals("siryus", TaskFormat.linkLabel("[[projects/siryus/siryus]]"))
        assertEquals("Big Data", TaskFormat.linkLabel("[[UFPB/big-data/big-data|Big Data]]"))
        assertEquals("texto", TaskFormat.linkLabel("texto"))
    }

    @Test
    fun `groups dates and base titles`() {
        assertEquals("To do", TaskFormat.groupLabel("todo", "note.status"))
        assertEquals("High", TaskFormat.groupLabel("high", "note.priority"))
        assertEquals("No value", TaskFormat.groupLabel(null, "note.status"))
        val today = LocalDate.of(2026, 11, 22)
        assertEquals("today", TaskFormat.dueLabel(today, today))
        assertEquals("tomorrow", TaskFormat.dueLabel(today.plusDays(1), today))
        assertEquals("11/30", TaskFormat.dueLabel(LocalDate.of(2026, 11, 30), today))
        assertEquals("UFPB", TaskFormat.baseTitle("UFPB/tasks/tasks.base"))
        assertEquals("projects", TaskFormat.baseTitle("projects/tasks/tasks.base"))
    }
}
