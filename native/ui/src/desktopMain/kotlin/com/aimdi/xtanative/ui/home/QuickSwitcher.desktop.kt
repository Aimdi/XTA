package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.ui.Routes
import com.aimdi.xtanative.ui.chrome.push
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_antennas
import com.aimdi.xtanative.ui.generated.resources.home_groups
import com.aimdi.xtanative.ui.generated.resources.home_home
import com.aimdi.xtanative.ui.generated.resources.home_saved
import com.aimdi.xtanative.ui.generated.resources.keys_quick_switch
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.generated.resources.settings_downloads
import com.aimdi.xtanative.ui.generated.resources.settings_no_results
import com.aimdi.xtanative.ui.generated.resources.settings_offline
import com.aimdi.xtanative.ui.saved.SavedRequests
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.SettingsSection
import com.aimdi.xtanative.ui.settings.sectionTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The quick switcher (Ctrl+P, Ctrl+K): a field at the top of the window over
 * the reader's own places ([rememberQuickTargets]), filtered as it is typed
 * into ([QuickSwitch.matching]). ↑ ↓ (round), Page Up and Down move the
 * choice, Enter or a click goes there ([onGo]), Esc or a click beside it
 * closes it; the keys go back to the page either way ([onDismiss]).
 */
@Composable
internal fun QuickSwitcher(targets: List<QuickTarget>, onGo: (QuickGo) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableIntStateOf(0) }
    val shown = remember(targets, query) { QuickSwitch.matching(targets, query) }
    val list = rememberLazyListState()
    val field = remember { FocusRequester() }
    LaunchedEffect(shown) { chosen = 0 }
    LaunchedEffect(chosen, shown) {
        // Only as far as the chosen row needs to show, as a menu's list moves.
        val seen = list.layoutInfo.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= list.layoutInfo.viewportEndOffset }
        val first = seen.firstOrNull()?.index ?: return@LaunchedEffect
        QuickSwitch.scrolledTo(chosen, first, seen.last().index)?.let { list.scrollToItem(it) }
    }
    LaunchedEffect(Unit) {
        // The popup's window may take the keys a frame late, dropping a focus asked for before.
        repeat(FOCUS_TRIES) {
            withFrameNanos {}
            if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    val go = { index: Int -> shown.getOrNull(index)?.let { onDismiss(); onGo(it.go) } }
    Popup(popupPositionProvider = OverWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier.fillMaxSize()
                .focusProperties { canFocus = false }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .padding(top = TOP, start = 16.dp, end = 16.dp)
                    .widthIn(max = WIDTH)
                    .focusProperties { canFocus = false }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> chosen = QuickSwitch.moved(chosen, shown.size, 1)
                            Key.DirectionUp -> chosen = QuickSwitch.moved(chosen, shown.size, -1)
                            Key.PageDown -> chosen = QuickSwitch.paged(chosen, shown.size, PAGE)
                            Key.PageUp -> chosen = QuickSwitch.paged(chosen, shown.size, -PAGE)
                            Key.Enter, Key.NumPadEnter -> go(chosen)
                            Key.Escape -> onDismiss()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    val hint = stringResource(Res.string.keys_quick_switch)
                    SearchField(query, { query = it }, hint, Modifier.fillMaxWidth().padding(12.dp).focusRequester(field), onSearch = { go(chosen) })
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(Res.string.settings_no_results),
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = LIST_HEIGHT).padding(bottom = 8.dp), state = list) {
                        itemsIndexed(shown, key = { _, target -> target.id }) { index, target ->
                            QuickRow(target, index == chosen) { go(index) }
                        }
                    }
                }
            }
        }
    }
}

/** One place: its name, and what it is at the end of the row; the chosen one lit as a menu's is. */
@Composable
private fun QuickRow(target: QuickTarget, chosen: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(if (chosen) colors.secondaryContainer else colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            target.title,
            Modifier.weight(1f).padding(end = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (chosen) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        target.label.takeIf { it.isNotEmpty() }?.let { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (chosen) colors.onSecondaryContainer else colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun kindLabel(kind: QuickKind): String? = when (kind) {
    QuickKind.SCREEN -> null
    QuickKind.TIMELINE -> stringResource(Res.string.home_home)
    QuickKind.GROUP -> stringResource(Res.string.home_groups)
    QuickKind.ANTENNA -> stringResource(Res.string.home_antennas)
    QuickKind.FOLDER -> stringResource(Res.string.home_saved)
    QuickKind.SETTINGS -> stringResource(Res.string.settings)
}

/**
 * The places the quick switcher lists, read from the device only: Home and
 * the screens of its menu, Home's timelines and bar pages (while Home is in
 * [nav]), the groups, antennas and Saved's folders, and Settings' pages.
 */
@Composable
internal fun rememberQuickTargets(nav: NavHostController): List<QuickTarget> {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsState()
    val antennas by produceState(graph.antennas.state.value.orEmpty(), graph) { value = graph.antennas.load() }
    val library by produceState(SavedLibrary(), graph) { value = graph.saved.library() }
    val home = remember(nav) { runCatching { nav.getBackStackEntry(Routes.HOME) }.getOrNull() }
    val ui = home?.let { frameModel(it).ui.collectAsStateWithLifecycle().value }
    val bar = ui?.bar.orEmpty()
    val barIds = bar.mapTo(HashSet()) { it.id }
    val exists = { route: String -> runCatching { nav.graph.findNode(route) != null }.getOrDefault(false) }
    val homeLabel = stringResource(Res.string.home_home)
    val screens = buildList {
        if (home != null && bar.isNotEmpty()) add(QuickTarget("home", homeLabel, QuickKind.SCREEN, QuickGo.HomePage(HomeBar.startPage(bar, ui?.initialBarIndex ?: 0).id)))
        (HomeMenu.list + HomeDestination.SETTINGS).filter { exists(it.route) }.forEach { destination ->
            val page = destination.barPage?.takeIf { home != null && it in barIds }
            add(QuickTarget("screen:" + destination.route, menuLabel(destination), QuickKind.SCREEN, page?.let(QuickGo::HomePage) ?: QuickGo.Route(destination.route)))
        }
    }
    val timelines = ui?.let { now ->
        now.tabs.map { tab -> QuickTarget("tab:" + tab.key, tabLabel(tab, now), QuickKind.TIMELINE, QuickGo.Timeline(tab.key)) } +
            now.bar.filter { it is BarPage.Network || it is BarPage.Group }
                .map { page -> QuickTarget("page:" + page.id, barLabel(page, now), QuickKind.TIMELINE, QuickGo.HomePage(page.id)) }
    }.orEmpty()
    val onBar = bar.filterIsInstance<BarPage.Group>().mapTo(HashSet()) { it.groupId }
    val groups = FollowGroup.ordered(session?.groups.orEmpty()).filter { it.id !in onBar }
        .map { QuickTarget("group:" + it.id, it.name, QuickKind.GROUP, QuickGo.Route(Routes.group(it.id))) }
    val antennaTargets = antennas.map { QuickTarget("antenna:" + it.id, it.name, QuickKind.ANTENNA, QuickGo.Route(Routes.antenna(it.id))) }
    val folders = SavedLibrary.ordered(library.folders).map { QuickTarget("folder:" + it.id, it.name, QuickKind.FOLDER, QuickGo.SavedTab(it.id, onBar = HomeBar.SAVED in barIds)) }
    val settings = (SettingsSection.everyday + SettingsSection.ACCOUNT + SettingsSection.DATA + SettingsSection.advanced).map { section ->
        QuickTarget("settings:" + section.id, stringResource(sectionTitle(section)), QuickKind.SETTINGS, QuickGo.Route(SettingsRoutes.of(section)))
    } + listOf(
        QuickTarget("settings:downloads", stringResource(Res.string.settings_downloads), QuickKind.SETTINGS, QuickGo.Route(SettingsRoutes.DOWNLOADS)),
        QuickTarget("settings:offline", stringResource(Res.string.settings_offline), QuickKind.SETTINGS, QuickGo.Route(SettingsRoutes.OFFLINE)),
    )
    val labels = QuickKind.entries.associateWith { kindLabel(it).orEmpty() }
    return QuickSwitch.distinct(screens + timelines + groups + antennaTargets + folders + settings).map { it.copy(label = labels.getValue(it.kind)) }
}

/**
 * Goes where [go] leads in [nav]: Home's page or timeline on Home (back to
 * it, whatever is over it), Saved on a folder (on Home's bar where it is
 * one of its pages, else its own screen), any other place over the screen
 * shown, unless it is that screen already.
 */
internal fun quickGo(nav: NavHostController, go: QuickGo) {
    when (go) {
        is QuickGo.HomePage -> {
            HomeRequests.showPage(go.id)
            nav.popBackStack(Routes.HOME, inclusive = false)
        }
        is QuickGo.Timeline -> {
            HomeRequests.show(go.key)
            nav.popBackStack(Routes.HOME, inclusive = false)
        }
        is QuickGo.SavedTab -> {
            SavedRequests.show(go.tab)
            quickGo(nav, if (go.onBar) QuickGo.HomePage(HomeBar.SAVED) else QuickGo.Route(Routes.SAVED))
        }
        is QuickGo.Route -> if (nav.currentBackStackEntry?.destination?.route?.substringBefore('?') != go.route.substringBefore('?')) nav.push(go.route)
    }
}

/** The quick switcher while the window's keys asked for it, over the places of its navigation. */
@Composable
internal actual fun QuickSwitchHost() {
    val request = LocalQuickSwitch.current ?: return
    if (!request.open) return
    QuickSwitcher(rememberQuickTargets(request.nav), onGo = { go -> quickGo(request.nav, go) }, onDismiss = request::close)
}

/** A popup laid over the whole window, from its top left corner. */
private object OverWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

private val TOP = 72.dp
private val WIDTH = 560.dp
private val LIST_HEIGHT = 420.dp

/** How many rows Page Up and Down move the choice. */
private const val PAGE = 8

/** How many frames the field is asked to take the focus before giving up. */
private const val FOCUS_TRIES = 10
