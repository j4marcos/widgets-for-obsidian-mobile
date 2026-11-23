package com.widgetsforobsidian.widgets.note

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.widgetsforobsidian.AppContainer
import com.widgetsforobsidian.R
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.markdown.Block
import com.widgetsforobsidian.markdown.Markdown
import com.widgetsforobsidian.markdown.WorkspaceReader
import com.widgetsforobsidian.settings.ObsidianTheme
import com.widgetsforobsidian.widgets.WidgetPalette
import com.widgetsforobsidian.vault.VaultPaths
import com.widgetsforobsidian.widgets.tasks.openInObsidian
import kotlinx.coroutines.launch

/**
 * The note widget is plain RemoteViews rather than Glance: its body is a ListView of styled
 * TextViews, and launchers only accept collections at the top level of an app widget, which
 * Glance's AndroidRemoteViews nesting cannot provide.
 */
class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAsync(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            updateAsync(context, allIds(context))
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { NoteWidgetStore.remove(context, it) }
    }

    private fun updateAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as WidgetsApp).container
        container.appScope.launch {
            try {
                ids.forEach { update(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.widgetsforobsidian.NOTE_REFRESH"
        private const val MAX_BLOCKS = 200

        fun allIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))

        suspend fun updateAll(context: Context) = allIds(context).forEach { update(context, it) }

        suspend fun update(context: Context, appWidgetId: Int) {
            val container = (context.applicationContext as WidgetsApp).container
            val config = NoteWidgetStore.get(context, appWidgetId)
            val state = load(context, container, config.path, config.follow)
            val theme = container.theme.current()
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, render(context, appWidgetId, config, state, theme))
        }

        private fun render(
            context: Context,
            appWidgetId: Int,
            config: NoteWidgetStore.Config,
            state: NoteState,
            theme: ObsidianTheme,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.note_widget)
            // Fixed light/dark themes override the day/night resources used for "system".
            val textColor = when (theme.mode) {
                ObsidianTheme.Mode.LIGHT -> WidgetPalette.LIGHT_TEXT.toInt()
                ObsidianTheme.Mode.DARK -> WidgetPalette.DARK_TEXT.toInt()
                ObsidianTheme.Mode.SYSTEM -> null
            }
            when (theme.mode) {
                ObsidianTheme.Mode.LIGHT -> views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_light)
                ObsidianTheme.Mode.DARK -> views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_dark)
                ObsidianTheme.Mode.SYSTEM -> Unit
            }
            textColor?.let { views.setTextColor(R.id.title, it) }
            val ready = state as? NoteState.Ready
            views.setTextViewText(R.id.title, ready?.path?.let(VaultPaths::basename) ?: context.getString(R.string.note_widget_name))
            views.setTextViewText(
                R.id.subtitle,
                (if (config.follow) context.getString(R.string.following_obsidian) else ready?.path?.let(VaultPaths::folder)?.ifEmpty { "/" } ?: "") + "  ▾",
            )
            views.setOnClickPendingIntent(R.id.header_title, activity(context, appWidgetId, 0, NoteWidgetConfigActivity.reconfigure(context, appWidgetId)))
            views.setOnClickPendingIntent(
                R.id.btn_refresh,
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, NoteWidgetProvider::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            views.setViewVisibility(R.id.btn_open, if (ready != null) View.VISIBLE else View.GONE)
            if (ready != null) {
                views.setOnClickPendingIntent(R.id.btn_open, activity(context, appWidgetId, 2, openInObsidian(ready.vaultName, ready.path)))
            }

            val message = when (state) {
                NoteState.Loading -> context.getString(R.string.loading)
                is NoteState.Error -> state.message
                is NoteState.Ready -> if (state.blocks.isEmpty()) context.getString(R.string.note_empty) else null
            }
            views.setTextViewText(R.id.message, message.orEmpty())
            views.setViewVisibility(R.id.message, if (message != null) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.note_list, if (message == null) View.VISIBLE else View.GONE)
            if (ready != null && message == null) {
                NoteList.attach(
                    context, views, appWidgetId, ready.path, ready.blocks, ready.truncated,
                    NoteList.Colors(textColor, theme.accentOrDefault),
                )
            }
            return views
        }

        /** Request codes include the widget id so each widget keeps its own PendingIntents. */
        private fun activity(context: Context, appWidgetId: Int, slot: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context,
                appWidgetId * 10 + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        suspend fun load(context: Context, container: AppContainer, fixedPath: String?, follow: Boolean): NoteState {
            val vault = container.settings.current().vault
                ?: return NoteState.Error(context.getString(R.string.open_app_pick_vault))
            return try {
                val path = (if (follow) currentObsidianNote(container, vault.treeUri) ?: fixedPath else fixedPath)
                    ?: return NoteState.Error(context.getString(if (follow) R.string.no_note_open else R.string.tap_title_to_pick))
                val entity = container.database.notes().get(path)
                    ?: return NoteState.Error(context.getString(R.string.note_not_found, path))
                val blocks = Markdown.parseNote(container.vault.readText(vault.treeUri, entity.documentId))
                NoteState.Ready(path, blocks.take(MAX_BLOCKS), vault.name, blocks.size > MAX_BLOCKS)
            } catch (e: Exception) {
                NoteState.Error(e.message ?: e.toString())
            }
        }

        private fun currentObsidianNote(container: AppContainer, tree: Uri): String? {
            for (file in listOf(".obsidian/workspace-mobile.json", ".obsidian/workspace.json")) {
                val id = container.vault.findDocumentId(tree, file) ?: continue
                WorkspaceReader.currentNote(container.vault.readText(tree, id))?.let { return it }
            }
            return null
        }
    }
}

sealed interface NoteState {
    data object Loading : NoteState
    data class Error(val message: String) : NoteState
    data class Ready(val path: String, val blocks: List<Block>, val vaultName: String, val truncated: Boolean) : NoteState
}

/** Per-widget settings: which note to show, or follow the note open in Obsidian. */
object NoteWidgetStore {
    data class Config(val path: String?, val follow: Boolean)

    private fun prefs(context: Context) = context.getSharedPreferences("note_widgets", Context.MODE_PRIVATE)

    fun get(context: Context, id: Int) =
        prefs(context).let { Config(it.getString("path_$id", null), it.getBoolean("follow_$id", false)) }

    fun set(context: Context, id: Int, config: Config) = prefs(context).edit()
        .putString("path_$id", config.path).putBoolean("follow_$id", config.follow).apply()

    fun remove(context: Context, id: Int) = prefs(context).edit().remove("path_$id").remove("follow_$id").apply()
}
