package com.widgetsforobsidian.markdown

enum class Style { BOLD, ITALIC, STRIKE, CODE, HIGHLIGHT, LINK, TAG }

data class Span(val text: String, val styles: Set<Style> = emptySet())

sealed interface Block {
    data class Heading(val level: Int, val text: List<Span>) : Block
    data class Paragraph(val text: List<Span>) : Block
    data class ListItem(val level: Int, val marker: String, val text: List<Span>) : Block
    /** [line] is the 0-based line of the task in the whole file (frontmatter included). */
    data class Task(val level: Int, val checked: Boolean, val text: List<Span>, val line: Int, val raw: String) : Block
    data class Quote(val lines: List<List<Span>>) : Block
    data class Callout(val type: String, val title: String, val lines: List<List<Span>>) : Block
    data class Code(val language: String, val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    data class Embed(val target: String) : Block
    data object Rule : Block
}

/**
 * Small Obsidian-flavoured markdown parser producing display blocks for widgets.
 * It is not a CommonMark implementation: it covers what notes commonly use (headings, lists,
 * tasks, quotes, callouts, code, tables, embeds, wikilinks, tags, highlights, comments).
 */
object Markdown {

    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val RULE = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val TASK = Regex("""^(\s*)[-*+]\s+\[(.)]\s?(.*)$""")
    private val LIST = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")
    private val FENCE = Regex("""^\s*(```+|~~~+)\s*([\w+-]*)""")
    private val CALLOUT = Regex("""^\[!([\w-]+)][+-]?\s*(.*)$""")
    private val EMBED_LINE = Regex("""^!\[\[([^\]]+)]]\s*$""")
    private val CELL_SEPARATOR = Regex("""(?<!\\)\|""")
    private val TABLE_SEPARATOR = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")

    /** Parses a whole note; the frontmatter is skipped but counted for task line numbers. */
    fun parseNote(content: String): List<Block> {
        val text = content.removePrefix("﻿").replace("\r\n", "\n")
        val lines = text.split('\n')
        var start = 0
        if (lines.firstOrNull()?.trimEnd() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..." }
            if (end != null) start = end + 1
        }
        return parse(lines, start)
    }

    fun parse(lines: List<String>, start: Int = 0): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(inline(paragraph.joinToString("\n")))
            paragraph.clear()
        }

        var i = start
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            FENCE.find(line)?.let { fence ->
                flush()
                val marker = fence.groupValues[1]
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(marker)) body += lines[i++]
                blocks += Block.Code(fence.groupValues[2], body.joinToString("\n"))
                i++
                return@let
            } ?: run {
                when {
                    trimmed.startsWith("%%") -> {
                        // Obsidian comment: hidden until the closing %%.
                        flush()
                        val closedOnSameLine = trimmed.length > 2 && trimmed.substring(2).contains("%%")
                        if (!closedOnSameLine) {
                            i++
                            while (i < lines.size && !lines[i].contains("%%")) i++
                        }
                        i++
                    }
                    trimmed.isEmpty() -> { flush(); i++ }
                    HEADING.matches(trimmed) && !line.startsWith("    ") -> {
                        flush()
                        val m = HEADING.matchEntire(trimmed)!!
                        blocks += Block.Heading(m.groupValues[1].length, inline(m.groupValues[2]))
                        i++
                    }
                    RULE.matches(line) -> { flush(); blocks += Block.Rule; i++ }
                    EMBED_LINE.matches(trimmed) -> {
                        flush()
                        blocks += Block.Embed(EMBED_LINE.matchEntire(trimmed)!!.groupValues[1].substringBefore('|'))
                        i++
                    }
                    TASK.matches(line) -> {
                        flush()
                        val m = TASK.matchEntire(line)!!
                        blocks += Block.Task(level(m.groupValues[1]), m.groupValues[2] != " ", inline(m.groupValues[3]), i, line)
                        i++
                    }
                    LIST.matches(line) -> {
                        flush()
                        val m = LIST.matchEntire(line)!!
                        val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
                        blocks += Block.ListItem(level(m.groupValues[1]), marker, inline(m.groupValues[3]))
                        i++
                    }
                    trimmed.startsWith(">") -> {
                        flush()
                        val quoted = mutableListOf<String>()
                        while (i < lines.size && lines[i].trim().startsWith(">")) {
                            quoted += lines[i].trim().removePrefix(">").removePrefix(" ")
                            i++
                        }
                        val callout = CALLOUT.matchEntire(quoted.first().trim())
                        blocks += if (callout != null) {
                            val type = callout.groupValues[1].lowercase()
                            Block.Callout(
                                type,
                                callout.groupValues[2].ifBlank { type.replaceFirstChar { it.uppercase() } },
                                quoted.drop(1).filter { it.isNotBlank() }.map(::inline),
                            )
                        } else {
                            Block.Quote(quoted.map(::inline))
                        }
                    }
                    trimmed.startsWith("|") -> {
                        flush()
                        val rows = mutableListOf<List<String>>()
                        while (i < lines.size && lines[i].trim().startsWith("|")) {
                            val row = lines[i].trim()
                            if (!TABLE_SEPARATOR.matches(row)) {
                                // `\|` escapes a pipe inside a cell, e.g. `[[note\|alias]]`.
                                rows += row.removePrefix("|").removeSuffix("|").split(CELL_SEPARATOR)
                                    .map { plain(it.replace("\\|", "|").trim()) }
                            }
                            i++
                        }
                        blocks += Block.Table(rows)
                    }
                    else -> { paragraph += trimmed; i++ }
                }
            }
        }
        flush()
        return blocks
    }

    private fun level(indent: String): Int {
        val tabs = indent.count { it == '\t' }
        val spaces = indent.count { it == ' ' }
        return if (tabs > 0) tabs + spaces / 4 else (spaces + 2) / 4
    }

    // Order matters: the leftmost match wins, then the first alternative listed.
    private val INLINE = Regex(
        listOf(
            """`(?<code>[^`]+)`""",
            """%%(?<comment>.*?)%%""",
            """!\[\[(?<embed>[^\]]+)]]""",
            """\[\[(?<wikilink>[^\]]+)]]""",
            """\[(?<mdtext>[^\]]+)]\((?<mdurl>[^)\s]+)\)""",
            """\*\*\*(?<bolditalic>.+?)\*\*\*""",
            """\*\*(?<bold>.+?)\*\*""",
            """__(?<bold2>.+?)__""",
            """~~(?<strike>.+?)~~""",
            """==(?<highlight>.+?)==""",
            """\*(?<italic>[^*\s](?:[^*]*[^*\s])?)\*""",
            """(?<![\w])_(?<italic2>[^_\s](?:[^_]*[^_\s])?)_(?![\w])""",
            """(?<=^|\s)#(?<tag>[\p{L}\p{N}_/-]*[\p{L}_][\p{L}\p{N}_/-]*)""",
        ).joinToString("|"),
    )

    fun inline(text: String, inherited: Set<Style> = emptySet()): List<Span> {
        val out = mutableListOf<Span>()
        var pos = 0
        fun add(s: String, styles: Set<Style>) {
            if (s.isEmpty()) return
            val last = out.lastOrNull()
            if (last != null && last.styles == styles) out[out.size - 1] = Span(last.text + s, styles) else out += Span(s, styles)
        }
        for (m in INLINE.findAll(text)) {
            add(text.substring(pos, m.range.first), inherited)
            val g = m.groups
            when {
                g["code"] != null -> add(g["code"]!!.value, inherited + Style.CODE)
                g["comment"] != null -> Unit
                g["embed"] != null -> add("🔗 " + g["embed"]!!.value.substringBefore('|'), inherited + Style.LINK)
                g["wikilink"] != null -> {
                    val target = g["wikilink"]!!.value
                    val label = if ('|' in target) target.substringAfter('|') else target.substringBefore('#').ifEmpty { target }
                    add(label, inherited + Style.LINK)
                }
                g["mdtext"] != null -> inline(g["mdtext"]!!.value, inherited + Style.LINK).forEach { add(it.text, it.styles) }
                g["bolditalic"] != null ->
                    inline(g["bolditalic"]!!.value, inherited + Style.BOLD + Style.ITALIC).forEach { add(it.text, it.styles) }
                g["bold"] != null || g["bold2"] != null ->
                    inline((g["bold"] ?: g["bold2"])!!.value, inherited + Style.BOLD).forEach { add(it.text, it.styles) }
                g["strike"] != null -> inline(g["strike"]!!.value, inherited + Style.STRIKE).forEach { add(it.text, it.styles) }
                g["highlight"] != null -> inline(g["highlight"]!!.value, inherited + Style.HIGHLIGHT).forEach { add(it.text, it.styles) }
                g["italic"] != null || g["italic2"] != null ->
                    inline((g["italic"] ?: g["italic2"])!!.value, inherited + Style.ITALIC).forEach { add(it.text, it.styles) }
                g["tag"] != null -> add("#" + g["tag"]!!.value, inherited + Style.TAG)
            }
            pos = m.range.last + 1
        }
        add(text.substring(pos), inherited)
        return out
    }

    fun plain(text: String): String = inline(text).joinToString("") { it.text }
}

/** Line edits on raw markdown. */
object MarkdownEdit {

    private val TASK = Regex("""^(\s*[-*+]\s+\[)(.)(].*)$""")

    /**
     * Toggles the checkbox on [line], which must still be [expected]. If the file changed and the
     * task moved, the first line equal to [expected] is toggled instead; if none, nothing changes.
     */
    fun toggleTask(content: String, line: Int, expected: String): String {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"
        val lines = content.split(newline).toMutableList()
        val target = line.takeIf { lines.getOrNull(it) == expected } ?: lines.indexOf(expected).takeIf { it >= 0 }
            ?: return content
        val m = TASK.matchEntire(lines[target]) ?: return content
        val mark = if (m.groupValues[2] == " ") "x" else " "
        lines[target] = m.groupValues[1] + mark + m.groupValues[3]
        return lines.joinToString(newline)
    }
}
