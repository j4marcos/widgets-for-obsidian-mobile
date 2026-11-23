package com.widgetsforobsidian.widgets.tasks

import android.appwidget.AppWidgetManager
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.widgetsforobsidian.WidgetsApp
import com.widgetsforobsidian.bases.BaseView
import com.widgetsforobsidian.index.NoteEntity
import com.widgetsforobsidian.ui.AppTheme
import com.widgetsforobsidian.ui.MainActivity
import kotlinx.coroutines.launch

/** Shown when a tasks widget is added (or reconfigured): pick a `.base` file and one of its views. */
class TasksWidgetConfigActivity : ComponentActivity() {

    private val container get() = (application as WidgetsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultIntent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                ConfigScreen(
                    onSave = { base, view ->
                        lifecycleScope.launch {
                            val glanceId = GlanceAppWidgetManager(this@TasksWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(this@TasksWidgetConfigActivity, glanceId) {
                                it[TasksWidgetKeys.BASE_PATH] = base.path
                                it[TasksWidgetKeys.VIEW_INDEX] = view.index
                                it[TasksWidgetKeys.REFRESH] = System.nanoTime()
                            }
                            TasksWidget().update(this@TasksWidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ConfigScreen(onSave: (NoteEntity, BaseView) -> Unit) {
        var bases by remember { mutableStateOf<List<NoteEntity>?>(null) }
        var hasVault by remember { mutableStateOf(true) }
        var selected by remember { mutableStateOf<NoteEntity?>(null) }
        var views by remember { mutableStateOf<List<BaseView>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            hasVault = container.settings.current().vault != null
            bases = container.database.notes().bases()
        }

        Scaffold(topBar = { TopAppBar(title = { Text(if (selected == null) "Escolha a base" else "Escolha a view") }) }) { padding ->
            Column(Modifier.padding(padding)) {
                when {
                    !hasVault -> NoVault()
                    bases == null -> Text("Carregando…", Modifier.padding(16.dp))
                    bases!!.isEmpty() -> Text(
                        "Nenhum arquivo .base indexado. Abra o app e toque em \"Reindexar agora\".",
                        Modifier.padding(16.dp),
                    )
                    selected == null -> LazyColumn {
                        items(bases!!, key = { it.path }) { base ->
                            ListItem(
                                headlineContent = { Text(TaskFormat.baseTitle(base.path) + " · " + base.name) },
                                supportingContent = { Text(base.path) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        error = null
                                        runCatching { container.bases.load(base.path) }
                                            .onSuccess { def ->
                                                views = def.views.filter { it.isListLike }
                                                selected = base
                                            }
                                            .onFailure { error = "Não consegui ler a base: ${it.message}" }
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                    views.isEmpty() -> Text("Esta base não tem views de lista, tabela ou board.", Modifier.padding(16.dp))
                    else -> LazyColumn {
                        items(views, key = { it.index }) { view ->
                            ListItem(
                                headlineContent = { Text(view.name) },
                                supportingContent = {
                                    Text(listOfNotNull(view.type, view.groupBy?.let { "agrupado por $it" }).joinToString(" · "))
                                },
                                modifier = Modifier.clickable { onSave(selected!!, view) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    @Composable
    private fun NoVault() {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Nenhum vault configurado. Abra o app e escolha a pasta do vault primeiro.")
            Button(onClick = { startActivity(Intent(this@TasksWidgetConfigActivity, MainActivity::class.java)) }) {
                Text("Abrir o app")
            }
        }
    }
}
