package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgetsforobsidian.R
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.bases.NoteRow
import com.widgetsforobsidian.settings.ObsidianTheme
import com.widgetsforobsidian.ui.AppTheme
import com.widgetsforobsidian.widgets.WidgetPalette
import com.widgetsforobsidian.widgets.WidgetUpdater
import com.widgetsforobsidian.widgets.capture.QuickNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Quick-add card in TickTick's style, floating over the home screen:
 * one big text field and a toolbar (due date, priority, destination, voice, open in Obsidian).
 *
 * From a tasks widget it creates a task note in that base, and the destination chip picks the
 * project ("Inbox" = no project). From the capture widget it creates a plain note
 * and the chip picks the folder, `inbox/` by default.
 */
class QuickAddActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_BASE = "base_path"
        private const val EXTRA_FOLDER = "folder"

        fun forBase(context: Context, basePath: String): Intent =
            base(context, "obsidianwidgets://quick-add/base/" + Uri.encode(basePath)).putExtra(EXTRA_BASE, basePath)

        fun forFolder(context: Context, folder: String = QuickNote.DEFAULT_FOLDER): Intent =
            base(context, "obsidianwidgets://quick-add/folder/" + Uri.encode(folder)).putExtra(EXTRA_FOLDER, folder)

        /** The data URI keeps PendingIntents of different widgets apart (extras are ignored when matching). */
        private fun base(context: Context, data: String) = Intent(context, QuickAddActivity::class.java)
            .setData(Uri.parse(data))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private val container get() = (application as WidgetsApp).container

    /** One entry of the destination chip: a project of the base, or a folder. */
    private data class Destination(val label: String, val value: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val basePath = intent.getStringExtra(EXTRA_BASE)
        val folder = intent.getStringExtra(EXTRA_FOLDER) ?: QuickNote.DEFAULT_FOLDER
        enableEdgeToEdge()
        setContent { AppTheme { QuickAdd(basePath, folder) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun QuickAdd(basePath: String?, defaultFolder: String) {
        val taskMode = basePath != null
        var theme by remember { mutableStateOf(ObsidianTheme()) }
        var members by remember { mutableStateOf<Pair<String, List<NoteRow>>?>(null) }
        var destinations by remember { mutableStateOf(listOf<Destination>()) }
        var destination by remember { mutableStateOf<Destination?>(null) }
        var text by remember { mutableStateOf("") }
        var priority by remember { mutableStateOf<String?>(null) }
        var due by remember { mutableStateOf<LocalDate?>(null) }
        var pickingDate by remember { mutableStateOf(false) }
        var priorityMenu by remember { mutableStateOf(false) }
        var destinationMenu by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focus.requestFocus()
            theme = container.theme.current()
            if (basePath != null) {
                runCatching { container.bases.members(basePath) }.onSuccess { m ->
                    members = m
                    destinations = listOf(Destination(getString(R.string.inbox), null)) +
                        TaskTemplate.defaults(m.second).projects.map { Destination(TaskFormat.linkLabel(it), it) }
                    destination = destinations.first()
                }
            } else {
                val folders = runCatching { container.database.notes().folders() }.getOrDefault(emptyList())
                    .filter { it.isNotEmpty() && '/' !in it }
                destinations = (listOf(defaultFolder) + folders).distinct().map { Destination(it, it) }
                destination = destinations.first()
            }
        }

        val dark = when (theme.mode) {
            ObsidianTheme.Mode.DARK -> true
            ObsidianTheme.Mode.LIGHT -> false
            ObsidianTheme.Mode.SYSTEM -> isSystemInDarkTheme()
        }
        val surface = if (dark) Color(0xFF2B2B2E) else Color(0xFFF7F7F8)
        val onSurface = Color(if (dark) WidgetPalette.DARK_TEXT else WidgetPalette.LIGHT_TEXT)
        val muted = if (dark) Color(0xFF9A9A9A) else Color(0xFF7A7A7A)
        val accent = Color(theme.accentOrDefault)

        fun save(openAfter: Boolean) {
            val title = text.trim()
            if (title.isEmpty() || saving) return
            saving = true
            scope.launch {
                val created = runCatching {
                    if (taskMode) createTask(members ?: error("base still loading"), title, destination?.value, priority ?: "medium", due)
                    else createNote(destination?.value ?: defaultFolder, title)
                }
                created.onSuccess { path ->
                    WidgetUpdater.refreshAll(applicationContext)
                    if (openAfter) {
                        container.settings.current().vault?.let { startActivity(openInObsidian(it.name, path)) }
                    } else {
                        Toast.makeText(applicationContext, getString(R.string.created_in, path.substringBeforeLast('/')), Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }.onFailure {
                    saving = false
                    Toast.makeText(applicationContext, getString(R.string.create_failed, it.message), Toast.LENGTH_LONG).show()
                }
            }
        }

        val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                text = listOf(text.trim(), it).filter(String::isNotEmpty).joinToString(" ")
            }
        }
        val canListen = remember {
            packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
        }

        // Dimmed home screen; tapping it closes the card, as in TickTick.
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { finish() }
                .statusBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f).heightIn(min = 120.dp).padding(top = 4.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(if (taskMode) R.string.quick_add_task_hint else R.string.quick_add_note_hint),
                                style = TextStyle(color = muted, fontSize = 22.sp),
                            )
                        }
                        BasicTextField(
                            value = text,
                            // Enter submits (soft keyboards use the IME action, hardware ones send "\n").
                            onValueChange = {
                                if ('\n' in it) { text = it.replace("\n", ""); save(openAfter = false) } else text = it
                            },
                            textStyle = TextStyle(color = onSurface, fontSize = 22.sp),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { save(openAfter = false) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    ToolIcon(R.drawable.ic_expand, stringResource(R.string.create_and_open), muted, enabled = text.isNotBlank()) { save(openAfter = true) }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (taskMode) {
                        ToolIcon(R.drawable.ic_calendar, stringResource(R.string.due_date), if (due != null) accent else muted) { pickingDate = true }
                        due?.let {
                            Text(TaskFormat.dueLabel(it, LocalDate.now(), TaskLabels.from(this@QuickAddActivity)), color = accent, fontSize = 14.sp,
                                modifier = Modifier.clickable { due = null }.padding(end = 4.dp))
                        }
                        Box {
                            ToolIcon(
                                R.drawable.ic_flag, stringResource(R.string.priority),
                                priority?.let { Color(WidgetPalette.priorityColor(it)) } ?: muted,
                            ) { priorityMenu = true }
                            DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                                listOf(
                                    "high" to R.string.priority_high, "medium" to R.string.priority_medium,
                                    "low" to R.string.priority_low, null to R.string.priority_none,
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.ic_flag), null,
                                                tint = value?.let { Color(WidgetPalette.priorityColor(it)) } ?: Color.Gray)
                                        },
                                        onClick = { priority = value; priorityMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { destinationMenu = true }.padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(if (taskMode) R.drawable.ic_inbox else R.drawable.ic_folder), null,
                                tint = accent, modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                destination?.label ?: "…", color = accent, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DropdownMenu(expanded = destinationMenu, onDismissRequest = { destinationMenu = false }) {
                            destinations.forEach { d ->
                                DropdownMenuItem(text = { Text(d.label) }, onClick = { destination = d; destinationMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (canListen) {
                        ToolIcon(R.drawable.ic_mic, stringResource(R.string.dictate), accent) {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                            )
                        }
                    }
                    if (text.isNotBlank()) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(accent).clickable { save(openAfter = false) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_send), stringResource(R.string.create), tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }

        if (pickingDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = (due ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        due = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        pickingDate = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(state) }
        }
    }

    @Composable
    private fun ToolIcon(icon: Int, description: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(24.dp))
        }
    }

    /** Creates the task note; retries with the next number if the id was taken meanwhile. */
    private suspend fun createTask(
        members: Pair<String, List<NoteRow>>,
        title: String,
        project: String?,
        priority: String,
        due: LocalDate?,
    ): String {
        val (folder, rows) = members
        val task = NewTask(title, project, priority, due)
        val owner = TaskTemplate.defaults(rows).owner
        var id = TaskTemplate.nextId(project, rows)
        var lastError: Throwable? = null
        repeat(5) {
            val text = TaskTemplate.render(id, task, owner, TaskTemplate.commonTags(project, rows), LocalDate.now())
            val created = runCatching { container.indexer.createNote(folder, "$id.md", text) }
            created.onSuccess { return it.path }
            lastError = created.exceptionOrNull()
            id = TaskTemplate.bump(id)
        }
        throw lastError ?: IllegalStateException("could not create the task")
    }

    private suspend fun createNote(folder: String, title: String): String = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val vault = container.settings.current().vault ?: error("no vault configured")
        val name = QuickNote.uniqueFileName(QuickNote.baseName(title, now, getString(R.string.note_fallback_name))) { candidate ->
            container.vault.findDocumentId(vault.treeUri, "$folder/$candidate") != null
        }
        container.indexer.createNote(folder, name, QuickNote.render(now)).path
    }
}
