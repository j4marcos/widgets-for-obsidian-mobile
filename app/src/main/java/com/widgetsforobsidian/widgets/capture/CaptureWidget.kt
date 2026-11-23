package com.widgetsforobsidian.widgets.capture

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.widgetsforobsidian.R
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.settings.ObsidianTheme
import com.widgetsforobsidian.widgets.LocalPalette
import com.widgetsforobsidian.widgets.WidgetPalette
import com.widgetsforobsidian.widgets.tasks.QuickAddActivity

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CaptureWidget()
}

/** One-tap capture button: opens the quick-add card to write a note into `inbox/`. */
class CaptureWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as WidgetsApp).container
        provideContent {
            val theme by produceState(ObsidianTheme()) { value = container.theme.current() }
            CompositionLocalProvider(LocalPalette provides WidgetPalette(theme)) {
                val p = LocalPalette.current
                val open = actionStartActivity(QuickAddActivity.forFolder(LocalContext.current))
                val wide = LocalSize.current.width >= 110.dp
                Row(
                    GlanceModifier.fillMaxSize().background(p.background).cornerRadius(20.dp).padding(12.dp).clickable(open),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    val label = LocalContext.current.getString(R.string.capture_widget_name)
                    Image(ImageProvider(R.drawable.ic_add), label, GlanceModifier.size(26.dp), colorFilter = ColorFilter.tint(p.accent))
                    if (wide) {
                        Spacer(GlanceModifier.width(8.dp))
                        Column {
                            Text(label, style = TextStyle(color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text("/${QuickNote.DEFAULT_FOLDER}", style = TextStyle(color = p.muted, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
