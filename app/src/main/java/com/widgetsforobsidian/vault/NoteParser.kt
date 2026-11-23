package com.widgetsforobsidian.vault

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

data class ParsedNote(
    /** Frontmatter properties, converted to JSON so they can be stored and queried later by Bases. */
    val properties: JsonObject,
    /** Markdown after the frontmatter block. */
    val body: String,
    /** Tags from the `tags` property and inline `#tags`, without `#`, de-duplicated. */
    val tags: List<String>,
    /** Targets of `[[wikilinks]]` and `![[embeds]]`, without heading/block/alias parts. */
    val links: List<String>,
)

/** Pure-Kotlin parser for the parts of an Obsidian note the index needs. */
object NoteParser {

    private val INLINE_TAG = Regex("""(?<=^|\s)#([\p{L}\p{N}_/\-]+)""")
    private val WIKILINK = Regex("""!?\[\[([^\]|#^]+)[^\]]*]]""")
    private val INLINE_CODE = Regex("`[^`\n]*`")
    private val FENCE = Regex("""^\s*(```|~~~)""")

    fun parse(content: String): ParsedNote {
        val text = content.removePrefix("﻿").replace("\r\n", "\n")
        val (yaml, body) = splitFrontmatter(text)
        val properties = yaml?.let(::parseYaml) ?: JsonObject(emptyMap())

        val prose = stripCode(body)
        val tags = LinkedHashSet<String>()
        propertyTags(properties).forEach(tags::add)
        INLINE_TAG.findAll(prose)
            .map { it.groupValues[1].trimEnd('/') }
            .filter { tag -> tag.any { !it.isDigit() } } // "#123" is not a tag in Obsidian
            .forEach(tags::add)

        // Links in properties (e.g. `project: "[[siryus]]"`) count too, as in Obsidian's file.hasLink().
        val linkSources = sequenceOf(prose) + propertyStrings(properties)
        val links = linkSources.flatMap { WIKILINK.findAll(it) }.map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }.distinct().toList()

        return ParsedNote(properties, body, tags.toList(), links)
    }

    /** Returns the YAML text (or null if there is no frontmatter) and the remaining body. */
    fun splitFrontmatter(text: String): Pair<String?, String> {
        if (!text.startsWith("---\n") && text != "---") return null to text
        val lines = text.split('\n')
        val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..." }
            ?: return null to text
        val yaml = lines.subList(1, end).joinToString("\n")
        val body = lines.subList(end + 1, lines.size).joinToString("\n")
        return yaml to body
    }

    private fun parseYaml(yaml: String): JsonObject? = try {
        val loaded = Load(LoadSettings.builder().build()).loadFromString(yaml)
        toJson(loaded) as? JsonObject
    } catch (e: Exception) {
        null // Invalid YAML: Obsidian also just shows no properties.
    }

    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        is Array<*> -> JsonArray(value.map(::toJson))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun propertyTags(properties: JsonObject): List<String> {
        val raw = properties["tags"] ?: properties["tag"] ?: return emptyList()
        val values = when (raw) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> raw.content.split(',', ' ')
            else -> emptyList()
        }
        return values.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
    }

    private fun propertyStrings(element: JsonElement): Sequence<String> = when (element) {
        is JsonObject -> element.values.asSequence().flatMap(::propertyStrings)
        is JsonArray -> element.asSequence().flatMap(::propertyStrings)
        is JsonPrimitive -> if (element.isString) sequenceOf(element.content) else emptySequence()
        else -> emptySequence()
    }

    /** Removes fenced code blocks and inline code so tags/links inside them are ignored. */
    private fun stripCode(body: String): String {
        val out = StringBuilder()
        var inFence = false
        for (line in body.split('\n')) {
            if (FENCE.containsMatchIn(line)) {
                inFence = !inFence
                continue
            }
            if (!inFence) out.append(INLINE_CODE.replace(line, " ")).append('\n')
        }
        return out.toString()
    }
}
