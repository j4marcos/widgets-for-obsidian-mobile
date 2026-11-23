package com.widgetsforobsidian.widgets.note

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.markdown.MarkdownEdit
import com.widgetsforobsidian.widgets.WidgetUpdater
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for taps inside the note widget's list (fill-in intents need one
 * PendingIntent template). Toggling a checkbox runs in the background; editing opens the popup.
 */
class NoteActionActivity : Activity() {

    companion object {
        private const val ACTION = "note_action"
        private const val PATH = "path"
        private const val LINE = "line"
        private const val RAW = "raw"

        fun editFillIn(path: String) = Intent().putExtra(ACTION, "edit").putExtra(PATH, path)

        fun toggleFillIn(path: String, line: Int, raw: String) =
            Intent().putExtra(ACTION, "toggle").putExtra(PATH, path).putExtra(LINE, line).putExtra(RAW, raw)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(PATH)
        when (intent.getStringExtra(ACTION)) {
            "toggle" -> if (path != null) {
                val line = intent.getIntExtra(LINE, -1)
                val raw = intent.getStringExtra(RAW).orEmpty()
                val container = (application as WidgetsApp).container
                container.appScope.launch {
                    container.indexer.editNote(path) { MarkdownEdit.toggleTask(it, line, raw) }
                    WidgetUpdater.refreshAll(applicationContext)
                }
            }
            "edit" -> if (path != null) startActivity(NoteEditorActivity.intent(this, path))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
