package com.widgetsforobsidian.widgets

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.widgetsforobsidian.settings.ObsidianTheme

/**
 * Widget colors following the vault's Obsidian theme: light, dark, or the system setting
 * (day/night pairs), plus the Obsidian accent color for checkboxes, links and headers.
 */
class WidgetPalette(val theme: ObsidianTheme) {

    private fun pick(day: Long, night: Long) = when (theme.mode) {
        ObsidianTheme.Mode.LIGHT -> ColorProvider(Color(day))
        ObsidianTheme.Mode.DARK -> ColorProvider(Color(night))
        ObsidianTheme.Mode.SYSTEM -> ColorProvider(day = Color(day), night = Color(night))
    }

    val background = pick(LIGHT_BG, DARK_BG)
    val card = pick(0xFFFFFFFF, 0xFF323235)
    val text = pick(LIGHT_TEXT, DARK_TEXT)
    val muted = pick(0xFF7A7A7A, 0xFF9A9A9A)
    val accent = ColorProvider(Color(theme.accentOrDefault))
    val overdue = ColorProvider(Color(0xFFE5484D))

    fun priority(priority: String?) = ColorProvider(Color(priorityColor(priority)))

    companion object {
        const val LIGHT_BG = 0xF2F7F7F8
        const val DARK_BG = 0xE6262628
        const val LIGHT_TEXT = 0xFF1F1F1F
        const val DARK_TEXT = 0xFFEDEDED

        fun priorityColor(priority: String?): Int = when (priority) {
            "high", "urgent" -> 0xFFE5484D
            "medium" -> 0xFFF5A524
            "low" -> 0xFF3E8EF7
            else -> 0xFF8A8A8A
        }.toInt()
    }
}

val LocalPalette = staticCompositionLocalOf { WidgetPalette(ObsidianTheme()) }
