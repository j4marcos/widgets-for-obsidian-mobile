package com.widgetsforobsidian.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class FrontmatterEditorTest {

    @Test
    fun `replaces only the target line`() {
        val before = "---\ntype: Task\nstatus: doing\ntags: [ siryus, task ]\n---\n# Body\nstatus: not frontmatter\n"
        val after = FrontmatterEditor.set(before, "status", "done")
        assertEquals("---\ntype: Task\nstatus: done\ntags: [ siryus, task ]\n---\n# Body\nstatus: not frontmatter\n", after)
    }

    @Test
    fun `replaces block values with their continuation lines`() {
        val before = "---\nstatus:\n  - a\n  - b\nnext: 1\n---\n"
        assertEquals("---\nstatus: done\nnext: 1\n---\n", FrontmatterEditor.set(before, "status", "done"))
    }

    @Test
    fun `adds missing key and does not match nested keys`() {
        val before = "---\nupdated:\n  status: x\n---\nbody"
        assertEquals("---\nupdated:\n  status: x\nstatus: done\n---\nbody", FrontmatterEditor.set(before, "status", "done"))
    }

    @Test
    fun `creates frontmatter when missing and keeps CRLF`() {
        assertEquals("---\nstatus: done\n---\nhello", FrontmatterEditor.set("hello", "status", "done"))
        assertEquals("---\r\nstatus: done\r\n---\r\nx", FrontmatterEditor.set("---\r\nstatus: todo\r\n---\r\nx", "status", "done"))
    }
}
