package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.DeckColumns
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.settings.DeckWidth
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LocalSettingsStore
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
import com.aimdi.xtanative.ui.generated.resources.home_x
import com.aimdi.xtanative.ui.group.GroupFeedBody
import com.aimdi.xtanative.ui.group.groupFeedViewModel
import com.aimdi.xtanative.ui.plugins.LocalPluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginActions
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ShellFeedRow
import com.aimdi.xtanative.ui.plugins.pluginAccent
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScreen
import com.aimdi.xtanative.ui.profile.ProfileScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
 * what the detail column shows, one page at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val columns = session?.deckColumns.orEmpty()
    val groups = session?.groups.orEmpty()
    val networks = LocalHomeNetworks.current
    val available by networks.available.collectAsStateWithLifecycle()
    val settings = LocalSettingsStore.current
    val width by remember(settings) { settings.observe(HomePrefs.deckColumnWidth) }.collectAsStateWithLifecycle(DeckWidth.MEDIUM)
    val scope = rememberCoroutineScope()
    val edit: (change: (List<String>) -> List<String>) -> Unit = { change -> scope.launch { graph.sessions.update { it.copy(deck = change(it.deck)) } } }
    val detail = remember { DeckDetail() }
    val columnActions = remember(actions, detail) { detail.actionsOver(actions) }
    val toTop = remember { mutableStateMapOf<String, Int>() }
    var refreshedAll by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    DesktopShortcuts(refresh = { refreshedAll++ }, turn = {}, isOpen = { detail.open }, close = { detail.back() })
    ReadColumns(columns.map { it.id })
    val chosen = chosenReadColumn()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.home_deck)) },
                navigationIcon = {
                    IconTip(stringResource(Res.string.back)) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } }
                },
                actions = {
                    IconTip(stringResource(Res.string.home_deck_add)) { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(Res.string.home_deck_add)) } }
                    WidthMenu(width) { chosen -> scope.launch { settings.set(HomePrefs.deckColumnWidth, chosen) } }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                if (columns.isEmpty()) {
                    EmptyDeck { adding = true }
                } else {
                    val each = DeckColumns.width(maxWidth.value, columns.size, width.dp.toFloat()).dp
                    Row(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
                        columns.forEachIndexed { index, column ->
                            if (index > 0) VerticalDivider()
                            key(column.id) {
                                val place = ColumnPlace(first = index == 0, last = index == columns.lastIndex)
                                val calls = ColumnCalls(
                                    onTitle = { toTop[column.id] = (toTop[column.id] ?: 0) + 1 },
                                    onMove = { by -> edit { DeckColumns.moved(it, index, by) } },
                                    onRemove = { edit { ids -> ids - column.id } },
                                )
                                ReadColumn(column.id) {
                                    DeckColumnView(
                                        column, labelOf(column, groups, available), place, calls, toTop[column.id] ?: 0, refreshedAll,
                                        columnActions, Modifier.width(each).fillMaxHeight().chosenMark(chosen == column.id),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (detail.open) {
                VerticalDivider()
                Box(Modifier.width(maxOf(width.dp, DETAIL_MIN_WIDTH).dp).fillMaxHeight()) { DetailView(detail, columnActions) }
            }
        }
    }
    if (adding) {
        AddColumnSheet(addable(columns, groups, available), networks, onDismiss = { adding = false }) { column ->
            adding = false
            edit { DeckColumns.added(it, column) }
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
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(chosen) { if (chosen) view.bringIntoView() }
    val color = MaterialTheme.colorScheme.primary
    return bringIntoViewRequester(view).drawWithContent {
        drawContent()
        if (chosen) drawRect(color, size = Size(size.width, CHOSEN_MARK.toPx()))
    }
}

/** How thick the chosen column's line is. */
private val CHOSEN_MARK = 3.dp

/** Where a column stands among the others: the first moves no further left, the last no further right. */
internal class ColumnPlace(val first: Boolean, val last: Boolean)

/**
 * What a column's header does: its title back to the top, its menu moving it by a place or taking it off.
 * Home's own columns ([HomeWide]) follow its timelines and have no such [menu].
 */
internal class ColumnCalls(val onTitle: () -> Unit, val onMove: (by: Int) -> Unit = {}, val onRemove: () -> Unit = {}, val menu: Boolean = true)

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
                RefreshesOn(reads) { vm.refresh() }
                ColumnHeader(label, place, calls, onRefresh = { refreshed++ })
                GroupFeedBody(state, vm, actions, rememberLazyListState(), PaddingValues(), mediaOnly = false, toTop = toTop)
            }
            DeckColumn.ForYou -> {
                val vm = forYouViewModel()
                RefreshesOn(reads) { vm.refresh() }
                ColumnHeader(label, place, calls, onRefresh = { refreshed++ })
                ForYouPage(toTop, actions, PaddingValues())
            }
            is DeckColumn.Network -> NetworkColumn(column.networkId, label, place, calls, toTop, reads) { refreshed++ }
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
    ColumnHeader(label, place, calls, onRefresh) { PluginActions(client) }
    val rows = ShellRows.of(client.feeds?.feeds, client.content?.tabs?.value.orEmpty(), onBar = false)
    val accent = pluginAccent(client.content?.accent?.value)
    rows.sections?.let { ShellFeedRow(it, null, accent) }
    rows.feeds?.let { ShellFeedRow(it, client.feeds?.filter, accent) }
    CompositionLocalProvider(LocalPluginClientBar provides client) {
        networks.Page(id, PaddingValues(), toTop)
    }
}

/** A column's first row: its name (back to the top), [extra] (a network's own search and menu), Refresh, and its menu. */
@Composable
private fun ColumnHeader(label: String, place: ColumnPlace, calls: ColumnCalls, onRefresh: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = calls.onTitle).padding(horizontal = 12.dp, vertical = 12.dp),
        )
        extra()
        IconTip(stringResource(Res.string.home_refresh)) { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) } }
        if (calls.menu) ColumnMenu(place, calls)
    }
    HorizontalDivider()
}

@Composable
private fun ColumnMenu(place: ColumnPlace, calls: ColumnCalls) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconTip(stringResource(Res.string.home_more_options)) { IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.home_more_options)) } }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.home_deck_move_left)) },
                enabled = !place.first,
                onClick = { open = false; calls.onMove(-1) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.home_deck_move_right)) },
                enabled = !place.last,
                onClick = { open = false; calls.onMove(1) },
            )
            DropdownMenuItem(text = { Text(stringResource(Res.string.home_deck_remove)) }, onClick = { open = false; calls.onRemove() })
        }
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
                    text = { Text(stringResource(widthLabel(choice))) },
                    trailingIcon = { if (choice == current) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; onPick(choice) },
                )
            }
        }
    }
}

private fun widthLabel(width: DeckWidth): StringResource = when (width) {
    DeckWidth.NARROW -> Res.string.home_deck_width_narrow
    DeckWidth.MEDIUM -> Res.string.home_deck_width_medium
    DeckWidth.WIDE -> Res.string.home_deck_width_wide
}

/** No columns yet: what the deck is for, and Add column. */
@Composable
private fun EmptyDeck(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(Res.string.home_deck_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.home_deck_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A column's name: Following, For you, the network's, or the group's with its emoji. */
@Composable
private fun labelOf(column: DeckColumn, groups: List<FollowGroup>, available: List<StripNetwork>): String = when (column) {
    DeckColumn.Following -> stringResource(Res.string.following)
    DeckColumn.ForYou -> stringResource(Res.string.home_x)
    is DeckColumn.Network -> available.firstOrNull { it.id == column.networkId }?.title ?: column.networkId
    is DeckColumn.Group -> groups.firstOrNull { it.id == column.groupId }?.let(HomeStrip::groupLabel) ?: column.groupId
}

/** What Add column offers: Home's timelines, every network running, then every group, less the columns already on the deck. */
private fun addable(columns: List<DeckColumn>, groups: List<FollowGroup>, available: List<StripNetwork>): List<DeckColumn> {
    val all = listOf(DeckColumn.Following, DeckColumn.ForYou) +
        available.filter { it.id != FeedStrip.FOLLOWING && it.id != FeedStrip.FOR_YOU }.map { DeckColumn.Network(it.id) } +
        groups.map { DeckColumn.Group(it.id) }
    return all.filterNot { it in columns }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddColumnSheet(choices: List<DeckColumn>, networks: HomeNetworks, onDismiss: () -> Unit, onPick: (DeckColumn) -> Unit) {
    val session by LocalGraph.current.sessions.state.collectAsStateWithLifecycle()
    val available by networks.available.collectAsStateWithLifecycle()
    val groups = session?.groups.orEmpty()
    ModalBottomSheet(sheetState = rememberSheetState(), onDismissRequest = onDismiss) {
        Text(
            stringResource(Res.string.home_deck_add),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(choices, key = { it.id }) { column ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(column) }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        (column as? DeckColumn.Network)?.let { networks.Mark(it.networkId, 24.dp) }
                    }
                    Text(labelOf(column, groups, available), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

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
