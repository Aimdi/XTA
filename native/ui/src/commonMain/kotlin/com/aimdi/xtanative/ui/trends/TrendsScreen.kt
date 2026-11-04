package com.aimdi.xtanative.ui.trends

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.home.HomeRequests
import com.aimdi.xtanative.ui.home.RetapsToTop
import com.aimdi.xtanative.core.x.model.XTrend
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrendPlaces
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_add_location
import com.aimdi.xtanative.ui.generated.resources.discover_antennas
import com.aimdi.xtanative.ui.generated.resources.discover_clear
import com.aimdi.xtanative.ui.generated.resources.discover_delete
import com.aimdi.xtanative.ui.generated.resources.discover_find_people
import com.aimdi.xtanative.ui.generated.resources.discover_plugin_empty
import com.aimdi.xtanative.ui.generated.resources.discover_search
import com.aimdi.xtanative.ui.generated.resources.discover_trend_posts
import com.aimdi.xtanative.ui.generated.resources.discover_trends_empty
import com.aimdi.xtanative.ui.generated.resources.saved_search_library
import com.aimdi.xtanative.ui.generated.resources.saved_source_x
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.search.EmptyNote
import com.aimdi.xtanative.ui.search.RecentSearchesBar
import com.aimdi.xtanative.ui.search.SearchablePlugin
import com.aimdi.xtanative.ui.search.searchablePlugins
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where Discover leads: a search of X ([openSearch] with a query, [openPeople]
 * on the Accounts tab), the antennas and the search of the reader's library.
 */
class TrendsActions(
    val openSearch: (query: String) -> Unit,
    val openPeople: () -> Unit,
    val openAntennas: () -> Unit,
    val openLibrary: (query: String) -> Unit,
)

/**
 * XTA's Discover: the search bar with the recent searches and the search of
 * the reader's library; for X, shortcuts to people and antennas, the trends
 * of the place picked, the places kept as tabs (a long press deletes one) and
 * Add a location; and, once a plugin that searches is installed, a chip for
 * X and each such plugin, whose results then show under the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(actions: TrendsActions, onBack: (() -> Unit)? = null) {
    val graph = LocalGraph.current
    val vm: TrendsViewModel = viewModel { TrendsViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    LaunchedEffect(state.textSet) {
        if (field.text != state.text) field = TextFieldValue(state.text, TextRange(state.text.length))
    }
    val plugins = searchablePlugins()
    val scope = DiscoverScopes.effective(state.scope, plugins.map { it.id })
    // The plugin whose chip was picked is gone: the bar searches X again.
    LaunchedEffect(scope) { if (scope != state.scope) vm.select(scope) }
    val plugin = plugins.firstOrNull { it.id == scope }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val list = rememberLazyListState()
    // Home's bar tapped again here: the trends to the top, and from there the cursor into the search.
    RetapsToTop(list) { focus.requestFocus() }
    // Reached from a network's page on Home's bar, Discover searches that network (_adoptSearchScope).
    val askedScope by HomeRequests.searchScope.collectAsStateWithLifecycle()
    LaunchedEffect(askedScope) {
        val id = askedScope ?: return@LaunchedEffect
        HomeRequests.searchScopeTaken(id)
        if (plugins.any { it.id == id }) vm.select(id)
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    // An empty bar takes focus instead of searching for nothing; a plugin's results show under the bar.
    val searched = { query: String? ->
        if (query == null) {
            focus.requestFocus()
        } else {
            focusManager.clearFocus()
            if (DiscoverScopes.opensXSearch(scope)) actions.openSearch(query)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                        }
                    },
                    title = {
                        DiscoverField(
                            value = field,
                            hint = plugin?.hint(currentLanguage()) ?: stringResource(Res.string.discover_search),
                            onValueChange = {
                                field = it
                                vm.type(it.text)
                            },
                            onSearch = { searched(vm.submit()) },
                            onClear = {
                                vm.clear()
                                focus.requestFocus()
                            },
                            onLibrary = { actions.openLibrary(field.text) },
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (plugin == null) PlaceTabs(state.places, onShow = vm::show, onRemove = vm::remove)
            }
        },
        floatingActionButton = {
            if (plugin == null) {
                FloatingActionButton(onClick = { picking = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.discover_add_location))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (plugin == null) DiscoverShortcuts(actions)
            ScopeChips(plugins, scope, onSelect = vm::select)
            if (state.committed.isEmpty()) {
                RecentSearchesBar(
                    recent,
                    onSelect = { query -> searched(vm.searchAgain(query)) },
                    onRemove = vm::removeRecent,
                    onClear = vm::clearRecent,
                )
            }
            if (plugin == null) {
                TrendsBody(state, list, onRefresh = vm::refresh, onTrend = { trend -> actions.openSearch(trend.query) }, onAddLocation = { picking = true }, bottom = padding.calculateBottomPadding())
            } else {
                PluginPane(plugin, state.committed, bottom = padding.calculateBottomPadding())
            }
        }
    }
    if (picking) PlacesSheet(vm, state.places, onDismiss = { picking = false })
}

/** The bar: [hint] says what it searches; the magnifier searches, the cross clears it, and the library button searches the reader's library. */
@Composable
private fun DiscoverField(
    value: TextFieldValue,
    hint: String,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.discover_search)) } },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.text.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(Res.string.discover_clear)) }
                IconButton(onClick = onLibrary) { Icon(SavedIcons.ManageSearch, stringResource(Res.string.saved_search_library)) }
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

/** The network the bar searches: X, then each installed plugin that searches; nothing while none does. */
@Composable
private fun ScopeChips(plugins: List<SearchablePlugin>, scope: String, onSelect: (String) -> Unit) {
    val chips = DiscoverScopes.chips(plugins.map { it.id })
    if (chips.isEmpty()) return
    val language = currentLanguage()
    val x = stringResource(Res.string.saved_source_x)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { id ->
            val label = plugins.firstOrNull { it.id == id }?.title(language) ?: x
            FilterChip(selected = id == scope, onClick = { onSelect(id) }, label = { Text(label, maxLines = 1) })
        }
    }
}

/** A plugin's search under the bar (discover_plugin_search.dart): what it searches until something is typed, then what it finds. */
@Composable
private fun PluginPane(plugin: SearchablePlugin, query: String, bottom: Dp) {
    if (DiscoverScopes.body(plugin.id, query) == DiscoverBody.PLUGIN_SEARCH) {
        Box(Modifier.fillMaxSize()) { plugin.search.Results(query, PluginFrame(PaddingValues(bottom = bottom))) }
    } else {
        EmptyNote(stringResource(Res.string.discover_plugin_empty, plugin.title(currentLanguage())), icon = plugin.plugin.icon)
    }
}

/** The places kept, one tab each; a long press on one but the world offers Delete. */
@Composable
private fun PlaceTabs(places: XTrendPlaces, onShow: (XTrendPlace) -> Unit, onRemove: (XTrendPlace) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = places.activeIndex,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.background,
        divider = { Hairline() },
    ) {
        places.places.forEach { place ->
            PlaceTab(place, TrendPlacesLogic.isShown(places, place), onShow, onRemove)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceTab(place: XTrendPlace, selected: Boolean, onShow: (XTrendPlace) -> Unit, onRemove: (XTrendPlace) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val removable = TrendPlacesLogic.canRemove(place)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.Tab, onLongClick = if (removable) ({ menu = true }) else null) { onShow(place) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            place.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.discover_delete)) },
                onClick = {
                    menu = false
                    onRemove(place)
                },
            )
        }
    }
}

/** Ways off Discover besides the bar: people, and antennas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverShortcuts(actions: TrendsActions) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = actions.openPeople,
            label = { Text(stringResource(Res.string.discover_find_people)) },
            leadingIcon = { Icon(DiscoverIcons.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = actions.openAntennas,
            label = { Text(stringResource(Res.string.discover_antennas)) },
            leadingIcon = { Icon(DiscoverIcons.Sensors, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

/** The trends, ranked, pulled down to read again; a tap searches one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendsBody(state: TrendsUi, list: LazyListState, onRefresh: () -> Unit, onTrend: (XTrend) -> Unit, onAddLocation: () -> Unit, bottom: Dp) {
    val problem = state.problem
    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            problem != null -> Whole { ProblemView(problem, onRefresh) }
            !state.loaded -> Whole { LoadingRow() }
            state.trends.isEmpty() -> Whole { EmptyTrends(onAddLocation) }
            // Room under the last trend for the button over it.
            else -> LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottom + 88.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.trends, key = { index, trend -> "$index-${trend.name}" }, contentType = { _, _ -> "trend" }) { index, trend ->
                    TrendRow(index + 1, trend) { onTrend(trend) }
                }
            }
        }
    }
}

@Composable
private fun TrendRow(rank: Int, trend: XTrend, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(trend.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            trend.volume?.let { volume ->
                Text(
                    pluralStringResource(Res.plurals.discover_trend_posts, volume.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), countText(volume)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyTrends(onAddLocation: () -> Unit) {
    Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(DiscoverIcons.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(
            stringResource(Res.string.discover_trends_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onAddLocation) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.discover_add_location), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A whole-screen state that can still be pulled to refresh. */
@Composable
private fun Whole(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
