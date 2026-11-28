package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.aimdi.xtanative.ui.common.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.aimdi.xtanative.ui.common.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.DeckColumnOptions
import com.aimdi.xtanative.core.session.DeckColumns
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.settings.DeckWidth
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.ui.common.ColumnTopAppBar
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.common.LocalSheetCloser
import com.aimdi.xtanative.ui.common.ReadingColumn
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.TopBarBack
import com.aimdi.xtanative.ui.common.rememberSheetState
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.home_deck
import com.aimdi.xtanative.ui.generated.resources.home_deck_add
import com.aimdi.xtanative.ui.generated.resources.home_deck_empty
import com.aimdi.xtanative.ui.generated.resources.home_deck_move_left
import com.aimdi.xtanative.ui.generated.resources.home_deck_move_right
import com.aimdi.xtanative.ui.generated.resources.home_deck_remove
import com.aimdi.xtanative.ui.generated.resources.home_deck_width
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_medium
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_narrow
import com.aimdi.xtanative.ui.generated.resources.home_deck_width_wide
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.generated.resources.home_column_for_you
import com.aimdi.xtanative.ui.group.GroupFeedBody
import com.aimdi.xtanative.ui.group.groupFeedViewModel
import com.aimdi.xtanative.ui.plugins.LocalPluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginActions
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ShellFeedRow
import com.aimdi.xtanative.ui.plugins.pluginAccent
import com.aimdi.xtanative.ui.post.LocalMenuRowMetrics
import com.aimdi.xtanative.ui.post.MenuRowMetrics
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScreen
import com.aimdi.xtanative.ui.profile.ProfileScreen
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.positionInWindow
import com.aimdi.xtanative.ui.common.SearchField
import com.aimdi.xtanative.ui.generated.resources.home_deck_show_in
import com.aimdi.xtanative.ui.generated.resources.home_groups
import com.aimdi.xtanative.ui.generated.resources.home_networks
import com.aimdi.xtanative.ui.generated.resources.search
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.aimdi.xtanative.ui.common.EmptyState

/** How many frames Add column's field waits to be laid out before it takes the keys. */
private const val FIELD_FOCUS_TRIES = 3

/** How long a column asked for from elsewhere stays lit. */
private const val LIT_MS = 1500L

/** The narrowest the detail column gets: a post's conversation needs a page's width. */
private const val DETAIL_MIN_WIDTH = 420

/**
 * The deck (XTA's deck screen), for a wide window or a tablet: timelines
 * side by side, each a column of its own: Following, X's For you, any
 * network's timeline, any group. Columns widen to fill the window while
 * they fit and keep their width past that, the deck scrolling sideways.
 * Each column says what it is, goes back to its top when its name is
 * tapped, reads again on its own, and moves or leaves from its menu; Add
 * column offers what is not on the deck yet. A post or a profile opened from
 * a column opens beside them ([DeckDetail]), the columns staying as they
 * were. The desktop's F5 and Ctrl+R read every column again; Escape closes
 * what the detail column shows, one page at a time, and meanwhile the
 * reading keys move the page it shows, Ctrl+← and Ctrl+→ choosing it or a
 * column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    // The deck's own part of the session: a write to the rest (a follow, a read mark) redraws no column.
    val deck by remember(graph) { graph.sessions.state.map(DeckState::of).distinctUntilChanged() }
        .collectAsStateWithLifecycle(remember(graph) { DeckState.of(graph.sessions.state.value) })
    val columns = deck.columns
    val networks = LocalHomeNetworks.current
    val names = rememberColumnNames()
    val settings = LocalSettingsStore.current
    val width by remember(settings) { settings.observe(HomePrefs.deckColumnWidth) }.collectAsStateWithLifecycle(DeckWidth.MEDIUM)
    val scope = rememberCoroutineScope()
    val edit: (change: (List<String>) -> List<String>) -> Unit = { change -> scope.launch { graph.sessions.update { it.copy(deck = change(it.deck)) } } }
    val options = deck.options
    val setOptions: (String, DeckColumnOptions) -> Unit = { id, chosen -> scope.launch { graph.sessions.update { it.withDeckOptions(id, chosen) } } }
    val detail = remember { DeckDetail() }
    val columnActions = remember(actions, detail) { detail.actionsOver(actions) }
    val toTop = remember { mutableStateMapOf<String, Int>() }
    var refreshedAll by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<DeckAsk?>(null) }
    // The desktop's Add column drops down under the + (or the empty deck's button) that opened it, as Home's switcher does.
    val addAnchor = remember(scope) { SwitcherAnchor(scope) }
    val barAdd = addAnchor.button(ADD_IN_BAR)
    val openAdd: (SwitcherButton) -> Unit = { button -> addAnchor.claim(button); adding = true }
    // Ctrl+N adds a column, as the bar's + does, dropping down under it.
    CreatesWithKeys { (!adding).also { if (it) openAdd(barAdd) } }
    // The detail beside the columns closes first at Esc, but covers nothing: the reading keys move it, and Ctrl+→ reaches it.
    DesktopShortcuts(refresh = { refreshedAll++ }, turn = {}, isOpen = { detail.open }, close = { detail.back() }, readsOpen = { detail.open })
    ReadColumns(columns.map { it.id } + listOfNotNull(DetailColumn.takeIf { detail.open }))
    // What the detail has just opened (a post, a profile, one back) is what the reading keys move next.
    val desk = LocalDesktopActions.current
    val shown = detail.pages.lastOrNull()
    LaunchedEffect(desk, shown, detail.pages.size) { if (shown != null) desk?.readOpened(DetailColumn) }
    // A column asked for from elsewhere (Show in Deck, one just dropped here) is brought into view and chosen, lit a moment.
    val litState = remember { mutableStateOf<String?>(null) }
    var lit by litState
    // Read as the columns are drawn, so choosing a column (Ctrl+← and Ctrl+→) or lighting one redraws its line, not the columns.
    val marked: (Any) -> Boolean = remember(desk) { { id -> desk?.chosen() == id || litState.value == id } }
    val asked = DeckRequests.shown
    LaunchedEffect(asked, columns) {
        val id = asked?.takeIf { wanted -> columns.any { it.id == wanted } } ?: return@LaunchedEffect
        DeckRequests.shownDone(id)
        desk?.chooseColumn(id)
        lit = id
        delay(LIT_MS)
        lit = null
    }
    // Where each column stands in the window, for a timeline dragged here from the account menu.
    val spans = remember { HashMap<String, Pair<Float, Float>>() }
    val ordered = { columns.mapNotNull { column -> spans[column.id]?.let { (left, right) -> Triple(column.id, left, right) } } }
    val deckLeft = remember { FloatArray(1) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Over the empty state's column; over the columns, which run across the window, from edge to edge.
            ColumnTopAppBar(
                column = if (columns.isEmpty()) ReadingColumn.MAX_WIDTH.dp else Dp.Infinity,
                title = { Text(stringResource(Res.string.home_deck)) },
                navigationIcon = {
                    TopBarBack(onBack)
                },
                actions = {
                    IconTip(stringResource(Res.string.home_deck_add)) { IconButton(onClick = { openAdd(barAdd) }, modifier = Modifier.anchoring(barAdd)) { Icon(Icons.Filled.Add, stringResource(Res.string.home_deck_add)) } }
                    WidthMenu(width) { chosen -> scope.launch { settings.set(HomePrefs.deckColumnWidth, chosen) } }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            // Read as it is drawn, so a drag over the deck redraws the line, not the columns.
            val dropLine = {
                DeckRequests.dragged?.at?.let { at -> DeckRequests.landing(at)?.takeIf { it.first.onDeck }?.let { (_, before) -> DeckDrops.lineAt(before, ordered()) } }
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxHeight()
                    .onGloballyPositioned { deckLeft[0] = it.positionInWindow().x }
                    .deckDropTarget(onDeck = true) { at -> DeckDrops.before(at.x, ordered()) }
                    .deckDropLine(dropLine, { deckLeft[0] }, MaterialTheme.colorScheme.primary),
            ) {
                if (columns.isEmpty()) {
                    val emptyAdd = addAnchor.button(ADD_IN_EMPTY)
                    EmptyDeck(Modifier.anchoring(emptyAdd)) { openAdd(emptyAdd) }
                } else {
                    val tally = graph.unread
                    val widths = DeckColumns.widths(maxWidth.value, columns.map { column -> DeckLook.widthOf(options[column.id], width).dp.toFloat() })
                    val scroll = rememberScrollState()
                    // A column's header dragged with the mouse moves it, the deck scrolling at its edges and Esc putting it back.
                    val slots = rememberSlotDrag(scroll, vertical = false)
                    val shownIds: List<Any> = columns.map { it.id }
                    Row(Modifier.fillMaxHeight().slotViewport(slots).horizontalScroll(scroll)) {
                        columns.forEachIndexed { index, column ->
                            key(column.id) {
                                val handle = Modifier.slotDragHandle(slots, column.id, shownIds, mouseOnly = true) { _, to ->
                                    val beside = shownIds[to]
                                    edit { ids -> ids.indexOf(column.id).let { from -> DeckColumns.moved(ids, from, ids.indexOf(beside) - from) } }
                                }
                                val own = options[column.id] ?: DeckColumnOptions()
                                // Made again only when what it does changes, so a column whose header and menu stay the same is not redrawn.
                                val calls = remember(column, index, handle, own) {
                                    ColumnCalls(
                                        onTitle = { toTop[column.id] = (toTop[column.id] ?: 0) + 1 },
                                        onMove = { by -> edit { DeckColumns.moved(it, index, by) } },
                                        onRemove = { scope.launch { graph.sessions.update { it.withoutDeckColumn(column.id) } } },
                                        handle = handle,
                                        onMarkRead = UnreadTally.tabOf(column)?.let { tab -> { tally.markRead(tab) } },
                                        options = own,
                                        onOptions = { chosen -> setOptions(column.id, chosen) },
                                    )
                                }
                                DeckSlot(
                                    column, labelOf(column, names), ColumnPlace(first = index == 0, last = index == columns.lastIndex), calls,
                                    widths[index].dp, toTop, refreshedAll, marked, columnActions, slots, spans,
                                )
                            }
                        }
                    }
                }
            }
            if (detail.open) {
                VerticalDivider()
                Box(Modifier.width(maxOf(width.dp, DETAIL_MIN_WIDTH).dp).fillMaxHeight().chosenMark { desk?.chosen() == DetailColumn }) {
                    ReadColumn(DetailColumn) { DetailView(detail, columnActions) }
                }
            }
        }
    }
    if (adding) {
        AddColumnSheet(AddColumnList.of(columns, names), networks, addAnchor, onDismiss = { adding = false }) { entry ->
            adding = false
            when (val choice = entry.choice) {
                // A column there already is shown, as Show in Deck shows it.
                // A new one is brought into view once it is there, as one dropped here is.
                is AddChoice.Column -> {
                    if (!entry.added) edit { DeckColumns.added(it, choice.column) }
                    DeckRequests.show(choice.column.id)
                }
                is AddChoice.Ask -> asking = choice.ask
            }
        }
    }
    asking?.let { ask ->
        AskColumnDialog(ask, onDismiss = { asking = null }) { column ->
            asking = null
            edit { DeckColumns.added(it, column) }
            DeckRequests.show(column.id)
        }
    }
}

/**
 * The column the reading keys move once Ctrl+← or Ctrl+→ chose it
 * ([ReadColumn]), on the Deck and on Home's wide page ([HomeWide]): a line
 * in the accent colour across its top, and the page scrolled sideways to
 * bring it into view where it scrolls.
 */
@Composable
internal fun Modifier.chosenMark(chosen: Boolean): Modifier {
    val now by rememberUpdatedState(chosen)
    return chosenMark { now }
}

/**
 * [chosenMark] with [chosen] read as the line is drawn: the column's
 * content is not composed again when another column is chosen.
 */
@Composable
internal fun Modifier.chosenMark(chosen: () -> Boolean): Modifier {
    val view = remember { BringIntoViewRequester() }
    val latest by rememberUpdatedState(chosen)
    LaunchedEffect(view) { snapshotFlow { latest() }.collect { if (it) view.bringIntoView() } }
    val color = MaterialTheme.colorScheme.primary
    return then(
        remember(view, color) {
            Modifier.bringIntoViewRequester(view).drawWithContent {
                drawContent()
                if (latest()) drawRect(color, size = Size(size.width, CHOSEN_MARK.toPx()))
            }
        },
    )
}

/** How thick the chosen column's line is. */
private val CHOSEN_MARK = 3.dp

/** Where a column stands among the others: the first moves no further left, the last no further right. */
internal data class ColumnPlace(val first: Boolean, val last: Boolean)

/** The deck's columns and their own choices: all of the session the deck redraws for. */
internal data class DeckState(val columns: List<DeckColumn>, val options: Map<String, DeckColumnOptions>) {
    companion object {
        fun of(session: Session?): DeckState = DeckState(session?.deckColumns.orEmpty(), session?.deckOptions.orEmpty())
    }
}

/**
 * One column on the deck, by the line before it, where the mouse drags it
 * and where it lands. What the deck changes under every column is read here,
 * by the column it concerns alone: its [toTop] count by its id, and whether
 * it is [marked] (chosen, or lit a moment), read as its line is drawn.
 */
@Composable
private fun DeckSlot(
    column: DeckColumn,
    label: String,
    place: ColumnPlace,
    calls: ColumnCalls,
    width: Dp,
    toTop: SnapshotStateMap<String, Int>,
    refreshedAll: Int,
    marked: (Any) -> Boolean,
    actions: PostActions,
    slots: SlotDrag,
    spans: HashMap<String, Pair<Float, Float>>,
) {
    val spanned = Modifier.onGloballyPositioned { spans[column.id] = it.positionInWindow().x.let { left -> left to left + it.size.width } }
    Row(Modifier.fillMaxHeight().then(spanned).dragSlot(slots, column.id, MaterialTheme.colorScheme.primary).background(MaterialTheme.colorScheme.background)) {
        if (!place.first) VerticalDivider()
        ReadColumn(column.id) {
            DeckColumnView(
                column, label, place, calls, toTop[column.id] ?: 0, refreshedAll,
                actions, Modifier.width(width).fillMaxHeight().chosenMark { marked(column.id) },
            )
        }
    }
}

/**
 * What a column's header does: its title back to the top, its menu moving it by a place or taking it off,
 * and the mouse dragging it by the header to another place ([handle]).
 * Home's own columns ([HomeWide]) follow its timelines and have no such [menu].
 */
internal class ColumnCalls(
    val onTitle: () -> Unit,
    val onMove: (by: Int) -> Unit = {},
    val onRemove: () -> Unit = {},
    val menu: Boolean = true,
    val handle: Modifier = Modifier,
    /** The column's Mark all as read; null for a network's, whose read marks are its plugin's. */
    val onMarkRead: (() -> Unit)? = null,
    /** The column's own choices ([DeckColumnOptions]), and the menu's way to change them; null where it has none. */
    val options: DeckColumnOptions = DeckColumnOptions(),
    val onOptions: ((DeckColumnOptions) -> Unit)? = null,
)

/** A column of the deck: its header over its timeline, read again on its own and with every column ([refreshedAll]). */
@Composable
internal fun DeckColumnView(
    column: DeckColumn,
    label: String,
    place: ColumnPlace,
    calls: ColumnCalls,
    toTop: Int,
    refreshedAll: Int,
    actions: PostActions,
    modifier: Modifier,
) {
    var refreshed by remember { mutableIntStateOf(0) }
    val reads = refreshed + refreshedAll
    Column(modifier) {
        when (column) {
            is DeckColumn.Group, DeckColumn.Following -> {
                val vm = groupFeedViewModel((column as? DeckColumn.Group)?.groupId)
                val state by vm.ui.collectAsStateWithLifecycle()
                val options = calls.options
                val keep = remember(options) { options.takeIf { it.filters }?.let { chosen -> chosen::keeps } }
                RefreshesOn(reads) { vm.refresh() }
                ColumnHeader(column, label, place, calls, onRefresh = { refreshed++ })
                GroupFeedBody(state, vm, actions, rememberLazyListState(), PaddingValues(), mediaOnly = options.mediaOnly, toTop = toTop, keep = keep)
            }
            DeckColumn.ForYou -> {
                val vm = forYouViewModel()
                RefreshesOn(reads) { vm.refresh() }
                ColumnHeader(column, label, place, calls, onRefresh = { refreshed++ })
                ForYouPage(toTop, actions, PaddingValues())
            }
            is DeckColumn.Network -> NetworkColumn(column.networkId, label, place, calls, toTop, reads) { refreshed++ }
            is DeckColumn.Search, is DeckColumn.Profile, is DeckColumn.Antenna, is DeckColumn.SavedFolder -> {
                ColumnHeader(column, label, place, calls, onRefresh = { refreshed++ })
                LocalColumnBody(column, actions, reads, toTop)
            }
        }
    }
}

/**
 * A network's timeline as a column: the plugin hands the column what it
 * hands Home's bar, its search and menu beside the column's name and its
 * feeds (or its sections, there being no bottom bar here) under it.
 */
@Composable
private fun NetworkColumn(id: String, label: String, place: ColumnPlace, calls: ColumnCalls, toTop: Int, reads: Int, onRefresh: () -> Unit) {
    val networks = LocalHomeNetworks.current
    val client = remember(id) { PluginClientBar() }
    RefreshesOn(reads) { networks.refresh(id) }
    ColumnHeader(DeckColumn.Network(id), label, place, calls, onRefresh) { PluginActions(client) }
    val rows = ShellRows.of(client.feeds?.feeds, client.content?.tabs?.value.orEmpty(), onBar = false)
    val accent = pluginAccent(client.content?.accent?.value)
    rows.sections?.let { ShellFeedRow(it, null, accent) }
    rows.feeds?.let { ShellFeedRow(it, client.feeds?.filter, accent) }
    CompositionLocalProvider(LocalPluginClientBar provides client) {
        networks.Page(id, PaddingValues(), toTop)
    }
}

/**
 * A column's first row, as Home's bar leads with the timeline's mark: the
 * column's mark ([ColumnMark]) and its name (back to the top), [extra] (a
 * network's own search and menu), Refresh, and its menu. In a column wider
 * than the posts' reading column it stands over the posts, as wide as they
 * are, not across the whole window.
 */
@Composable
private fun ColumnHeader(column: DeckColumn, label: String, place: ColumnPlace, calls: ColumnCalls, onRefresh: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    // A group's emoji stands in the mark's place, ahead of its bare name.
    val (emoji, name) = ColumnTitle.of(column, label)
    Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = ReadingColumn.MAX_WIDTH.dp)) {
        Row(Modifier.fillMaxWidth().then(calls.handle).heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = calls.onTitle).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (emoji != null) Text(emoji, fontSize = 20.sp, maxLines = 1) else ColumnMark(column)
                    }
                    TipText(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (calls.options.unreadCount) ColumnUnread(column)
                }
            }
            extra()
            IconTip(stringResource(Res.string.home_refresh)) { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) } }
            if (calls.menu) ColumnMenu(column, place, calls)
        }
        HorizontalDivider()
    }
}

/** A column's name as its head shows it. */
internal object ColumnTitle {
    /** [column]'s [label] as its head shows it: a group's emoji apart from its name ([split]); any other column's label whole. */
    fun of(column: DeckColumn, label: String): Pair<String?, String> = if (column is DeckColumn.Group) split(label) else null to label

    /** A group's label ([HomeStrip.groupLabel]) as its emoji, null without one, and its name. */
    fun split(label: String): Pair<String?, String> {
        val first = label.substringBefore(' ')
        val rest = label.substringAfter(' ', "")
        return if (rest.isNotBlank() && first.none { it.isLetterOrDigit() }) first to rest else null to label
    }
}

/** A column's mark, as Home's bar draws its timeline's: Following's stacked posts, X's own mark, a network's, a group's people. */
@Composable
private fun ColumnMark(column: DeckColumn) {
    when (column) {
        DeckColumn.Following -> TimelineMark(HomeTab.Following, LocalHomeNetworks.current, 24.dp)
        DeckColumn.ForYou -> TimelineMark(HomeTab.ForYou, LocalHomeNetworks.current, 24.dp)
        is DeckColumn.Network -> LocalHomeNetworks.current.Mark(column.networkId, 24.dp)
        is DeckColumn.Group -> Icon(HomeIcons.PeopleOutlined, contentDescription = null, modifier = Modifier.size(24.dp))
        else -> DeckMarks.of(column)?.let { Icon(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
    }
}

/** The top bar's ⋮: the columns' width, the one in use ticked. */
@Composable
private fun WidthMenu(current: DeckWidth, onPick: (DeckWidth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconTip(stringResource(Res.string.home_more_options)) { IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.home_more_options)) } }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(Res.string.home_deck_width),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DeckWidth.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(widthName(choice))) },
                    trailingIcon = { if (choice == current) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; onPick(choice) },
                )
            }
        }
    }
}

/** No columns yet: what the deck is for, and Add column. */
@Composable
private fun EmptyDeck(button: Modifier, onAdd: () -> Unit) {
    EmptyState(HomeIcons.ViewColumnOutlined, stringResource(Res.string.home_deck_empty)) {
        Button(onClick = onAdd, modifier = button) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.home_deck_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A column's name: Following, For you (X's), the network's, the group's with its emoji, the search, the account, the antenna's or the folder's. */
@Composable
private fun labelOf(column: DeckColumn, names: ColumnNames): String = when (column) {
    DeckColumn.Following -> stringResource(Res.string.following)
    // Named as X names that timeline, the X mark beside it: an X mark beside "X" read as a close button.
    DeckColumn.ForYou -> stringResource(Res.string.home_column_for_you)
    is DeckColumn.Network -> names.available.firstOrNull { it.id == column.networkId }?.title ?: column.networkId
    is DeckColumn.Group -> names.groups.firstOrNull { it.id == column.groupId }?.let(HomeStrip::groupLabel) ?: column.groupId
    else -> names.of(column)
}

/** The Add column buttons, by the part of the deck they stand in: the top bar's +, and the empty deck's. */
private const val ADD_IN_BAR = "bar"
private const val ADD_IN_EMPTY = "empty"

/** [button]'s place followed, and its focus, for the drop-down it opens ([SwitcherButton]). */
private fun Modifier.anchoring(button: SwitcherButton): Modifier =
    onGloballyPositioned { button.bounds = it.boundsInWindow() }.focusRequester(button.focus)

/**
 * Add column's choices ([AddColumnList]): in the desktop's wide frame a
 * drop-down under the button that opened it ([SwitcherPopover]), its rows a
 * pointer menu's; elsewhere (a phone, a narrow window) the sheet. Home's
 * timelines lead, then the networks and the groups under their names, then
 * the rest; the columns there already are ticked. A long list starts with a
 * field: what is typed keeps the rows whose name holds it, and Enter adds
 * the first of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddColumnSheet(entries: List<AddEntry>, networks: HomeNetworks, anchor: SwitcherAnchor, onDismiss: () -> Unit, onPick: (AddEntry) -> Unit) {
    val frame = LocalWideFrame.current
    // Taken once, whether or not it drops down, as the switcher takes its button.
    val button = remember { anchor.take()?.takeIf { frame != null && it.bounds != null } }
    val names = rememberColumnNames(networks)
    var query by remember { mutableStateOf("") }
    val labels = entries.associate { it.key to entryLabel(it, names) }
    val shown = AddColumnList.matching(entries, query) { labels[it.key].orEmpty() }
    val filters = AddColumnList.filters(entries)
    if (button != null) {
        // The bar's + stands at its end: the list opens back across the page, its end under the +.
        SwitcherPopover(anchor, button, onDismiss = onDismiss, atEnd = button === anchor.button(ADD_IN_BAR)) {
            AddColumnTitle(pointer = true)
            if (filters) AddColumnFilter(query, shown, onQuery = { query = it }, onPick = onPick)
            shown.forEachIndexed { index, entry ->
                key(entry.key) {
                    AddSectionHead(entry, shown.getOrNull(index - 1))
                    AddColumnRow(entry, labels[entry.key].orEmpty(), onPick)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        return
    }
    KeyedSheet(sheetState = rememberSheetState(), onDismissRequest = onDismiss) {
        AddColumnTitle(pointer = false)
        if (filters) AddColumnFilter(query, shown, onQuery = { query = it }, onPick = onPick)
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), state = rememberSheetList()) {
            itemsIndexed(shown, key = { _, entry -> entry.key }) { index, entry ->
                Column {
                    AddSectionHead(entry, shown.getOrNull(index - 1))
                    AddColumnRow(entry, labels[entry.key].orEmpty(), onPick)
                }
            }
        }
    }
}

@Composable
private fun AddColumnTitle(pointer: Boolean) {
    Text(
        stringResource(Res.string.home_deck_add),
        style = if (pointer) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
        color = if (pointer) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = if (pointer) Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp) else Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** The field that narrows Add column's rows to [query]; Enter adds the first column left ([AddColumnList.first]). */
@Composable
private fun AddColumnFilter(query: String, shown: List<AddEntry>, onQuery: (String) -> Unit, onPick: (AddEntry) -> Unit) {
    val closer = LocalSheetCloser.current
    // Typing goes into it at once, however the list was opened.
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) {
        repeat(FIELD_FOCUS_TRIES) {
            withFrameNanos {}
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    SearchField(
        value = query,
        onValueChange = onQuery,
        placeholder = stringResource(Res.string.search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus),
        onSearch = { AddColumnList.first(shown)?.let { first -> closer.close { onPick(first) } } },
    )
}

/** The name over a section's first row (the networks, the groups), a line over the rest; none over the first row or within a section. */
@Composable
private fun AddSectionHead(entry: AddEntry, before: AddEntry?) {
    if (before?.section == entry.section) return
    val title = when (entry.section) {
        AddSection.NETWORKS -> Res.string.home_networks
        AddSection.GROUPS -> Res.string.home_groups
        AddSection.TIMELINES, AddSection.MORE -> null
    }
    if (title == null) {
        if (before != null) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        return
    }
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** What a row of Add column is called: its column's name, or the column it asks the reader to name. */
@Composable
private fun entryLabel(entry: AddEntry, names: ColumnNames): String = when (val choice = entry.choice) {
    is AddChoice.Column -> labelOf(choice.column, names)
    is AddChoice.Ask -> stringResource(choice.ask.title)
}

/**
 * One of Add column's choices, led by the mark its column's head will show:
 * a group's emoji, else its people; a tick at its end where it is on the
 * deck already. Denser in a pointer's menu.
 */
@Composable
private fun AddColumnRow(entry: AddEntry, label: String, onPick: (AddEntry) -> Unit) {
    val closer = LocalSheetCloser.current
    val metrics = LocalMenuRowMetrics.current
    val pointer = metrics == MenuRowMetrics.Pointer
    val choice = entry.choice
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (pointer) metrics.minHeight else 56.dp)
            .clickable { closer.close { onPick(entry) } }
            .padding(horizontal = if (pointer) metrics.padding else 24.dp, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (pointer) metrics.gap else 16.dp),
    ) {
        val (emoji, name) = when (choice) {
            is AddChoice.Column -> ColumnTitle.of(choice.column, label)
            is AddChoice.Ask -> null to label
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                emoji != null -> Text(emoji, fontSize = 18.sp, maxLines = 1)
                choice is AddChoice.Column -> ColumnMark(choice.column)
                choice is AddChoice.Ask -> Icon(choice.ask.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        TipText(
            name,
            style = if (pointer) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.added) Icon(Icons.Filled.Check, stringResource(Res.string.home_deck_show_in), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** The detail column's key among the deck's columns ([ReadColumn]), apart from every column's id. */
private object DetailColumn

/** What the detail column shows: a post's conversation or a profile. */
internal sealed interface DeckPage {
    data class Post(val id: String) : DeckPage

    data class Profile(val screenName: String, val userId: String?) : DeckPage
}

/**
 * The deck's detail column: the posts and profiles opened from the columns,
 * the last one shown; back (its arrow, Escape) goes to the one before, and
 * past the first closes the column.
 */
@Stable
internal class DeckDetail {
    val pages = mutableStateListOf<DeckPage>()

    val open: Boolean get() = pages.isNotEmpty()

    fun show(page: DeckPage) {
        if (pages.lastOrNull() != page) pages.add(page)
    }

    fun back() {
        pages.removeLastOrNull()
    }

    /** [base] with the posts and profiles it opens shown here instead of over the deck. */
    fun actionsOver(base: PostActions): PostActions = PostActions(
        openPost = { show(DeckPage.Post(it)) },
        openProfile = { show(DeckPage.Profile(it, null)) },
        openMedia = base.openMedia,
        search = base.search,
        keep = base.keep,
        openQuotes = base.openQuotes,
        openReposters = base.openReposters,
        openArticle = base.openArticle,
        openFollows = base.openFollows,
        openLink = base.openLink,
        openInBrowser = base.openInBrowser,
        openImage = base.openImage,
        openUser = { screenName, userId -> show(DeckPage.Profile(screenName, userId)) },
    )
}

/** The page on top of [detail], each keeping its place while another is shown over it. */
@Composable
private fun DetailView(detail: DeckDetail, actions: PostActions) {
    val page = detail.pages.lastOrNull() ?: return
    val holder = rememberSaveableStateHolder()
    holder.SaveableStateProvider("${detail.pages.size}:$page") {
        when (page) {
            is DeckPage.Post -> PostScreen(page.id, actions, onBack = detail::back)
            is DeckPage.Profile -> ProfileScreen(page.screenName, actions, onBack = detail::back, userId = page.userId, ownKeys = false)
        }
    }
}
