package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.groups_enter_list
import com.aimdi.xtanative.ui.generated.resources.groups_finished_with
import com.aimdi.xtanative.ui.generated.resources.groups_import
import com.aimdi.xtanative.ui.generated.resources.groups_import_from
import com.aimdi.xtanative.ui.generated.resources.groups_import_handles_helper
import com.aimdi.xtanative.ui.generated.resources.groups_import_handles_hint
import com.aimdi.xtanative.ui.generated.resources.groups_import_handles_none
import com.aimdi.xtanative.ui.generated.resources.groups_import_help
import com.aimdi.xtanative.ui.generated.resources.groups_import_list
import com.aimdi.xtanative.ui.generated.resources.groups_import_list_help
import com.aimdi.xtanative.ui.generated.resources.groups_import_list_note
import com.aimdi.xtanative.ui.generated.resources.groups_import_note
import com.aimdi.xtanative.ui.generated.resources.groups_import_sort
import com.aimdi.xtanative.ui.generated.resources.groups_import_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_imported_so_far
import com.aimdi.xtanative.ui.generated.resources.groups_invalid_list
import com.aimdi.xtanative.ui.generated.resources.groups_list_not_found
import com.aimdi.xtanative.ui.generated.resources.groups_list_url_or_id
import com.aimdi.xtanative.ui.generated.resources.groups_unable_to_import
import com.aimdi.xtanative.ui.generated.resources.groups_username
import org.jetbrains.compose.resources.stringResource

/** Importing the public following of the handles pasted (_import.dart). */
@Composable
fun ImportFollowingScreen(onBack: () -> Unit, onSortUngrouped: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ImportFollowingViewModel = viewModel { ImportFollowingViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    ImportScaffold(Res.string.groups_import_subscriptions, onBack, onImport = vm::start, enabled = !state.running) {
        Text(stringResource(Res.string.groups_import_help))
        Text(stringResource(Res.string.groups_import_note))
        OutlinedTextField(
            value = state.input,
            onValueChange = vm::type,
            minLines = 3,
            maxLines = 8,
            label = { Text(stringResource(Res.string.groups_username)) },
            placeholder = { Text(stringResource(Res.string.groups_import_handles_hint)) },
            supportingText = {
                Text(stringResource(if (state.invalid) Res.string.groups_import_handles_none else Res.string.groups_import_handles_helper))
            },
            isError = state.invalid,
            modifier = Modifier.fillMaxWidth(),
        )
        Progress(state.run, onSortUngrouped)
    }
}

/** Importing an X list as a group (_import_list.dart); [initial] is a list link or id to start from. */
@Composable
fun ListImportScreen(initial: String?, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ListImportViewModel = viewModel { ListImportViewModel(graph, initial) }
    val state by vm.ui.collectAsStateWithLifecycle()
    ImportScaffold(Res.string.groups_import_list, onBack, onImport = vm::start, enabled = !state.running) {
        Text(stringResource(Res.string.groups_import_list_help))
        Text(stringResource(Res.string.groups_import_list_note))
        OutlinedTextField(
            value = state.input,
            onValueChange = vm::type,
            singleLine = true,
            label = { Text(stringResource(Res.string.groups_list_url_or_id)) },
            placeholder = { Text(stringResource(Res.string.groups_enter_list)) },
            supportingText = if (state.invalid) ({ Text(stringResource(Res.string.groups_invalid_list)) }) else null,
            isError = state.invalid,
            modifier = Modifier.fillMaxWidth(),
        )
        Progress(state.run, onSortUngrouped = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportScaffold(
    title: org.jetbrains.compose.resources.StringResource,
    onBack: () -> Unit,
    onImport: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (enabled) onImport() }) {
                Icon(GroupIcons.CloudDownload, stringResource(Res.string.groups_import))
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** How far an import is, what it came to, or why it failed. */
@Composable
private fun Progress(run: ImportRun, onSortUngrouped: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (run) {
            ImportRun.Idle -> Unit
            is ImportRun.Reading -> {
                CircularProgressIndicator(Modifier.padding(16.dp))
                run.handle?.let { Text(stringResource(Res.string.groups_import_from, it)) }
                Text(stringResource(Res.string.groups_imported_so_far, run.count.toString()))
            }
            is ImportRun.Done -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF00BA7C), modifier = Modifier.padding(16.dp).size(36.dp))
                Text(stringResource(Res.string.groups_finished_with, run.count.toString()))
                if (onSortUngrouped != null) FilledTonalButton(onClick = onSortUngrouped) { Text(stringResource(Res.string.groups_import_sort)) }
            }
            is ImportRun.Failed -> {
                Text(stringResource(Res.string.groups_unable_to_import), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (run.notFound) stringResource(Res.string.groups_list_not_found) else problemText(run.problem),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
