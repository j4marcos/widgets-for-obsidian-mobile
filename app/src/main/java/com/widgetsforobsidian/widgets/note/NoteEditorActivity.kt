package com.widgetsforobsidian.widgets.note

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.ui.AppTheme
import com.widgetsforobsidian.ui.PopupSurface
import com.widgetsforobsidian.vault.VaultPaths
import com.widgetsforobsidian.widgets.WidgetUpdater
import com.widgetsforobsidian.widgets.tasks.openInObsidian
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Floating markdown editor opened from the note widget. Saves when closed; if the note changed
 * on disk since it was opened (Obsidian, sync), it asks instead of overwriting.
 */
class NoteEditorActivity : ComponentActivity() {

    private class ConflictException : RuntimeException()

    companion object {
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, path: String): Intent =
            Intent(context, NoteEditorActivity::class.java)
                .setData(Uri.parse("obsidianwidgets://edit/" + Uri.encode(path)))
                .putExtra(EXTRA_PATH, path)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private val container get() = (application as WidgetsApp).container
    private lateinit var path: String

    private var original by mutableStateOf<String?>(null)
    private var text by mutableStateOf("")
    private var conflict by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var saving by mutableStateOf(false)
    private var closing = false

    private val dirty get() = original != null && text != original

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra(EXTRA_PATH) ?: return finish()
        enableEdgeToEdge()
        setContent { AppTheme { EditorScreen() } }
    }

    private suspend fun readCurrent(): String = withContext(Dispatchers.IO) {
        val vault = container.settings.current().vault ?: error("Nenhum vault configurado")
        val entity = container.database.notes().get(path) ?: error("Nota não encontrada: $path")
        container.vault.readText(vault.treeUri, entity.documentId)
    }

    /** Writes [text] unless the file changed since it was loaded (or [force] is set). */
    private suspend fun save(force: Boolean): Result<Unit> {
        val base = original ?: return Result.success(Unit)
        val newText = text
        return runCatching {
            container.indexer.editNote(path) { current ->
                if (!force && current != base) throw ConflictException()
                newText
            }
            original = newText
            WidgetUpdater.refreshAll(applicationContext)
        }
    }

    private fun saveAndClose(force: Boolean = false) {
        if (saving) return
        if (!dirty) return finish()
        saving = true
        lifecycleScope.launch {
            save(force)
                .onSuccess { closing = true; finish() }
                .onFailure {
                    saving = false
                    if (it is ConflictException) conflict = true else error = "Não consegui salvar: ${it.message}"
                }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving without tapping Save (home button, another app) still keeps the edit,
        // unless that would overwrite a newer version of the note.
        if (!closing && !isChangingConfigurations && dirty && !conflict) {
            container.appScope.launch {
                save(force = false).onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Nota não salva: ela mudou fora do editor", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    @Composable
    private fun EditorScreen() {
        LaunchedEffect(path) {
            runCatching { readCurrent() }
                .onSuccess { original = it; text = it }
                .onFailure { error = it.message }
        }

        PopupSurface(onDismiss = { saveAndClose() }, alignment = Alignment.Center, fill = true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(VaultPaths.basename(path), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    lifecycleScope.launch {
                        if (dirty) save(force = false)
                        val vault = container.settings.current().vault ?: return@launch
                        closing = true
                        startActivity(openInObsidian(vault.name, path))
                        finish()
                    }
                }) { Text("Obsidian") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = original != null,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when { original == null -> "Carregando…"; dirty -> "Alterações não salvas"; else -> "Salvo" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { closing = true; finish() }) { Text(if (dirty) "Descartar" else "Fechar") }
                Button(onClick = { saveAndClose() }, enabled = dirty && !saving) { Text("Salvar") }
            }
        }

        if (conflict) {
            AlertDialog(
                onDismissRequest = { conflict = false },
                title = { Text("A nota mudou") },
                text = { Text("Ela foi alterada fora do editor (Obsidian ou sincronização) depois que você abriu. Sobrescrever com o seu texto ou recarregar a versão atual?") },
                confirmButton = { TextButton(onClick = { conflict = false; saveAndClose(force = true) }) { Text("Sobrescrever") } },
                dismissButton = {
                    TextButton(onClick = {
                        conflict = false
                        lifecycleScope.launch {
                            runCatching { readCurrent() }.onSuccess { original = it; text = it }
                        }
                    }) { Text("Recarregar") }
                },
            )
        }
    }
}
