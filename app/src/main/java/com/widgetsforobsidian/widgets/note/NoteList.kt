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
    private const val ACCENT = NoteRenderer.ACCENT

    /** Fills the `note_list` ListView of [views] with one row per block. */
    fun attach(context: Context, views: RemoteViews, appWidgetId: Int, path: String, blocks: List<Block>, truncated: Boolean) {
        val template = PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, NoteActionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        views.setPendingIntentTemplate(R.id.note_list, template)

        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(4)
        val edit = NoteActionActivity.editFillIn(path)
        blocks.forEachIndexed { i, block -> builder.addItem(i.toLong(), row(context, block, path, edit)) }
        if (truncated) {
            builder.addItem(blocks.size.toLong(), text(context, "… continua no Obsidian", 13f, edit).apply {
                setTextColor(R.id.note_text, MUTED)
            })
        }
        RemoteViewsCompat.setRemoteAdapter(context, views, appWidgetId, R.id.note_list, builder.build())
    }

    private fun row(context: Context, block: Block, path: String, edit: Intent): RemoteViews {
        val bottom = when (block) {
            is Block.ListItem, is Block.Task -> 2f
            is Block.Heading -> 4f
            else -> 8f
        }
        val views = when (block) {
            Block.Rule -> RemoteViews(context.packageName, R.layout.note_item_rule)
                .also { it.setOnClickFillInIntent(R.id.item_root, edit) }
            is Block.Task -> task(context, block, path, edit)
            is Block.Callout, is Block.Code, is Block.Table -> boxed(context, block, edit)
            else -> {
                val r = NoteRenderer.render(block)!!
                text(context, r.text, r.sizeSp, edit)
            }
        }
        // Nested tasks are indented on the row, not the checkbox, whose width is fixed.
        val start = if (block is Block.Task) dp(context, block.level * 18f) else 0
        views.setViewPadding(R.id.item_root, start, 0, dp(context, 8f), dp(context, bottom))
        return views
    }

    private fun text(context: Context, text: CharSequence, size: Float, edit: Intent) =
        RemoteViews(context.packageName, R.layout.note_item_text).apply {
            setTextViewText(R.id.note_text, text)
            setTextViewTextSize(R.id.note_text, TypedValue.COMPLEX_UNIT_SP, size)
            setOnClickFillInIntent(R.id.item_root, edit)
        }

    private fun boxed(context: Context, block: Block, edit: Intent): RemoteViews {
        val r = NoteRenderer.render(block)!!
        val tint = when (block) {
            is Block.Callout -> (NoteRenderer.calloutColor(block.type) and 0x00FFFFFF) or 0x24000000
            else -> 0x1F888888
        }
        return RemoteViews(context.packageName, R.layout.note_item_box).apply {
            setTextViewText(R.id.note_text, r.text)
            setTextViewTextSize(R.id.note_text, TypedValue.COMPLEX_UNIT_SP, r.sizeSp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setColorStateList(R.id.note_text, "setBackgroundTintList", ColorStateList.valueOf(tint))
            } else {
                setInt(R.id.note_text, "setBackgroundColor", tint)
            }
            setOnClickFillInIntent(R.id.item_root, edit)
        }
    }

    private fun task(context: Context, block: Block.Task, path: String, edit: Intent): RemoteViews {
        val r = NoteRenderer.render(block)!!
        return RemoteViews(context.packageName, R.layout.note_item_task).apply {
            setTextViewText(R.id.note_text, r.text)
            if (block.checked) setTextColor(R.id.note_text, MUTED)
            setImageViewResource(R.id.task_box, if (block.checked) R.drawable.ic_task_done else R.drawable.ic_task_open)
            setInt(R.id.task_box, "setColorFilter", if (block.checked) MUTED else ACCENT)
            setContentDescription(R.id.task_box, if (block.checked) "Desmarcar" else "Marcar")
            setOnClickFillInIntent(R.id.task_box, NoteActionActivity.toggleFillIn(path, block.line, block.raw))
            setOnClickFillInIntent(R.id.note_text, edit)
        }
    }

    private fun dp(context: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
}
