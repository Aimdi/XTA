package com.aimdi.xtanative.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.store.saved.ArchiveNotesStore
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.downloads.rememberFilePicker
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.archive_extract
import com.aimdi.xtanative.ui.generated.resources.archive_extract_notice
import com.aimdi.xtanative.ui.generated.resources.archive_highlight
import com.aimdi.xtanative.ui.generated.resources.archive_tags
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_ok
import com.aimdi.xtanative.ui.saved.NoteEditorDialog
import com.aimdi.xtanative.ui.saved.NoteTarget
import com.aimdi.xtanative.ui.saved.SavedIcons
import org.jetbrains.compose.resources.stringResource

/**
 * An article kept offline, annotated (archive_document_screen.dart): its
 * tags, the AI transcription of a screenshot when an AI is set, the passages
 * highlighted with their notes, and the text, in which a selected passage is
 * copied, or highlighted with a note, from the selection's menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArchiveScreen(id: String, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ArchiveViewModel = viewModel(key = "archive-$id") { ArchiveViewModel(graph, id) }
    val state by vm.ui.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    var highlighting by remember { mutableStateOf<String?>(null) }
    var selection by remember(state.text) { mutableStateOf(TextFieldValue(state.text)) }
    val pick = rememberFilePicker(images = true, title = stringResource(Res.string.archive_extract))
    val passage = ArchiveRules.passage(selection.text, selection.selection.start, selection.selection.end)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(Res.string.archive_tags), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tags.forEach { tag ->
                    FilterChip(selected = tag in state.annotation.tags, onClick = { vm.toggleTag(tag) }, label = { Text(tag) })
                }
            }
            if (state.ai != null) {
                TextButton(onClick = { confirming = true }, enabled = !state.extracting) {
                    if (state.extracting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(SavedIcons.Scan, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.archive_extract))
                }
            }
            state.annotation.highlights.forEach { (quote, note) ->
                Card(onClick = { highlighting = quote }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(quote) },
                        supportingContent = { Text(note) },
                        trailingContent = {
                            IconButton(onClick = { vm.removeHighlight(quote) }) { Icon(Icons.Filled.Close, stringResource(Res.string.saved_delete)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            // Read-only, so its text can be selected but not changed: the selection's menu copies it, or highlights it with a note.
            HighlightMenu(stringResource(Res.string.archive_highlight), passage?.let { { highlighting = it } }) {
                BasicTextField(
                    value = selection,
                    onValueChange = { selection = it.copy(text = state.text, selection = clamp(it.selection, state.text.length)) },
                    readOnly = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }

    if (confirming) {
        val ai = state.ai
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.archive_extract)) },
            text = { Text(stringResource(Res.string.archive_extract_notice, ai?.host.orEmpty())) },
            confirmButton = { Button(onClick = { confirming = false; vm.extract(pick) }) { Text(stringResource(Res.string.saved_ok)) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    highlighting?.let { quote ->
        NoteEditorDialog(
            target = NoteTarget.Text(state.annotation.highlights[quote], ArchiveNotesStore.draftKey(id, quote), allowUnchanged = true),
            onDismiss = { highlighting = null },
            saveText = { note -> vm.highlight(quote, note) },
        )
    }
}

private fun clamp(range: TextRange, length: Int): TextRange = TextRange(range.start.coerceIn(0, length), range.end.coerceIn(0, length))
