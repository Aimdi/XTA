package com.aimdi.xtanative.ui.antenna

import com.aimdi.xtanative.ui.generated.resources.saved_save
import com.aimdi.xtanative.ui.generated.resources.saved_create
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_terms_hint
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_about
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.aimdi.xtanative.ui.post.PostMenuSurface
import com.aimdi.xtanative.ui.post.MenuRow
import com.aimdi.xtanative.ui.common.onSecondaryClick
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.aimdi.xtanative.ui.common.destructiveButtonColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.aimdi.xtanative.ui.common.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import com.aimdi.xtanative.ui.common.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.antenna.Antenna
import com.aimdi.xtanative.store.antenna.AntennaScope
import com.aimdi.xtanative.ui.common.ActionsInTopBar
import com.aimdi.xtanative.ui.common.ColumnTopAppBar
import com.aimdi.xtanative.ui.common.EmptyState
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.ReadingColumn
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.TopBarBack
import com.aimdi.xtanative.ui.common.dialogDefault
import com.aimdi.xtanative.ui.common.rememberSheetState
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_empty
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_exclude
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_include
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_name
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_new
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_scope_following
import com.aimdi.xtanative.ui.generated.resources.discover_antenna_scope_search
import com.aimdi.xtanative.ui.generated.resources.discover_antennas
import com.aimdi.xtanative.ui.generated.resources.discover_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.discover_cancel
import com.aimdi.xtanative.ui.generated.resources.discover_delete
import com.aimdi.xtanative.ui.generated.resources.discover_settings
import com.aimdi.xtanative.ui.generated.resources.discover_show_menu
import com.aimdi.xtanative.ui.generated.resources.problem_unable_to_load_the_tweets
import com.aimdi.xtanative.ui.home.CreatesWithKeys
import com.aimdi.xtanative.ui.home.verticalScrollByKeys
import com.aimdi.xtanative.ui.search.DiscoverIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** XTA's antennas list and editor are no wider than this on a wide screen: the reading column. */
private val ContentWidth = ReadingColumn.MAX_WIDTH.dp

/**
 * XTA's antennas: saved keyword listeners, newest first, each opening its
 * feed; the editor makes or changes one, and Delete asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntennasScreen(openAntenna: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: AntennasViewModel = viewModel { AntennasViewModel(graph) }
    val antennas by vm.antennas.collectAsStateWithLifecycle()
    val state by vm.ui.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    // Ctrl+N makes an antenna, as the round button does.
    CreatesWithKeys { (state.editing == null && state.deleting == null).also { if (it) vm.edit() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ColumnTopAppBar(
                navigationIcon = {
                    TopBarBack(onBack)
                },
                title = { Text(stringResource(Res.string.discover_antennas)) },
                actions = {
                    // Not while the empty page offers its own button for the first antenna.
                    if (ActionsInTopBar && !antennas.isNullOrEmpty()) {
                        val add = stringResource(Res.string.discover_antenna_new)
                        IconTip(add) { IconButton(onClick = { vm.edit() }) { Icon(Icons.Filled.Add, contentDescription = add) } }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (!ActionsInTopBar) FloatingActionButton(onClick = { vm.edit() }) { Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.discover_antenna_new)) }
        },
    ) { padding ->
        val list = antennas
        val failed = problem
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                failed != null -> ProblemView(failed, vm::retry, context = stringResource(Res.string.problem_unable_to_load_the_tweets))
                list == null -> LoadingRow()
                list.isEmpty() -> EmptyAntennas(onNew = { vm.edit() })
                else -> LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
                    modifier = Modifier.widthIn(max = ContentWidth).fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { _, antenna -> antenna.id }, contentType = { _, _ -> "antenna" }) { index, antenna ->
                        AntennaRow(antenna, onOpen = { openAntenna(antenna.id) }, onSettings = { vm.edit(antenna) }, onDelete = { vm.askDelete(antenna) })
                        if (index < list.lastIndex) Hairline()
                    }
                }
            }
        }
    }
    state.editing?.let { draft ->
        AntennaEditor(draft, onChange = vm::change, onSave = { vm.save { saved -> openAntenna(saved.id) } }, onDismiss = vm::closeEditor)
    }
    state.deleting?.let { antenna -> DeleteDialog(antenna, onDelete = vm::delete, onDismiss = vm::cancelDelete) }
}

@Composable
private fun scopeLabel(scope: AntennaScope): String =
    stringResource(if (scope == AntennaScope.FOLLOWING) Res.string.discover_antenna_scope_following else Res.string.discover_antenna_scope_search)

/**
 * An antenna: its name, whose posts it listens to and its terms; the menu
 * holds Settings and Delete, under its ⋮ or, at a right-click on the row, at
 * the pointer ([PostMenuSurface]), as a desktop opens a thing's menu.
 */
@Composable
private fun AntennaRow(antenna: Antenna, onOpen: () -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var pointerMenu by remember { mutableStateOf(false) }
    if (pointerMenu) {
        PostMenuSurface(onDismiss = { pointerMenu = false }) {
            MenuRow(DiscoverIcons.Tune, stringResource(Res.string.discover_settings)) {
                pointerMenu = false
                onSettings()
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                MenuRow(Icons.Filled.Delete, stringResource(Res.string.discover_delete)) {
                    pointerMenu = false
                    onDelete()
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).onSecondaryClick { pointerMenu = true }.clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DiscoverIcons.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            TipText(antenna.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val secondary = MaterialTheme.colorScheme.onSurfaceVariant
            TipText(scopeLabel(antenna.scope), style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            TipText(antenna.includeTerms.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconTip(stringResource(Res.string.discover_show_menu)) { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.discover_show_menu)) } }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.discover_settings)) },
                    leadingIcon = { Icon(DiscoverIcons.Tune, contentDescription = null) },
                    onClick = {
                        menu = false
                        onSettings()
                    },
                )
                val error = MaterialTheme.colorScheme.error
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.discover_delete), color = error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = error) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyAntennas(onNew: () -> Unit) {
    EmptyState(DiscoverIcons.Sensors, stringResource(Res.string.discover_antenna_empty), body = stringResource(Res.string.discover_antenna_about)) {
        Button(onClick = onNew, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.discover_antenna_new), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DeleteDialog(antenna: Antenna, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.discover_are_you_sure)) },
        text = { Text(antenna.name) },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = destructiveButtonColors(),
            ) { Text(stringResource(Res.string.discover_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.dialogDefault()) { Text(stringResource(Res.string.discover_cancel)) } },
    )
}

/**
 * XTA's antenna editor: a name, the terms to listen for and to leave out
 * (how they are written said under them), whose posts, and Create (Save for
 * one being changed). Enter in a field, or Ctrl+Enter, saves once it can
 * ([AntennaDraft.enter]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AntennaEditor(draft: AntennaDraft, onChange: (AntennaDraft) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    // A new antenna starts at its name, which has the keys as the editor opens.
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (draft.isNew) runCatching { nameFocus.requestFocus() } }
    KeyedSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState(alwaysFull = true)) {
        val focus = LocalFocusManager.current
        val enter: (Boolean) -> Unit = { ctrl ->
            when (AntennaDraft.enter(draft.canSave, ctrl)) {
                EditorEnter.SAVE -> onSave()
                EditorEnter.NEXT_FIELD -> focus.moveFocus(FocusDirection.Next).let { }
                EditorEnter.NOTHING -> Unit
            }
        }
        Column(
            // Above the keyboard, as XTA lifts its editor.
            Modifier.widthIn(max = ContentWidth).fillMaxWidth().align(Alignment.CenterHorizontally).imePadding()
                .verticalScrollByKeys(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (draft.isNew) Res.string.discover_antenna_new else Res.string.discover_antennas),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            EditorField(draft.name, Res.string.discover_antenna_name, onEnter = enter, modifier = Modifier.focusRequester(nameFocus)) { onChange(draft.copy(name = it)) }
            EditorField(draft.include, Res.string.discover_antenna_include, onEnter = enter) { onChange(draft.copy(include = it)) }
            EditorField(draft.exclude, Res.string.discover_antenna_exclude, last = true, onDone = { if (draft.canSave) onSave() }, onEnter = enter) {
                onChange(draft.copy(exclude = it))
            }
            Text(
                stringResource(Res.string.discover_antenna_terms_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ScopeChoice(draft.scope) { onChange(draft.copy(scope = it)) }
            Button(onClick = onSave, enabled = draft.canSave, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text(stringResource(if (draft.isNew) Res.string.saved_create else Res.string.saved_save))
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    label: StringResource,
    last: Boolean = false,
    onDone: () -> Unit = {},
    onEnter: (ctrl: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        // The hardware keyboard's Enter (Ctrl+Enter too) as a desktop form's: the editor decides ([AntennaDraft.enter]).
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
            (event.type == KeyEventType.KeyDown && enter && !event.isShiftPressed && !event.isAltPressed).also { if (it) onEnter(event.isCtrlPressed) }
        },
    )
}

/** All of search, or only accounts followed: side by side when there is room, else one under the other. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeChoice(scope: AntennaScope, onChange: (AntennaScope) -> Unit) {
    val scopes = listOf(AntennaScope.SEARCH, AntennaScope.FOLLOWING)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (AntennaDraft.scopesSideBySide(maxWidth.value, LocalDensity.current.fontScale)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                scopes.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = choice == scope,
                        onClick = { onChange(choice) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                    ) { Text(scopeLabel(choice)) }
                }
            }
        } else {
            Column {
                scopes.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = choice == scope, role = Role.RadioButton) { onChange(choice) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == scope, onClick = null)
                        Text(scopeLabel(choice), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}
