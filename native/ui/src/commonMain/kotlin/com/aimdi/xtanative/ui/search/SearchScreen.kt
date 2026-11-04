package com.aimdi.xtanative.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.format.Formats
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.search.XAdvancedSearch
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.community.CommunityRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_accounts
import com.aimdi.xtanative.ui.generated.resources.discover_advanced_search
import com.aimdi.xtanative.ui.generated.resources.discover_antennas
import com.aimdi.xtanative.ui.generated.resources.discover_clear
import com.aimdi.xtanative.ui.generated.resources.discover_communities
import com.aimdi.xtanative.ui.generated.resources.discover_filter_value
import com.aimdi.xtanative.ui.generated.resources.discover_go_to
import com.aimdi.xtanative.ui.generated.resources.discover_media
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.generated.resources.discover_popular
import com.aimdi.xtanative.ui.generated.resources.discover_recent
import com.aimdi.xtanative.ui.generated.resources.discover_search
import com.aimdi.xtanative.ui.generated.resources.discover_search_x
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import org.jetbrains.compose.resources.stringResource

/** Where the search screen's own rows lead, besides posts and profiles. */
class SearchActions(val openCommunity: (id: String) -> Unit, val openAntennas: () -> Unit)

/**
 * XTA's search of X: the field (with the advanced search behind its filter
 * button), Popular, Recent, Media, Accounts and Communities; recent searches
 * until something is searched, and the filters set as chips above the results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(initial: String?, initialTab: SearchTab, actions: PostActions, searchActions: SearchActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: SearchViewModel = viewModel { SearchViewModel(graph, initial, initialTab) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    LaunchedEffect(state.textSet) {
        if (field.text != state.text) field = TextFieldValue(state.text, TextRange(state.text.length))
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (initial.isNullOrBlank()) focus.requestFocus() }
    val searchField = @Composable { modifier: Modifier ->
        SearchField(
            value = field,
            onValueChange = {
                field = it
                vm.type(it.text)
            },
            filters = state.view.advanced.activeFilters.size,
            onSearch = {
                vm.submit()
                focusManager.clearFocus()
            },
            onClear = {
                vm.clear()
                focus.requestFocus()
            },
            onAdvanced = vm::openAdvanced,
            modifier = modifier.focusRequester(focus),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SearchHeader(
                hasQuery = state.view.hasQuery,
                query = state.view.query,
                tab = state.tab,
                onTab = vm::select,
                openAntennas = searchActions.openAntennas,
                onBack = onBack,
                field = searchField,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Formats.handleOf(field.text)?.let { handle -> GoToRow(handle) { actions.openProfile(handle) } }
            if (!state.view.hasQuery) {
                RecentSearchesBar(
                    recent,
                    onSelect = { query ->
                        vm.searchAgain(query)
                        focusManager.clearFocus()
                    },
                    onRemove = vm::removeRecent,
                    onClear = vm::clearRecent,
                )
                StartState()
            } else {
                FilterStrip(SearchFilters.chips(state.view.advanced)) { filter ->
                    vm.clearFilter(filter)
                    focusManager.clearFocus()
                }
                Results(vm, state.tab, actions, searchActions, PaddingValues(bottom = padding.calculateBottomPadding()))
            }
        }
    }
    state.editing?.let { filters ->
        AdvancedSearchDialog(
            filters = filters,
            onChange = vm::edit,
            onApply = {
                vm.applyAdvanced()
                focusManager.clearFocus()
            },
            onDismiss = vm::closeAdvanced,
        )
    }
}

/**
 * The bar: on a narrow screen or at large text, "Search X" over the field,
 * as XTA lays it out; wider, the field in the title. The tabs under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHeader(
    hasQuery: Boolean,
    query: String,
    tab: SearchTab,
    onTab: (SearchTab) -> Unit,
    openAntennas: () -> Unit,
    onBack: () -> Unit,
    field: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints {
        val separate = maxWidth < 600.dp || LocalDensity.current.fontScale >= 1.3f
        Column {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                title = {
                    if (separate) {
                        Text(stringResource(Res.string.discover_search_x), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        field(Modifier.fillMaxWidth().padding(end = 8.dp))
                    }
                },
                actions = {
                    IconButton(onClick = openAntennas) { Icon(DiscoverIcons.Sensors, stringResource(Res.string.discover_antennas)) }
                    if (hasQuery) FollowMenu(FollowTarget.Search(query))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            if (separate) field(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            SearchTabs(tab, onTab)
        }
    }
}

@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    filters: Int,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onAdvanced: () -> Unit,
    modifier: Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.discover_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(Res.string.discover_clear)) }
                }
                AdvancedButton(filters, onAdvanced)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier,
    )
}

/** The advanced search, with how many filters are set on it. */
@Composable
private fun AdvancedButton(filters: Int, onClick: () -> Unit) {
    val label = stringResource(Res.string.discover_advanced_search)
    IconButton(onClick = onClick) {
        if (filters > 0) {
            BadgedBox(badge = { Badge { Text(filters.toString()) } }) {
                Icon(DiscoverIcons.Tune, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Icon(DiscoverIcons.Tune, contentDescription = label)
        }
    }
}

@Composable
private fun SearchTabs(selected: SearchTab, onSelect: (SearchTab) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selected.ordinal,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.background,
        divider = { Hairline() },
    ) {
        SearchTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tabName(tab)), maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun tabName(tab: SearchTab) = when (tab) {
    SearchTab.TOP -> Res.string.discover_popular
    SearchTab.LATEST -> Res.string.discover_recent
    SearchTab.MEDIA -> Res.string.discover_media
    SearchTab.PEOPLE -> Res.string.discover_accounts
    SearchTab.COMMUNITIES -> Res.string.discover_communities
}

/** The tabs' results, swiped between as XTA's tabs are; each reads the search when it is first shown. */
@Composable
private fun Results(vm: SearchViewModel, tab: SearchTab, actions: PostActions, searchActions: SearchActions, padding: PaddingValues) {
    // Each tab keeps its place while another is shown.
    val lists = SearchTab.entries.associateWith { rememberLazyListState() }
    val pager = rememberPagerState(initialPage = tab.ordinal) { SearchTab.entries.size }
    LaunchedEffect(tab) {
        if (pager.currentPage != tab.ordinal) pager.animateScrollToPage(tab.ordinal)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.select(SearchTab.entries[it]) }
    }
    HorizontalPager(state = pager, key = { SearchTab.entries[it].name }, modifier = Modifier.fillMaxSize()) { page ->
        val shown = SearchTab.entries[page]
        val list = lists.getValue(shown)
        val retry = { vm.retry(shown) }
        val more = { vm.more(shown) }
        when (shown) {
            SearchTab.TOP -> PostResults(vm.top, list, actions, retry, more, padding)
            SearchTab.LATEST -> PostResults(vm.latest, list, actions, retry, more, padding)
            SearchTab.MEDIA -> MediaResults(vm, list, actions, retry, more, padding)
            SearchTab.PEOPLE -> PeopleResults(vm, list, actions, retry, more, padding)
            SearchTab.COMMUNITIES -> CommunityResults(vm, list, searchActions, retry, more, padding)
        }
    }
}

@Composable
private fun PostResults(read: PagedRead<XThread, String>, list: LazyListState, actions: PostActions, onRetry: () -> Unit, onMore: () -> Unit, padding: PaddingValues) {
    val paged by read.state.collectAsStateWithLifecycle()
    LoadMoreNearEnd(list, paged, onMore)
    LaunchedEffect(paged.loading) { if (paged.loading) list.scrollToItem(0) }
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        items(paged.items, key = { it.id }, contentType = { "thread" }) { thread -> ThreadRows(thread, actions) }
        pageStatus(paged, Res.string.discover_no_results, onRetry, onMore)
    }
}

@Composable
private fun MediaResults(vm: SearchViewModel, list: LazyListState, actions: PostActions, onRetry: () -> Unit, onMore: () -> Unit, padding: PaddingValues) {
    val paged by vm.media.state.collectAsStateWithLifecycle()
    val shape by vm.mediaShape.collectAsStateWithLifecycle()
    LoadMoreNearEnd(list, paged, onMore)
    LaunchedEffect(paged.loading) { if (paged.loading) list.scrollToItem(0) }
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        mediaRows(paged.items, shape) { tile -> actions.openMedia(tile.postId, tile.index) }
        pageStatus(paged, Res.string.discover_no_results, onRetry, onMore)
    }
}

@Composable
private fun PeopleResults(vm: SearchViewModel, list: LazyListState, actions: PostActions, onRetry: () -> Unit, onMore: () -> Unit, padding: PaddingValues) {
    val paged by vm.people.state.collectAsStateWithLifecycle()
    LaunchedEffect(paged.loading) { if (paged.loading) list.scrollToItem(0) }
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(paged.items, key = { _, user -> user.id }, contentType = { _, _ -> "person" }) { index, user ->
            PersonRow(user, onOpen = { actions.openProfile(user.screenName) })
            if (index < paged.items.lastIndex) PersonDivider()
        }
        pageStatus(paged, Res.string.discover_no_results, onRetry, onMore, icon = DiscoverIcons.PersonSearch)
    }
}

@Composable
private fun CommunityResults(vm: SearchViewModel, list: LazyListState, searchActions: SearchActions, onRetry: () -> Unit, onMore: () -> Unit, padding: PaddingValues) {
    val paged by vm.communities.state.collectAsStateWithLifecycle()
    LoadMoreNearEnd(list, paged, onMore)
    LaunchedEffect(paged.loading) { if (paged.loading) list.scrollToItem(0) }
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        items(paged.items, key = { it.id }, contentType = { "community" }) { community ->
            CommunityRow(community) { searchActions.openCommunity(community.id) }
        }
        pageStatus(paged, Res.string.discover_no_results, onRetry, onMore, icon = DiscoverIcons.Groups)
    }
}

/** The filters set, as chips above the results: a tap takes one away and searches again without it. */
@Composable
private fun FilterStrip(chips: List<FilterChip>, onRemove: (XAdvancedSearch.Filter) -> Unit) {
    AnimatedVisibility(visible = chips.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips, key = { it.filter.name }) { chip ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(chip.filter) },
                    label = { Text(chipText(chip), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp)) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.discover_clear), modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun chipText(chip: FilterChip): String {
    val label = filterLabel(chip.label)
    return if (chip.value.isEmpty()) label else stringResource(Res.string.discover_filter_value, label, chip.value)
}

/** Nothing searched yet. */
@Composable
private fun StartState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(
            stringResource(Res.string.discover_search_x),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** A profile by its handle: it opens without a search, which needs an X account. */
@Composable
private fun GoToRow(handle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.discover_go_to, handle), modifier = Modifier.padding(start = 16.dp))
    }
}
