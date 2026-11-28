package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.ui.common.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.SteadyWidth
import com.aimdi.xtanative.ui.common.FilterIcons
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedSort
import com.aimdi.xtanative.store.saved.SavedSource
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.ui.common.ActionsInTopBar
import com.aimdi.xtanative.ui.common.ColumnTopAppBar
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.common.ReadingColumn
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.TopBarBack
import com.aimdi.xtanative.ui.common.edgeFade
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.common.wheelScrollsSideways
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.downloads_title
import com.aimdi.xtanative.ui.generated.resources.offline_title
import com.aimdi.xtanative.ui.generated.resources.saved_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.saved_clear_search
import com.aimdi.xtanative.ui.generated.resources.saved_clear_selection
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_export_folder
import com.aimdi.xtanative.ui.generated.resources.saved_filter_and_sort
import com.aimdi.xtanative.ui.generated.resources.saved_find_deleted
import com.aimdi.xtanative.ui.generated.resources.saved_manage_folders
import com.aimdi.xtanative.ui.generated.resources.saved_media_only
import com.aimdi.xtanative.ui.generated.resources.saved_move_selected
import com.aimdi.xtanative.ui.generated.resources.saved_networks
import com.aimdi.xtanative.ui.generated.resources.saved_networks_all
import com.aimdi.xtanative.ui.generated.resources.saved_new_folder
import com.aimdi.xtanative.ui.generated.resources.saved_none_selected
import com.aimdi.xtanative.ui.generated.resources.saved_note_delete_confirm
import com.aimdi.xtanative.ui.generated.resources.saved_search_hint
import com.aimdi.xtanative.ui.generated.resources.saved_search_library
import com.aimdi.xtanative.ui.generated.resources.saved_select
import com.aimdi.xtanative.ui.generated.resources.saved_select_all
import com.aimdi.xtanative.ui.generated.resources.saved_selected
import com.aimdi.xtanative.ui.generated.resources.saved_sort_newest
import com.aimdi.xtanative.ui.generated.resources.saved_sort_oldest
import com.aimdi.xtanative.ui.generated.resources.saved_source_bluesky
import com.aimdi.xtanative.ui.generated.resources.saved_source_mastodon
import com.aimdi.xtanative.ui.generated.resources.saved_source_other
import com.aimdi.xtanative.ui.generated.resources.saved_source_reddit
import com.aimdi.xtanative.ui.generated.resources.saved_source_threads
import com.aimdi.xtanative.ui.generated.resources.saved_source_x
import com.aimdi.xtanative.ui.generated.resources.saved_tab_all
import com.aimdi.xtanative.ui.generated.resources.saved_tab_likes
import com.aimdi.xtanative.ui.generated.resources.saved_tab_notes
import com.aimdi.xtanative.ui.generated.resources.saved_tab_unfiled
import com.aimdi.xtanative.ui.generated.resources.saved_title
import com.aimdi.xtanative.ui.generated.resources.saved_write_note
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.home.CreatesWithKeys
import com.aimdi.xtanative.ui.home.FindsWithKeys
import com.aimdi.xtanative.ui.home.SelectsWithKeys
import com.aimdi.xtanative.ui.home.foundWithKeys
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.takesPostDrops
import com.aimdi.xtanative.ui.theme.edgeColor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where Saved leads: Manage folders, a note's thread, the downloads, the
 * offline library and Settings; and XTA's search of the whole library,
 * offered once a screen for it is given.
 */
class SavedLinks(
    val openFolders: () -> Unit,
    val openThread: (rootId: String) -> Unit,
    val openDownloads: () -> Unit,
    val openOffline: () -> Unit,
    val openSettings: () -> Unit,
    val searchLibrary: (() -> Unit)? = null,
)

/** A dialog or sheet Saved opens over itself. */
private sealed interface SavedDialog {
    data class EditFolder(val folder: SavedFolder?) : SavedDialog

    data class FolderMenu(val folder: SavedFolder) : SavedDialog

    data class DeleteFolder(val folder: SavedFolder) : SavedDialog

    data class ExportFolder(val folder: SavedFolder, val format: ExportFormat? = null) : SavedDialog

    data object MoveSelected : SavedDialog

    data object RemoveSelected : SavedDialog

    data object Cleanup : SavedDialog

    data class Compose(val target: NoteTarget.Note) : SavedDialog

    data class DeleteNote(val note: LocalPost) : SavedDialog
}

/**
 * Saved, XTA's archive of what the device keeps (saved_screen.dart): the
 * title with its lock and menu, the search with Filter and sort, the chips
 * (the network picker, the tabs and a new folder), and the open tab. With
 * [onBack] it is a screen of its own; without, a home tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(actions: PostActions, links: SavedLinks, onBack: (() -> Unit)? = null) {
    val graph = LocalGraph.current
    val vm: SavedViewModel = viewModel { SavedViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SavedDialog?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val view = state.view
    // While selecting, the search is put away with the rest of the bar: Ctrl+F and / leave the selection be, rather than leave Saved.
    if (view.selecting) FindsWithKeys {}
    // Ctrl+A, Delete and Esc on the selection, as in a file manager; Delete asks first, as the bar's button does.
    SelectsWithKeys(
        selecting = view.selecting,
        selectAll = { dialog == null && vm.selectAllByKeys() },
        remove = { (dialog == null && view.removableByKeys).also { if (it) dialog = SavedDialog.RemoveSelected } },
        leave = { (dialog == null && view.selecting).also { if (it) vm.finishSelection() } },
    )
    // A folder another part of the app asked for (the desktop's quick switcher).
    val asked by SavedRequests.tab.collectAsStateWithLifecycle()
    LaunchedEffect(asked) {
        val tab = asked ?: return@LaunchedEffect
        if (SavedRequests.taken(tab)) vm.select(tab)
    }
    // Ctrl+N writes a note, as the round button does; not while selecting, where the button is put away.
    CreatesWithKeys { (dialog == null && !view.selecting).also { if (it) dialog = SavedDialog.Compose(NoteTarget.Note()) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (view.selecting) {
                SelectionBar(
                    count = view.selected.size,
                    allSelected = view.allSelected(state.visible),
                    onClose = vm::finishSelection,
                    onSelectAll = vm::selectAll,
                    onMove = { dialog = SavedDialog.MoveSelected },
                    onRemove = { dialog = SavedDialog.RemoveSelected },
                )
            } else {
                TitleBar(SavedNotice.of(view.tab), onBack, searchable = links.searchLibrary != null, onWrite = { dialog = SavedDialog.Compose(NoteTarget.Note()) }) { action ->
                    when (action) {
                        SavedMenuAction.SEARCH_LIBRARY -> links.searchLibrary?.invoke()
                        SavedMenuAction.CREATE_FOLDER -> dialog = SavedDialog.EditFolder(null)
                        SavedMenuAction.MANAGE_FOLDERS -> links.openFolders()
                        SavedMenuAction.DOWNLOADS -> links.openDownloads()
                        SavedMenuAction.OFFLINE -> links.openOffline()
                        SavedMenuAction.CLEANUP -> dialog = SavedDialog.Cleanup
                        SavedMenuAction.SETTINGS -> links.openSettings()
                    }
                }
            }
        },
        floatingActionButton = {
            if (!view.selecting && !ActionsInTopBar) {
                FloatingActionButton(onClick = { dialog = SavedDialog.Compose(NoteTarget.Note()) }) {
                    Icon(SavedIcons.EditNote, stringResource(Res.string.saved_write_note))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), horizontalAlignment = Alignment.CenterHorizontally) {
            // The search and the chips over the reading column the posts below keep to, in a wide window.
            if (!view.selecting) Column(Modifier.widthIn(max = ReadingColumn.MAX_WIDTH.dp).fillMaxWidth()) {
                SearchRow(
                    query = query,
                    onQuery = { query = it; vm.search(it) },
                    menu = state.filterMenu,
                    sort = view.sort,
                    mediaOnly = view.mediaOnly,
                    onFilter = vm::filter,
                )
                ChipRow(
                    chips = state.chips,
                    tab = view.tab,
                    likesByGroup = view.likesByGroup,
                    source = view.source.takeIf { view.tab != SavedTabs.NOTES },
                    onSource = vm::pickSource,
                    onSelect = vm::select,
                    onFolderMenu = { dialog = SavedDialog.FolderMenu(it) },
                    onNewFolder = { dialog = SavedDialog.EditFolder(null) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.loaded) {
                    SavedBodyView(
                        state = state,
                        actions = actions,
                        contentPadding = PaddingValues(top = 4.dp, bottom = padding.calculateBottomPadding() + 88.dp),
                        onWriteNote = { dialog = SavedDialog.Compose(NoteTarget.Note()) },
                        onNote = { target -> dialog = SavedDialog.Compose(target) },
                        onDeleteNote = { dialog = SavedDialog.DeleteNote(it) },
                        onOpenThread = links.openThread,
                        vm = vm,
                    )
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit
        is SavedDialog.EditFolder -> FolderDialog(open.folder, onDismiss = { dialog = null }) { name, autoDownload, upload ->
            val folder = open.folder
            if (folder == null) vm.createFolder(name, autoDownload, upload) else vm.updateFolder(folder.id, name, autoDownload, upload)
            dialog = null
        }
        is SavedDialog.FolderMenu -> FolderMenuSheet(
            onRename = { dialog = SavedDialog.EditFolder(open.folder) },
            onDelete = { dialog = SavedDialog.DeleteFolder(open.folder) },
            onManage = { dialog = null; links.openFolders() },
            onExport = { dialog = SavedDialog.ExportFolder(open.folder) },
            onDismiss = { dialog = null },
        )
        is SavedDialog.ExportFolder -> when (val format = open.format) {
            null -> ExportFormatSheet(onDismiss = { dialog = null }) { dialog = open.copy(format = it) }
            else -> ExportFolder(open.folder, state.library, format, stringResource(Res.string.saved_export_folder), onDone = { dialog = null })
        }
        is SavedDialog.DeleteFolder -> DeleteFolderDialog(onDismiss = { dialog = null }) {
            vm.deleteFolder(open.folder.id)
            dialog = null
        }
        SavedDialog.MoveSelected -> MoveSheet(state.folders, onDismiss = { dialog = null }) { folderId ->
            vm.moveSelected(folderId)
            dialog = null
        }
        SavedDialog.RemoveSelected -> RemoveSelectedDialog(view.selected.size, onDismiss = { dialog = null }) {
            vm.removeSelected()
            dialog = null
        }
        SavedDialog.Cleanup -> CleanupDialog(state.library.posts, onDismiss = { dialog = null })
        is SavedDialog.Compose -> key(open.target) {
            NoteEditorDialog(
                target = open.target,
                onDismiss = { dialog = null },
                onSaved = { saved ->
                    vm.showNotes()
                    val parent = open.target.replyTo
                    if (parent != null && saved != null) links.openThread(LocalPosts.threadRootId(state.notes + saved, parent.id))
                },
            )
        }
        is SavedDialog.DeleteNote -> ConfirmDialog(
            title = stringResource(Res.string.saved_are_you_sure),
            text = stringResource(Res.string.saved_note_delete_confirm),
            confirm = stringResource(Res.string.saved_delete),
            onDismiss = { dialog = null },
        ) {
            vm.deleteNote(open.note.id)
            dialog = null
        }
    }
}

/**
 * The title, the lock that says what stays on this device, and the archive's
 * menu. The title is XTA's 28 sp on a phone; on the desktop it is the size of
 * every other page's title beside it in the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleBar(notice: SavedNotice, onBack: (() -> Unit)?, searchable: Boolean, onWrite: () -> Unit, onMenu: (SavedMenuAction) -> Unit) {
    val noticeText = onDeviceText(notice)
    ColumnTopAppBar(
        navigationIcon = {
            if (onBack != null) TopBarBack(onBack)
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TipText(
                    stringResource(Res.string.saved_title),
                    fontSize = if (ActionsInTopBar) TextUnit.Unspecified else 28.sp,
                    fontWeight = if (ActionsInTopBar) null else FontWeight.Bold,
                    letterSpacing = if (ActionsInTopBar) TextUnit.Unspecified else (-0.56).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconTip(noticeText) { IconButton(onClick = { KeepNotices.show(KeepNotice.OnDevice(notice)) }) {
                    Icon(Icons.Filled.Lock, noticeText, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                } }
            }
        },
        actions = {
            // On the desktop "Write a note" sits here, by the menu, rather than in a round button in the corner.
            if (ActionsInTopBar) {
                val write = stringResource(Res.string.saved_write_note)
                IconTip(write) { IconButton(onClick = onWrite) { Icon(SavedIcons.EditNote, write) } }
            }
            SavedMenu(searchable, onMenu)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun SavedMenu(searchable: Boolean, onMenu: (SavedMenuAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(action: SavedMenuAction, icon: ImageVector, label: String) =
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { open = false; onMenu(action) })
            if (searchable) item(SavedMenuAction.SEARCH_LIBRARY, SavedIcons.ManageSearch, stringResource(Res.string.saved_search_library))
            item(SavedMenuAction.CREATE_FOLDER, SavedIcons.NewFolder, stringResource(Res.string.saved_new_folder))
            item(SavedMenuAction.MANAGE_FOLDERS, SavedIcons.Folder, stringResource(Res.string.saved_manage_folders))
            item(SavedMenuAction.DOWNLOADS, SavedIcons.Download, stringResource(Res.string.downloads_title))
            item(SavedMenuAction.OFFLINE, SavedIcons.OfflinePinOutline, stringResource(Res.string.offline_title))
            item(SavedMenuAction.CLEANUP, SavedIcons.Cleaning, stringResource(Res.string.saved_find_deleted))
            item(SavedMenuAction.SETTINGS, Icons.Filled.Settings, stringResource(Res.string.settings))
        }
    }
}

/** While selecting: close, how many are selected, select all, move and remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(count: Int, allSelected: Boolean, onClose: () -> Unit, onSelectAll: () -> Unit, onMove: () -> Unit, onRemove: () -> Unit) {
    ColumnTopAppBar(
        navigationIcon = { IconTip(stringResource(Res.string.close)) { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) } } },
        title = { Text(if (count == 0) stringResource(Res.string.saved_none_selected) else pluralStringResource(Res.plurals.saved_selected, count, count)) },
        actions = {
            IconTip(stringResource(if (allSelected) Res.string.saved_clear_selection else Res.string.saved_select_all)) { IconButton(onClick = onSelectAll) {
                Icon(
                    if (allSelected) SavedIcons.Deselect else SavedIcons.SelectAll,
                    stringResource(if (allSelected) Res.string.saved_clear_selection else Res.string.saved_select_all),
                )
            } }
            IconTip(stringResource(Res.string.saved_move_selected)) { IconButton(onClick = onMove, enabled = count > 0) { Icon(SavedIcons.MoveToFolder, stringResource(Res.string.saved_move_selected)) } }
            IconTip(stringResource(Res.string.saved_delete)) { IconButton(onClick = onRemove, enabled = count > 0) { Icon(Icons.Filled.Delete, stringResource(Res.string.saved_delete)) } }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** The search, always there, with a clear button once something is typed, and Filter and sort at its end. */
@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, menu: SavedFilterMenu, sort: SavedSort, mediaOnly: Boolean, onFilter: (SavedFilterAction) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SearchField(
        value = query,
        onValueChange = onQuery,
        placeholder = stringResource(Res.string.saved_search_hint),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).foundWithKeys(),
        trailing = {
            if (query.isNotEmpty()) {
                IconTip(stringResource(Res.string.saved_clear_search)) { IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, stringResource(Res.string.saved_clear_search), tint = muted, modifier = Modifier.size(18.dp)) } }
            }
            VerticalDivider(Modifier.height(20.dp), color = edgeColor())
            FilterButton(menu, sort, mediaOnly, onFilter)
        },
    )
}

/** Newest or oldest saved first, Media only, and Select; off where the tab has none of them. */
@Composable
private fun FilterButton(menu: SavedFilterMenu, sort: SavedSort, mediaOnly: Boolean, onFilter: (SavedFilterAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconTip(stringResource(Res.string.saved_filter_and_sort)) { IconButton(onClick = { open = true }, enabled = menu.enabled) {
            Icon(FilterIcons.SortAndFilter, stringResource(Res.string.saved_filter_and_sort), modifier = Modifier.size(20.dp))
        } }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun checked(action: SavedFilterAction, label: String, on: Boolean) = DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { if (on) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                onClick = { open = false; onFilter(action) },
            )
            if (menu.sortable) {
                checked(SavedFilterAction.SORT_NEWEST, stringResource(Res.string.saved_sort_newest), sort == SavedSort.NEWEST)
                checked(SavedFilterAction.SORT_OLDEST, stringResource(Res.string.saved_sort_oldest), sort == SavedSort.OLDEST)
            }
            if (menu.sortable && menu.showMedia) HorizontalDivider()
            if (menu.showMedia) checked(SavedFilterAction.MEDIA_ONLY, stringResource(Res.string.saved_media_only), mediaOnly)
            if (menu.sortable) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.saved_select)) },
                    leadingIcon = { Icon(SavedIcons.Checklist, null) },
                    onClick = { open = false; onFilter(SavedFilterAction.SELECT) },
                )
            }
        }
    }
}

/**
 * The chips under the search: the network picker and a rule, the tabs, and a
 * + for a new folder, kept in reach after them. Where the chips overflow, the
 * mouse wheel scrolls them and the cut end fades.
 */
@Composable
private fun ChipRow(
    chips: List<SavedChip>,
    tab: String,
    likesByGroup: Boolean,
    source: SavedSource?,
    onSource: (SavedSource) -> Unit,
    onSelect: (String) -> Unit,
    onFolderMenu: (SavedFolder) -> Unit,
    onNewFolder: () -> Unit,
) {
    val row = rememberLazyListState()
    val keeper = LocalKeeper.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            Modifier.weight(1f).wheelScrollsSideways(row).edgeFade(row),
            state = row,
            contentPadding = PaddingValues(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (source != null) {
                item(key = "source") { SourcePicker(source, onSource) }
                item(key = "rule") { VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant) }
            }
            items(chips, key = { it.token }) { chip ->
                val selected = chip.token == tab
                TabChip(
                    label = chipLabel(chip),
                    selected = selected,
                    trailing = if (selected && chip.token == SavedTabs.FAVORITES) {
                        if (likesByGroup) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
                    } else {
                        null
                    },
                    onClick = { onSelect(chip.token) },
                    onLongClick = chip.folder?.let { folder -> { onFolderMenu(folder) } },
                    // A post dragged from its card files in a folder's chip, or Unfiled's ([takesPostDrops]).
                    onPost = when {
                        chip.folder != null -> { post -> Keeping.fileByDrop(keeper, Keepable.of(post), chip.folder.id) }
                        chip.token == SavedTabs.UNFILED -> { post -> Keeping.fileByDrop(keeper, Keepable.of(post), null) }
                        else -> null
                    },
                )
            }
        }
        IconTip(stringResource(Res.string.saved_new_folder)) { IconButton(onClick = onNewFolder) {
            Icon(Icons.Filled.Add, stringResource(Res.string.saved_new_folder), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        } }
    }
}

@Composable
private fun chipLabel(chip: SavedChip): String = when (chip.token) {
    SavedTabs.ALL -> stringResource(Res.string.saved_tab_all)
    SavedTabs.UNFILED -> stringResource(Res.string.saved_tab_unfiled)
    SavedTabs.FAVORITES -> stringResource(Res.string.saved_tab_likes)
    SavedTabs.NOTES -> stringResource(Res.string.saved_tab_notes)
    else -> chip.folder?.name.orEmpty()
}

/** A tab as a plain outlined chip, the selected one written and outlined in the accent; a folder's opens its menu on a long press, and takes a post dropped on it ([onPost]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(label: String, selected: Boolean, trailing: ImageVector?, onClick: () -> Unit, onLongClick: (() -> Unit)?, onPost: ((XPost) -> Unit)? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .then(onPost?.let { Modifier.takesPostDrops(it) } ?: Modifier)
            .border(1.dp, if (selected) accent else edgeColor(), shape)
            // A right-click opens what a long press does, as a desktop opens a thing's menu.
            .then(onLongClick?.let { Modifier.onSecondaryClick(it) } ?: Modifier)
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // As wide as bold whether chosen or not, so choosing it moves no chip.
        SteadyWidth({ Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) }) {
            TipText(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailing, contentDescription = null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

/** The network picker: an outlined chip with the network's mark, its name ("All networks" until one is chosen) and a chevron. */
@Composable
private fun SourcePicker(selected: SavedSource, onPick: (SavedSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box {
        Row(
            Modifier
                .clip(shape)
                .border(1.dp, edgeColor(), shape)
                .clickable(role = Role.DropdownList, onClickLabel = stringResource(Res.string.saved_networks)) { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceMark(selected, 16)
            Spacer(Modifier.width(6.dp))
            Text(sourceLabel(selected), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SavedSource.entries.forEach { source ->
                DropdownMenuItem(
                    text = { Text(sourceLabel(source), fontWeight = if (source == selected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { SourceMark(source, 20) },
                    trailingIcon = { if (source == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { open = false; onPick(source) },
                )
            }
        }
    }
}

@Composable
fun sourceLabel(source: SavedSource): String = when (source) {
    SavedSource.ALL -> stringResource(Res.string.saved_networks_all)
    SavedSource.X -> stringResource(Res.string.saved_source_x)
    SavedSource.REDDIT -> stringResource(Res.string.saved_source_reddit)
    SavedSource.MASTODON -> stringResource(Res.string.saved_source_mastodon)
    SavedSource.BLUESKY -> stringResource(Res.string.saved_source_bluesky)
    SavedSource.THREADS -> stringResource(Res.string.saved_source_threads)
    SavedSource.OTHER -> stringResource(Res.string.saved_source_other)
}

