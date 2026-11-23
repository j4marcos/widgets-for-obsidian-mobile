package com.widgetsforobsidian.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The parts of Obsidian's appearance settings the widgets mirror. */
data class ObsidianTheme(
    val mode: Mode = Mode.SYSTEM,
    /** ARGB accent color, or null for Obsidian's default purple. */
    val accent: Int? = null,
) {
    enum class Mode { LIGHT, DARK, SYSTEM }

    val accentOrDefault: Int get() = accent ?: DEFAULT_ACCENT

    companion object {
        const val DEFAULT_ACCENT = 0xFF8B6CEF.toInt()

        /** Parses `.obsidian/appearance.json`: `theme` is `obsidian` (dark), `moonstone` (light) or `system`. */
        fun parse(json: String?): ObsidianTheme {
            val root = json?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return ObsidianTheme()
            val mode = when ((root["theme"] as? JsonPrimitive)?.contentOrNull) {
                "obsidian" -> Mode.DARK
                "moonstone" -> Mode.LIGHT
                else -> Mode.SYSTEM
            }
            val accent = (root["accentColor"] as? JsonPrimitive)?.contentOrNull?.let(::parseHex)
            return ObsidianTheme(mode, accent)
        }

        fun parseHex(hex: String): Int? {
            val h = hex.trim().removePrefix("#")
            val rgb = when (h.length) {
                3 -> h.map { "$it$it" }.joinToString("")
                6 -> h
                else -> return null
            }
            return rgb.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        }
    }
}
