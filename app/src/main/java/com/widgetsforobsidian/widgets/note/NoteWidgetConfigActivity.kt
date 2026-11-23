package com.widgetsforobsidian.widgets.note

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.widgetsforobsidian.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.index.NoteEntity
import com.widgetsforobsidian.ui.AppTheme
import kotlinx.coroutines.launch

/** Picks the note a note widget shows, or "follow Obsidian". Also opened by tapping the widget title. */
class NoteWidgetConfigActivity : ComponentActivity() {

    companion object {
        fun reconfigure(context: Context, appWidgetId: Int): Intent =
            Intent(context, NoteWidgetConfigActivity::class.java)
                .setData(Uri.parse("obsidianwidgets://note-config/$appWidgetId"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private val container get() = (application as WidgetsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        fun save(path: String?, follow: Boolean) = lifecycleScope.launch {
            val previous = NoteWidgetStore.get(this@NoteWidgetConfigActivity, appWidgetId)
            NoteWidgetStore.set(this@NoteWidgetConfigActivity, appWidgetId, NoteWidgetStore.Config(path ?: previous.path, follow))
            NoteWidgetProvider.update(this@NoteWidgetConfigActivity, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
        }

        enableEdgeToEdge()
        setContent { AppTheme { PickerScreen(onPick = { save(it, false) }, onFollow = { save(null, true) }) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PickerScreen(onPick: (String) -> Unit, onFollow: () -> Unit) {
        var query by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }
        var hasVault by remember { mutableStateOf(true) }

        LaunchedEffect(query) {
            hasVault = container.settings.current().vault != null
            notes = container.database.notes().searchNotes(query.trim(), limit = 100)
        }

        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_note)) }) }) { padding ->
            Column(Modifier.padding(padding)) {
                if (!hasVault) {
                    Text(stringResource(R.string.no_vault_configured), Modifier.padding(16.dp))
                    return@Column
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.follow_obsidian)) },
                    supportingContent = { Text(stringResource(R.string.follow_obsidian_hint)) },
                    modifier = Modifier.clickable(onClick = onFollow),
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                LazyColumn {
                    items(notes, key = { it.path }) { note ->
                        ListItem(
                            headlineContent = { Text(note.name) },
                            supportingContent = {
                                Text(note.folder.ifEmpty { "/" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.clickable { onPick(note.path) },
                        )
                    }
                }
            }
        }
    }
}
