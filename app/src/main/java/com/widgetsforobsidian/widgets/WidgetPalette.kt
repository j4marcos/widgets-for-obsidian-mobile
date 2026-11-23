package com.widgetsforobsidian.widgets

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

/** Colors shared by all widgets; day/night pairs follow the system theme. */
object WidgetPalette {
    val background = ColorProvider(day = Color(0xF2F7F7F8), night = Color(0xE6262628))
    val card = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF323235))
    val text = ColorProvider(day = Color(0xFF1F1F1F), night = Color(0xFFEDEDED))
    val muted = ColorProvider(day = Color(0xFF7A7A7A), night = Color(0xFF9A9A9A))
    val accent = ColorProvider(day = Color(0xFF7C3AED), night = Color(0xFFA78BFA))
    val overdue = ColorProvider(Color(0xFFE5484D))

    fun priority(priority: String?) = ColorProvider(
        when (priority) {
            "high", "urgent" -> Color(0xFFE5484D)
            "medium" -> Color(0xFFF5A524)
            "low" -> Color(0xFF3E8EF7)
            else -> Color(0xFF8A8A8A)
        },
    )
}
