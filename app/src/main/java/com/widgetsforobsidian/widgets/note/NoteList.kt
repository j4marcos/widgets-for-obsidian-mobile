package com.widgetsforobsidian.widgets.note

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import com.widgetsforobsidian.R
import com.widgetsforobsidian.markdown.Block

/**
 * Builds the note body as a plain RemoteViews ListView.
 *
 * Styled text (bold, links, highlights…) needs TextViews with spans, which Glance cannot
 * provide in clickable list items, so every block is a RemoteViews row and taps reach
 * [NoteActionActivity] through fill-in intents.
 */
object NoteList {

    private const val MUTED = 0xFF8A8A8A.toInt()

    /** [text] is null when the text color should follow the system day/night setting. */
    data class Colors(val text: Int?, val accent: Int)

    /** Fills the `note_list` ListView of [views] with one row per block. */
    fun attach(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        path: String,
        blocks: List<Block>,
        truncated: Boolean,
        colors: Colors,
    ) {
        val template = PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, NoteActionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        views.setPendingIntentTemplate(R.id.note_list, template)

        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(4)
        val open = NoteActionActivity.openFillIn(path)
        blocks.forEachIndexed { i, block -> builder.addItem(i.toLong(), row(context, block, path, open, colors)) }
        if (truncated) {
            builder.addItem(blocks.size.toLong(), text(context, context.getString(R.string.continues_in_obsidian), 13f, open, colors).apply {
                setTextColor(R.id.note_text, MUTED)
            })
        }
        RemoteViewsCompat.setRemoteAdapter(context, views, appWidgetId, R.id.note_list, builder.build())
    }

    private fun row(context: Context, block: Block, path: String, open: Intent, colors: Colors): RemoteViews {
        val bottom = when (block) {
            is Block.ListItem, is Block.Task -> 2f
            is Block.Heading -> 4f
            else -> 8f
        }
        val views = when (block) {
            Block.Rule -> RemoteViews(context.packageName, R.layout.note_item_rule)
                .also { it.setOnClickFillInIntent(R.id.item_root, open) }
            is Block.Task -> task(context, block, path, open, colors)
            is Block.Callout, is Block.Code, is Block.Table -> boxed(context, block, open, colors)
            else -> {
                val r = NoteRenderer.render(block, colors.accent)!!
                text(context, r.text, r.sizeSp, open, colors)
            }
        }
        // Nested tasks are indented on the row, not the checkbox, whose width is fixed.
        val start = if (block is Block.Task) dp(context, block.level * 18f) else 0
        views.setViewPadding(R.id.item_root, start, 0, dp(context, 8f), dp(context, bottom))
        return views
    }

    private fun text(context: Context, text: CharSequence, size: Float, open: Intent, colors: Colors) =
        RemoteViews(context.packageName, R.layout.note_item_text).apply {
            setTextViewText(R.id.note_text, text)
            setTextViewTextSize(R.id.note_text, TypedValue.COMPLEX_UNIT_SP, size)
            colors.text?.let { setTextColor(R.id.note_text, it) }
            setOnClickFillInIntent(R.id.item_root, open)
        }

    private fun boxed(context: Context, block: Block, open: Intent, colors: Colors): RemoteViews {
        val r = NoteRenderer.render(block, colors.accent)!!
        val tint = when (block) {
            is Block.Callout -> (NoteRenderer.calloutColor(block.type) and 0x00FFFFFF) or 0x24000000
            else -> 0x1F888888
        }
        return RemoteViews(context.packageName, R.layout.note_item_box).apply {
            setTextViewText(R.id.note_text, r.text)
            setTextViewTextSize(R.id.note_text, TypedValue.COMPLEX_UNIT_SP, r.sizeSp)
            colors.text?.let { setTextColor(R.id.note_text, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setColorStateList(R.id.note_text, "setBackgroundTintList", ColorStateList.valueOf(tint))
            } else {
                setInt(R.id.note_text, "setBackgroundColor", tint)
            }
            setOnClickFillInIntent(R.id.item_root, open)
        }
    }

    private fun task(context: Context, block: Block.Task, path: String, open: Intent, colors: Colors): RemoteViews {
        val r = NoteRenderer.render(block, colors.accent)!!
        return RemoteViews(context.packageName, R.layout.note_item_task).apply {
            setTextViewText(R.id.note_text, r.text)
            when {
                block.checked -> setTextColor(R.id.note_text, MUTED)
                colors.text != null -> setTextColor(R.id.note_text, colors.text)
            }
            setImageViewResource(R.id.task_box, if (block.checked) R.drawable.ic_task_done else R.drawable.ic_task_open)
            setInt(R.id.task_box, "setColorFilter", if (block.checked) MUTED else colors.accent)
            setContentDescription(R.id.task_box, context.getString(if (block.checked) R.string.uncheck else R.string.check))
            setOnClickFillInIntent(R.id.task_box, NoteActionActivity.toggleFillIn(path, block.line, block.raw))
            setOnClickFillInIntent(R.id.note_text, open)
        }
    }

    private fun dp(context: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
}
