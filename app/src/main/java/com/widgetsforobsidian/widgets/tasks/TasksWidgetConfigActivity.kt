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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import com.widgetsforobsidian.R
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
                    onSave = { base, view, options ->
                        lifecycleScope.launch {
                            val glanceId = GlanceAppWidgetManager(this@TasksWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(this@TasksWidgetConfigActivity, glanceId) {
                                it[TasksWidgetKeys.BASE_PATH] = base.path
                                it[TasksWidgetKeys.VIEW_INDEX] = view.index
                                it[TasksWidgetKeys.LAYOUT] = options.layout
                                it[TasksWidgetKeys.GROUPING] = if (options.groupByProject) "project" else "view"
                                it[TasksWidgetKeys.HIDE_DONE] = options.hideDone
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
    private fun ConfigScreen(onSave: (NoteEntity, BaseView, WidgetOptions) -> Unit) {
        var bases by remember { mutableStateOf<List<NoteEntity>?>(null) }
        var hasVault by remember { mutableStateOf(true) }
        var selected by remember { mutableStateOf<NoteEntity?>(null) }
        var views by remember { mutableStateOf<List<BaseView>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var chosenView by remember { mutableStateOf<BaseView?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            hasVault = container.settings.current().vault != null
            bases = container.database.notes().bases()
        }

        Scaffold(topBar = { TopAppBar(
                title = {
                    Text(
                        when {
                            selected == null -> stringResource(R.string.pick_base)
                            chosenView == null -> stringResource(R.string.pick_view)
                            else -> stringResource(R.string.options)
                        },
                    )
                },
            ) }) { padding ->
            Column(Modifier.padding(padding)) {
                when {
                    !hasVault -> NoVault()
                    bases == null -> Text(stringResource(R.string.loading), Modifier.padding(16.dp))
                    bases!!.isEmpty() -> Text(
                        stringResource(R.string.no_bases),
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
                                            .onFailure { error = getString(R.string.base_read_error, it.message) }
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                    views.isEmpty() -> Text(stringResource(R.string.no_list_views), Modifier.padding(16.dp))
                    chosenView != null -> OptionsStep(chosenView!!) { onSave(selected!!, chosenView!!, it) }
                    else -> LazyColumn {
                        items(views, key = { it.index }) { view ->
                            ListItem(
                                headlineContent = { Text(view.name) },
                                supportingContent = {
                                    Text(listOfNotNull(view.type, view.groupBy?.let { getString(R.string.grouped_by, it) }).joinToString(" · "))
                                },
                                modifier = Modifier.clickable { chosenView = view },
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
            Text(stringResource(R.string.no_vault_configured))
            Button(onClick = { startActivity(Intent(this@TasksWidgetConfigActivity, MainActivity::class.java)) }) {
                Text(stringResource(R.string.open_app))
            }
        }
    }

    @Composable
    private fun OptionsStep(view: BaseView, onDone: (WidgetOptions) -> Unit) {
        var layout by remember { mutableStateOf("auto") }
        var groupByProject by remember { mutableStateOf(true) }
        var hideDone by remember { mutableStateOf(true) }
        LazyColumn {
            item {
                SwitchRow(stringResource(R.string.group_by_project), stringResource(R.string.group_by_project_hint), groupByProject) { groupByProject = it }
                SwitchRow(stringResource(R.string.hide_done), stringResource(R.string.hide_done_hint), hideDone) { hideDone = it }
                HorizontalDivider()
                Text(stringResource(R.string.layout), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp))
            }
            val layoutName = mapOf(
                TaskLayout.LIST to getString(R.string.layout_list),
                TaskLayout.TABLE to getString(R.string.layout_table),
                TaskLayout.CARDS to getString(R.string.layout_cards),
            )
            val layouts = listOf(
                "auto" to getString(R.string.layout_auto, layoutName.getValue(TaskLayout.resolve(null, view.type)).lowercase()),
                "list" to layoutName.getValue(TaskLayout.LIST),
                "table" to layoutName.getValue(TaskLayout.TABLE),
                "cards" to layoutName.getValue(TaskLayout.CARDS),
            )
            items(layouts, key = { it.first }) { (value, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(selected = layout == value, onClick = { layout = value }) },
                    modifier = Modifier.clickable { layout = value },
                )
            }
            item {
                Button(
                    onClick = { onDone(WidgetOptions(layout, groupByProject, hideDone)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.add_widget)) }
            }
        }
    }

    @Composable
    private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
            modifier = Modifier.clickable { onChange(!checked) },
        )
    }
}

data class WidgetOptions(val layout: String, val groupByProject: Boolean, val hideDone: Boolean)
