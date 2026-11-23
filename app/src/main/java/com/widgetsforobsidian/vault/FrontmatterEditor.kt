package com.widgetsforobsidian.vault

/**
 * Edits single top-level frontmatter fields in place.
 *
 * Only the target line (and its indented continuation lines) is touched, so the rest of the
 * YAML keeps its original formatting, comments and key order. Re-serializing the whole block
 * would rewrite quoting and flow lists written by hand or by other tools.
 */
object FrontmatterEditor {

    fun set(content: String, key: String, value: String): String {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"
        val bom = if (content.startsWith("﻿")) "﻿" else ""
        val lines = content.removePrefix(bom).split(newline).toMutableList()
        val line = "$key: $value"

        val hasFrontmatter = lines.firstOrNull()?.trimEnd() == "---"
        val end = if (hasFrontmatter) {
            (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..." }
        } else {
            null
        }
        if (end == null) {
            return bom + listOf("---", line, "---").joinToString(newline) + newline + content.removePrefix(bom)
        }

        val keyPattern = Regex("^${Regex.escape(key)}\\s*:")
        val at = (1 until end).firstOrNull { keyPattern.containsMatchIn(lines[it]) }
        if (at == null) {
            lines.add(end, line)
        } else {
            var stop = at + 1
            while (stop < end && (lines[stop].startsWith(" ") || lines[stop].startsWith("\t"))) stop++
            repeat(stop - at - 1) { lines.removeAt(at + 1) }
            lines[at] = line
        }
        return bom + lines.joinToString(newline)
    }
}
