package com.widgetsforobsidian.vault

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParserTest {

    @Test
    fun `parses frontmatter properties and body`() {
        val note = NoteParser.parse(
            """
            ---
            status: doing
            rating: 4
            done: false
            aliases: [Foo, Bar]
            ---
            # Title
            Body
            """.trimIndent(),
        )
        assertEquals(JsonPrimitive("doing"), note.properties["status"])
        assertEquals(JsonPrimitive(4), note.properties["rating"])
        assertEquals(JsonPrimitive(false), note.properties["done"])
        assertEquals(2, note.properties["aliases"]!!.jsonArray.size)
        assertEquals("# Title\nBody", note.body)
    }

    @Test
    fun `note without frontmatter keeps whole body`() {
        val text = "Just text\n---\nnot yaml"
        val note = NoteParser.parse(text)
        assertTrue(note.properties.isEmpty())
        assertEquals(text, note.body)
    }

    @Test
    fun `unterminated frontmatter is treated as body`() {
        val (yaml, body) = NoteParser.splitFrontmatter("---\nfoo: bar\nno end")
        assertNull(yaml)
        assertEquals("---\nfoo: bar\nno end", body)
    }

    @Test
    fun `invalid yaml yields empty properties`() {
        val note = NoteParser.parse("---\nfoo: [unclosed\n---\nbody")
        assertTrue(note.properties.isEmpty())
        assertEquals("body", note.body)
    }

    @Test
    fun `handles CRLF and BOM`() {
        val note = NoteParser.parse("﻿---\r\ntitle: x\r\n---\r\nhello")
        assertEquals(JsonPrimitive("x"), note.properties["title"])
        assertEquals("hello", note.body)
    }

    @Test
    fun `collects property and inline tags, ignoring code and headings`() {
        val note = NoteParser.parse(
            """
            ---
            tags: [project, "#work"]
            ---
            # Heading is not a tag
            Text with #idea and #area/health, issue #123.
            `#notatag` inline code
            ```
            #alsonot
            ```
            """.trimIndent(),
        )
        assertEquals(listOf("project", "work", "idea", "area/health"), note.tags)
    }

    @Test
    fun `string tags property is split`() {
        val note = NoteParser.parse("---\ntags: a, b c\n---\n")
        assertEquals(listOf("a", "b", "c"), note.tags)
    }

    @Test
    fun `extracts wikilink and embed targets`() {
        val note = NoteParser.parse("See [[Note A]], [[Note B#Section|alias]], ![[img.png]] and [[Note A]].")
        assertEquals(listOf("Note A", "Note B", "img.png"), note.links)
    }
}
