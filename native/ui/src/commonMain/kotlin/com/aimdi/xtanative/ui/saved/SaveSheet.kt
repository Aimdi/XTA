package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.saved.FolderPicker
import com.aimdi.xtanative.store.saved.QuickPick
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.settings.SavedPrefs
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.saved_matches
import com.aimdi.xtanative.ui.generated.resources.saved_recently_used
import com.aimdi.xtanative.ui.generated.resources.saved_remember_folder
import com.aimdi.xtanative.ui.generated.resources.saved_remember_folder_description
import com.aimdi.xtanative.ui.generated.resources.saved_search_or_create
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_all_folders
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_create
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_in_folder
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_no_match
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_not_saved
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_title
import com.aimdi.xtanative.ui.generated.resources.saved_sheet_unfiled
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The sheet a long press on the bookmark opens (save_sheet.dart): the
 * folders as chips that file the post with a tap (a tap on its own folder
 * takes it out, still saved), a search that can make a folder, Remember this
 * folder, and a line saying where the post is. Any network's kept post can
 * use it with its [item].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(item: Keepable, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val library by graph.saved.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) {
        graph.saved.library()
        graph.settings.load()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val loaded = library ?: return@ModalBottomSheet
        SheetBody(item, loaded, settings?.get(SavedPrefs.stickyFolderEnabled) == true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetBody(item: Keepable, library: SavedLibrary, remembering: Boolean) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    // Fixed once, so a chip never moves from under a finger.
    val openOrder = remember { SaveSheetView.openOrder(library) }
    var view by remember { mutableStateOf(SaveSheetView()) }
    val chips = view.chips(library, openOrder)
    val current = if (library.isSaved(item.id)) library.folderOf(item.id) else null
    val file = { folderId: String? -> Keeping.fileFromSheet(graph, item, folderId) }

    Column(Modifier.fillMaxWidth().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.saved_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (chips.hasMore) {
                TextButton(onClick = { view = view.showingAll() }) {
                    Text(stringResource(Res.string.saved_sheet_all_folders))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        SheetSearch(view.query, onChange = { view = view.search(it) }, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (SaveSheetView.labelShown(chips)) SectionLabel(sectionText(chips.section))
            if (chips.canCreate) {
                CreateRow(stringResource(Res.string.saved_sheet_create, view.query.trim())) {
                    val name = view.query.trim()
                    scope.launch {
                        val folder = graph.saved.createFolder(name) ?: return@launch
                        view = view.created(folder.id)
                        file(folder.id)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.items.forEach { folder ->
                    FolderChip(folder, selected = folder.id == current) { file(FolderPicker.afterTap(current, folder.id)) }
                }
            }
            Spacer(Modifier.size(8.dp))
            RememberRow(remembering) { on -> scope.launch { SavedFiling(graph.saved, graph.settings).setRemember(on, current) } }
        }
        StatusLine(SaveStatus.of(library, item.id))
    }
}

@Composable
private fun sectionText(section: QuickPick.Section): String = when (section) {
    QuickPick.Section.RECENT -> stringResource(Res.string.saved_recently_used)
    QuickPick.Section.ALL -> stringResource(Res.string.saved_sheet_all_folders)
    QuickPick.Section.MATCHES -> stringResource(Res.string.saved_matches)
    QuickPick.Section.NO_MATCH -> stringResource(Res.string.saved_sheet_no_match)
}

@Composable
private fun SheetSearch(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.saved_search_or_create)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
    )
}

/** "Create “Japan” and save", for a typed name no folder has yet. */
@Composable
private fun CreateRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** A folder as a pill with its round mark, outlined in the accent with a check while the post is in it. */
@Composable
private fun FolderChip(folder: SavedFolder, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SavedIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
        border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    )
}

/** Whether a plain tap on the bookmark files into the folder picked last. */
@Composable
private fun RememberRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!on) }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(stringResource(Res.string.saved_remember_folder), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.saved_remember_folder_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/** Where the post is, read out as it changes; the icon is in the accent once it is saved. */
@Composable
private fun StatusLine(status: SaveStatus) {
    val saved = status != SaveStatus.NotSaved
    Hairline()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (saved) Icons.Filled.CheckCircle else SavedIcons.BookmarkBorder,
            contentDescription = null,
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            when (status) {
                SaveStatus.NotSaved -> stringResource(Res.string.saved_sheet_not_saved)
                SaveStatus.Unfiled -> stringResource(Res.string.saved_sheet_unfiled)
                is SaveStatus.InFolder -> stringResource(Res.string.saved_sheet_in_folder, status.name)
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
