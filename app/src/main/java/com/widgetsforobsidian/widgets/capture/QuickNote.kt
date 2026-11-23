package com.widgetsforobsidian.widgets.capture

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Plain notes captured into a folder (the inbox by default). */
object QuickNote {

    const val DEFAULT_FOLDER = "inbox"
    private val INVALID = Regex("""[\\/:*?"<>|#^\[\]]""")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")

    /** A valid Obsidian file name from the typed title, or a timestamp when it is blank. */
    fun baseName(title: String, now: LocalDateTime, fallback: String = "Note"): String =
        INVALID.replace(title, " ").replace(Regex("""\s+"""), " ").trim().trimEnd('.').take(80)
            .ifEmpty { "$fallback ${now.format(STAMP)}" }

    /** `name.md`, or `name 1.md`, `name 2.md`… if taken. */
    fun uniqueFileName(base: String, taken: (String) -> Boolean): String {
        var name = "$base.md"
        var n = 1
        while (taken(name)) name = "$base ${n++}.md"
        return name
    }

    fun render(now: LocalDateTime): String =
        "---\ncreated: ${now.withNano(0).withSecond(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).take(16)}\n---\n"
}
