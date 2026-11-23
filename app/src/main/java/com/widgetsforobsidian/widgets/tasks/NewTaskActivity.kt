package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.bases.NoteRow
import com.widgetsforobsidian.ui.AppTheme
import com.widgetsforobsidian.ui.PopupSurface
import com.widgetsforobsidian.widgets.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Popup opened by the widget's + button: creates a task note in the base's folder without opening Obsidian. */
class NewTaskActivity : ComponentActivity() {

    private val container get() = (application as WidgetsApp).container

    companion object {
        private const val EXTRA_BASE = "base_path"

        /** The data URI keeps PendingIntents of different widgets apart (extras are ignored when matching). */
        fun intent(context: Context, basePath: String): Intent =
            Intent(context, NewTaskActivity::class.java)
                .setData(Uri.parse("obsidianwidgets://new-task/" + Uri.encode(basePath)))
                .putExtra(EXTRA_BASE, basePath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val basePath = intent.getStringExtra(EXTRA_BASE) ?: return finish()
        enableEdgeToEdge()
        setContent { AppTheme { NewTaskScreen(basePath) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NewTaskScreen(basePath: String) {
        var members by remember { mutableStateOf<Pair<String, List<NoteRow>>?>(null) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var title by remember { mutableStateOf("") }
        var project by remember { mutableStateOf<String?>(null) }
        var priority by remember { mutableStateOf("medium") }
        var due by remember { mutableStateOf<LocalDate?>(null) }
        var pickingDate by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }

        LaunchedEffect(basePath) {
            runCatching { container.bases.members(basePath) }
                .onSuccess {
                    members = it
                    project = TaskTemplate.defaults(it.second).projects.firstOrNull()
                }
                .onFailure { loadError = it.message }
            focus.requestFocus()
        }

        val defaults = members?.second?.let(TaskTemplate::defaults)
        val nextId = members?.let { TaskTemplate.nextId(project, it.second) }

        fun save() {
            val (folder, rows) = members ?: return
            if (title.isBlank() || saving) return
            saving = true
            scope.launch {
                val task = NewTask(title, project, priority, due)
                val result = runCatching {
                    // Retry with the next number if the name was taken meanwhile (e.g. by a sync).
                    var id = TaskTemplate.nextId(project, rows)
                    var lastError: Throwable? = null
                    repeat(5) {
                        val text = TaskTemplate.render(id, task, defaults?.owner, TaskTemplate.commonTags(project, rows), LocalDate.now())
                        val created = runCatching { container.indexer.createNote(folder, "$id.md", text) }
                        if (created.isSuccess) return@runCatching id
                        lastError = created.exceptionOrNull()
                        id = TaskTemplate.bump(id)
                    }
                    throw lastError ?: IllegalStateException("falha ao criar")
                }
                result.onSuccess { id ->
                    WidgetUpdater.refreshAll(applicationContext)
                    Toast.makeText(applicationContext, "$id criada", Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure {
                    saving = false
                    loadError = "Não consegui criar a tarefa: ${it.message}"
                }
            }
        }

        PopupSurface(onDismiss = ::finish) {
            Text(
                "Nova tarefa" + (nextId?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(TaskFormat.baseTitle(basePath), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            defaults?.projects?.takeIf { it.isNotEmpty() }?.let { projects ->
                Spacer(Modifier.height(12.dp))
                Text("Projeto", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    projects.forEach { p ->
                        FilterChip(selected = p == project, onClick = { project = p }, label = { Text(TaskFormat.linkLabel(p)) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Prioridade", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("high" to "Alta", "medium" to "Média", "low" to "Baixa").forEach { (value, label) ->
                    FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = due != null,
                    onClick = { pickingDate = true },
                    label = { Text(due?.let { "Prazo: ${TaskFormat.dueLabel(it, LocalDate.now())}" } ?: "Prazo") },
                )
                if (due != null) TextButton(onClick = { due = null }) { Text("Sem prazo") }
            }
            loadError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = ::finish) { Text("Cancelar") }
                Button(onClick = ::save, enabled = title.isNotBlank() && members != null && !saving) { Text("Criar") }
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
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancelar") } },
            ) { DatePicker(state) }
        }
    }
}
