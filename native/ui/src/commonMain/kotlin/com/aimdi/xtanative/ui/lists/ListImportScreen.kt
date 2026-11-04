package com.aimdi.xtanative.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_import
import com.aimdi.xtanative.ui.generated.resources.discover_list_finished
import com.aimdi.xtanative.ui.generated.resources.discover_list_hint
import com.aimdi.xtanative.ui.generated.resources.discover_list_import_intro
import com.aimdi.xtanative.ui.generated.resources.discover_list_import_rate_limited
import com.aimdi.xtanative.ui.generated.resources.discover_list_import_title
import com.aimdi.xtanative.ui.generated.resources.discover_list_invalid
import com.aimdi.xtanative.ui.generated.resources.discover_list_label
import com.aimdi.xtanative.ui.generated.resources.discover_list_not_found
import com.aimdi.xtanative.ui.generated.resources.discover_list_progress
import com.aimdi.xtanative.ui.generated.resources.discover_list_unable
import com.aimdi.xtanative.ui.search.DiscoverIcons
import org.jetbrains.compose.resources.stringResource

/** XTA's success green for a finished import. */
private val Done = Color(0xFF4CAF50)

/**
 * An X list as XTA reads one: its link or id, then its members imported as
 * a group of accounts followed here, with how many so far.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListImportScreen(listId: String?, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ListImportViewModel = viewModel { ListImportViewModel(graph, listId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val title = stringResource(Res.string.discover_list_import_title)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.stage != ListImportStage.RUNNING) {
                FloatingActionButton(onClick = vm::import) {
                    Icon(DiscoverIcons.CloudDownload, contentDescription = stringResource(Res.string.discover_import))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.discover_list_import_intro))
            Text(stringResource(Res.string.discover_list_import_rate_limited), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::type,
                singleLine = true,
                enabled = state.stage != ListImportStage.RUNNING,
                label = { Text(stringResource(Res.string.discover_list_label)) },
                placeholder = { Text(stringResource(Res.string.discover_list_hint)) },
                isError = state.invalid,
                supportingText = if (state.invalid) {
                    { Text(stringResource(Res.string.discover_list_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.import() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Progress(state)
        }
    }
}

/** How far the import has come: so many members so far, all of them, or why it stopped. */
@Composable
private fun Progress(state: ListImportUi) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val count = state.imported.toString()
        when (state.stage) {
            ListImportStage.IDLE -> Unit
            ListImportStage.RUNNING -> {
                CircularProgressIndicator(Modifier.padding(top = 16.dp))
                Text(stringResource(Res.string.discover_list_progress, count), textAlign = TextAlign.Center)
            }
            ListImportStage.DONE -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Done, modifier = Modifier.padding(top = 16.dp).size(36.dp))
                Text(stringResource(Res.string.discover_list_finished, count), textAlign = TextAlign.Center)
            }
            ListImportStage.FAILED -> {
                Text(stringResource(Res.string.discover_list_unable), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                val problem = state.problem
                Text(
                    if (problem != null) problemText(problem) else stringResource(Res.string.discover_list_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
