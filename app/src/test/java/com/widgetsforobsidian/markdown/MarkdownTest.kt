package com.widgetsforobsidian.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `parses blocks and keeps file line numbers for tasks`() {
        val note = """
            ---
            title: x
            ---
            # Título
            Texto com **negrito**.
            segunda linha

            - item
              - sub
            1. um
            - [ ] fazer
            - [x] feito
            > [!warning] Cuidado
            > corpo
            > citação simples? não, continua no callout
            ```kotlin
            val a = 1
            ```
            | a | b |
            |---|---|
            | 1 | [[x\|y]] |
            ![[img.png]]
            ---
            %% escondido %%
            fim
        """.trimIndent()
        val blocks = Markdown.parseNote(note)
        assertEquals(Block.Heading(1, listOf(Span("Título"))), blocks[0])
        assertEquals(
            listOf(Span("Texto com "), Span("negrito", setOf(Style.BOLD)), Span(".\nsegunda linha")),
            (blocks[1] as Block.Paragraph).text,
        )
        assertEquals(0, (blocks[2] as Block.ListItem).level)
        assertEquals(1, (blocks[3] as Block.ListItem).level)
        assertEquals("1.", (blocks[4] as Block.ListItem).marker)
        val open = blocks[5] as Block.Task
        assertEquals(false, open.checked)
        assertEquals(10, open.line)
        assertEquals("- [ ] fazer", open.raw)
        assertEquals(true, (blocks[6] as Block.Task).checked)
        val callout = blocks[7] as Block.Callout
        assertEquals("warning", callout.type)
        assertEquals("Cuidado", callout.title)
        assertEquals(2, callout.lines.size)
        assertEquals(Block.Code("kotlin", "val a = 1"), blocks[8])
        assertEquals(listOf(listOf("a", "b"), listOf("1", "y")), (blocks[9] as Block.Table).rows)
        assertEquals(Block.Embed("img.png"), blocks[10])
        assertEquals(Block.Rule, blocks[11])
        assertEquals(Block.Paragraph(listOf(Span("fim"))), blocks[12])
        assertEquals(13, blocks.size)
    }

    @Test
    fun `inline styles`() {
        assertEquals(
            listOf(
                Span("a "), Span("b", setOf(Style.ITALIC)), Span(" "), Span("c", setOf(Style.STRIKE)),
                Span(" "), Span("d", setOf(Style.HIGHLIGHT)), Span(" "), Span("e()", setOf(Style.CODE)),
                Span(" "), Span("Alias", setOf(Style.LINK)), Span(" "), Span("Nota", setOf(Style.LINK)),
                Span(" "), Span("site", setOf(Style.LINK)), Span(" "), Span("#tag/sub", setOf(Style.TAG)),
                // The comment is removed but the spaces around it stay, as in the source.
                Span("  fim"),
            ),
            Markdown.inline("a *b* ~~c~~ ==d== `e()` [[x/y|Alias]] [[Nota#Seção]] [site](https://a.b) #tag/sub %%oculto%% fim"),
        )
        assertEquals(listOf(Span("x", setOf(Style.BOLD, Style.ITALIC))), Markdown.inline("***x***").let { listOf(Span(it.joinToString("") { s -> s.text }, it.first().styles)) })
        assertEquals("snake_case_name", Markdown.plain("snake_case_name"))
        assertEquals("issue #123", Markdown.plain("issue #123"))
        assertTrue(Markdown.inline("issue #123").none { Style.TAG in it.styles })
    }

    @Test
    fun `toggles tasks by line, following moved lines`() {
        val content = "a\n- [ ] one\n  - [x] two"
        assertEquals("a\n- [x] one\n  - [x] two", MarkdownEdit.toggleTask(content, 1, "- [ ] one"))
        assertEquals("a\n- [ ] one\n  - [ ] two", MarkdownEdit.toggleTask(content, 2, "  - [x] two"))
        assertEquals("new\na\n- [x] one\n  - [x] two", MarkdownEdit.toggleTask("new\n$content", 1, "- [ ] one"))
        assertEquals(content, MarkdownEdit.toggleTask(content, 1, "- [ ] gone"))
    }

    @Test
    fun `reads current note from workspace`() {
        val json = """
            {"main":{"id":"m","type":"split","children":[{"id":"t","type":"tabs","children":[
              {"id":"leaf1","type":"leaf","state":{"type":"markdown","state":{"file":"notes/a.md"}}},
              {"id":"leaf2","type":"leaf","state":{"type":"markdown","state":{"file":"notes/b.md"}}}]}]},
             "active":"leaf2","lastOpenFiles":["x.canvas","notes/c.md"]}
        """.trimIndent()
        assertEquals("notes/b.md", WorkspaceReader.currentNote(json))
        assertEquals("notes/c.md", WorkspaceReader.currentNote("""{"active":"zzz","lastOpenFiles":["x.canvas","notes/c.md"]}"""))
        assertEquals(null, WorkspaceReader.currentNote("not json"))
    }
}
