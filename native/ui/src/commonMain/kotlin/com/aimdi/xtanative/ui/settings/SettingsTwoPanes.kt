package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.ColumnScrollbar
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_clear
import com.aimdi.xtanative.ui.generated.resources.settings_app_info
import com.aimdi.xtanative.ui.generated.resources.settings_plugin_store
import com.aimdi.xtanative.ui.generated.resources.settings_search
import com.aimdi.xtanative.ui.generated.resources.settings_title
import com.aimdi.xtanative.ui.home.LocalBesideList
import com.aimdi.xtanative.ui.home.LocalDesktopActions
import com.aimdi.xtanative.ui.home.NavRowFrame
import com.aimdi.xtanative.ui.home.NavRowLook
import com.aimdi.xtanative.ui.pluginstore.PluginRoutes
import com.aimdi.xtanative.ui.pluginstore.PluginStoreScreen
import com.aimdi.xtanative.ui.home.foundWithKeys
import com.aimdi.xtanative.ui.home.verticalScrollByKeys
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Settings in a wide desktop window ([SettingsPanes]): the search and the
 * list of its sections at the start, the section picked standing beside
 * them, as a desktop's settings window. ↑ and ↓ walk the list, showing each
 * section as they reach it; Enter or Tab goes into the section, Esc there
 * back to its row; the plugin store stands there as a section does. What a
 * section opens that is no section (Backup, a plugin, an account's sign-in)
 * is pushed as on a phone ([onOpen]).
 */
@Composable
internal fun SettingsTwoPanes(
    vm: SettingsViewModel,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onIntroAgain: () -> Unit,
    onStartOver: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(SettingsPanes.last) }
    var focus by remember { mutableStateOf<String?>(null) }
    val shown = SettingsPanes.shown(picked)
    val marked = SettingsPanes.marked(shown)
    val rows = remember { SettingsPanes.rows.associateWith { FocusRequester() } }
    val pane = remember { FocusRequester() }
    var inPane by remember { mutableStateOf(false) }

    fun show(id: String, control: String? = null) {
        picked = id
        SettingsPanes.last = id
        focus = control
    }

    // A section's own way on: one the list shows (Data's Sync) beside it, anything else as its own screen.
    val open: (String) -> Unit = { route ->
        val target = SettingsPanes.target(route)
        if (target != null) show(target.id, target.focus) else onOpen(route)
    }
    val pick: (String) -> Unit = { id -> show(id) }
    // The keys move the focus here themselves, so Compose is told the keyboard is in use, as its own Tab does: after a
    // click on a row it holds the pointer's mode and drops the focus from the next row's lighting, which showed no ring.
    val modes = LocalInputModeManager.current
    val intoPane = {
        modes.requestInputMode(InputMode.Keyboard)
        runCatching { pane.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
    }
    // Whether the row took the focus: none does while search results stand in place of the list.
    val toRow = { id: String ->
        modes.requestInputMode(InputMode.Keyboard)
        runCatching { rows[id]?.requestFocus(FocusDirection.Enter) == true }.getOrDefault(false)
    }

    // Esc in the section takes the keys back to its row in the list, before it would leave Settings; with no list
    // shown (a search's results), it leaves the section as Esc does anywhere else.
    val actions = LocalDesktopActions.current
    val latestMarked by rememberUpdatedState(marked)
    val latestInPane by rememberUpdatedState(inPane)
    DisposableEffect(actions) {
        val owner = Any()
        actions?.addFieldEscape(owner) { latestInPane && toRow(latestMarked) }
        onDispose { actions?.removeFieldEscape(owner) }
    }

    fun keys(id: String, event: KeyEvent): Boolean {
        if (event.isAltPressed || event.isCtrlPressed) return false
        val down = event.type == KeyEventType.KeyDown
        return when (event.key) {
            Key.DirectionDown, Key.DirectionUp -> {
                if (down && !event.isShiftPressed) {
                    SettingsPanes.step(id, if (event.key == Key.DirectionDown) 1 else -1)?.let { next ->
                        toRow(next)
                        show(next)
                    }
                }
                !event.isShiftPressed
            }
            Key.Enter, Key.NumPadEnter -> {
                if (down) {
                    pick(id)
                    intoPane()
                }
                true
            }
            Key.Tab -> !event.isShiftPressed && (!down || intoPane())
            else -> false
        }
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(SettingsPanes.LIST_WIDTH.dp).fillMaxHeight()) {
            SettingsPage(stringResource(Res.string.settings_title), onBack) { padding ->
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Column(Modifier.verticalScrollByKeys(scroll).padding(bottom = 24.dp)) {
                        SearchField(
                            value = query,
                            onValueChange = onQuery,
                            placeholder = stringResource(Res.string.settings_search),
                            trailing = {
                                if (query.isNotEmpty()) {
                                    IconTip(stringResource(Res.string.discover_clear)) {
                                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(Res.string.discover_clear)) }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp).foundWithKeys(),
                        )
                        if (query.isBlank()) {
                            SectionList(marked, rows, ::keys, pick)
                        } else {
                            SearchResults(query, open)
                        }
                    }
                    ColumnScrollbar(scroll, Modifier.align(Alignment.TopEnd))
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .onFocusChanged { inPane = it.hasFocus }
                .focusRequester(pane)
                .focusGroup(),
        ) {
            // A page one of the list's opens (Data's Sync) keeps its Back, to the page it came from.
            val paneBack = { if (shown != marked) show(marked) else onBack() }
            CompositionLocalProvider(LocalBesideList provides (shown == marked)) {
                key(shown) {
                    if (shown == SettingsPanes.ABOUT) {
                        AboutPage(vm, onBack, onOpen, onIntroAgain)
                    } else if (shown == SettingsPanes.PLUGIN_STORE) {
                        PluginStoreScreen(onBack, onOpen = { id -> onOpen(PluginRoutes.client(id)) }, onSettings = { id -> onOpen(PluginRoutes.settings(id)) })
                    } else {
                        SettingsSectionScreen(shown, focus, onBack = paneBack, onOpen = open, onStartOver = onStartOver)
                    }
                }
            }
        }
    }
}

/** The list's rows ([SettingsPanes.rows]) in the settings home's groups, [marked] the one whose page stands beside it. */
@Composable
private fun SectionList(marked: String, rows: Map<String, FocusRequester>, keys: (String, KeyEvent) -> Boolean, pick: (String) -> Unit) {
    val groups = listOf(
        SettingsSection.everyday.map { it.id },
        listOf(SettingsSection.ACCOUNT.id, SettingsPanes.PLUGIN_STORE),
        listOf(SettingsSection.DATA.id) + SettingsSection.advanced.map { it.id },
        listOf(SettingsPanes.ABOUT),
    )
    groups.forEachIndexed { index, group ->
        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        group.forEach { id ->
            val section = SettingsSection.of(id)
            val (title, icon) = when {
                section != null -> stringResource(sectionTitle(section)) to sectionIcon(section)
                id == SettingsPanes.PLUGIN_STORE -> stringResource(Res.string.settings_plugin_store) to SettingsIcons.ExtensionOutlined
                else -> stringResource(Res.string.settings_app_info) to GroupIcons.InfoOutline
            }
            PaneRow(
                title, icon, marked = id == marked,
                modifier = Modifier.focusRequester(rows.getValue(id)).onPreviewKeyEvent { keys(id, it) },
            ) { pick(id) }
        }
    }
}

/**
 * One row of the list: a section's icon and title, lit as the frame's own
 * column lights the place shown ([NavRowFrame]: the accent and a bold title
 * while its page stands beside the list), the pointer only a faint wash and
 * the keyboard's focus a ring, so neither passes for the row picked.
 */
@Composable
private fun PaneRow(title: String, icon: ImageVector, marked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavRowFrame(marked, onClick, modifier.padding(vertical = 1.dp), shape = RoundedCornerShape(12.dp), minHeight = 44.dp) {
        Icon(icon, null, tint = if (marked) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = NavRowLook.weight(marked), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** Settings › App info as a page beside the list: the settings home's last rows. */
@Composable
private fun AboutPage(vm: SettingsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onIntroAgain: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    SettingsPage(stringResource(Res.string.settings_app_info), onBack, snackbar) { padding ->
        SettingsList(padding) { AboutRows(onOpen, onIntroAgain, snackbar, vm) }
    }
}
