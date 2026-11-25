package com.aimdi.xtanative.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.saved.ArchiveNotesStore
import com.aimdi.xtanative.ui.common.ColumnTopAppBar
import com.aimdi.xtanative.ui.common.FindBlock
import com.aimdi.xtanative.ui.common.FlatFilterChip
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.ReadingPage
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.foundIn
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
import com.aimdi.xtanative.ui.home.FindInPage
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

    // Ctrl+F finds in the archived page's text (the desktop's find bar).
    val findBlocks = remember(state.text) { listOf(FindBlock(TEXT_KEY, state.text)) }
    FindInPage(findBlocks) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ColumnTopAppBar(
                title = { TipText(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconTip(stringResource(Res.string.back)) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        ReadingPage(padding, PaddingValues(16.dp)) {
            Text(stringResource(Res.string.archive_tags), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tags.forEach { tag ->
                    FlatFilterChip(selected = tag in state.annotation.tags, onClick = { vm.toggleTag(tag) }, label = { Text(tag) })
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
                ListItem(
                    headlineContent = { Text(quote) },
                    supportingContent = { Text(note) },
                    trailingContent = {
                        IconTip(stringResource(Res.string.saved_delete)) { IconButton(onClick = { vm.removeHighlight(quote) }) { Icon(Icons.Filled.Close, stringResource(Res.string.saved_delete)) } }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().clickable { highlighting = quote },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.size(12.dp))
            // Read-only, so its text can be selected but not changed: the selection's menu copies it, or highlights it with a note.
            HighlightMenu(stringResource(Res.string.archive_highlight), passage?.let { { highlighting = it } }) {
                val found = foundIn(TEXT_KEY, remember(state.text) { AnnotatedString(state.text) })
                BasicTextField(
                    value = selection,
                    onValueChange = { selection = it.copy(text = state.text, selection = clamp(it.selection, state.text.length)) },
                    readOnly = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
                    // The find bar's matches marked on the text, every character where it was.
                    visualTransformation = { TransformedText(found.text, OffsetMapping.Identity) },
                    onTextLayout = found.laidOut,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).then(found.modifier),
                )
            }
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

/** The find bar's key of the archived page's text. */
private const val TEXT_KEY = "archive-text"

private fun clamp(range: TextRange, length: Int): TextRange = TextRange(range.start.coerceIn(0, length), range.end.coerceIn(0, length))
