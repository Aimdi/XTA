package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackArchive
import com.aimdi.xtanative.networks.substack.SubstackFilters
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackOrder
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.networks.substack.SubstackPublicationFilter
import com.aimdi.xtanative.networks.substack.SubstackRecommendation
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues

/**
 * A publication's page as XTA's SubstackPublicationStore runs it: its archive
 * page by page, its archive searched (a moment after typing stops) and paged
 * on its own, the loaded posts filtered and ordered here, and its header
 * filled in from its own site when what is known of it is thin.
 */
internal class PublicationModel(private val app: SubstackApp, opened: SubstackPublication, private val scope: CoroutineScope) {
    data class State(
        val publication: SubstackPublication,
        val archive: SubstackArchive.State = SubstackArchive.State(loading = true),
        val search: SubstackArchive.State = SubstackArchive.State(),
        val query: String = "",
        val filter: SubstackPublicationFilter = SubstackPublicationFilter.ALL,
        val order: SubstackOrder = SubstackOrder.NEWEST,
        val expanded: Boolean = false,
    ) {
        val page: SubstackArchive.State get() = if (query.isEmpty()) archive else search
    }

    private val mutable = MutableStateFlow(State(opened))
    val state: StateFlow<State> = mutable.asStateFlow()
    private val archive = SubstackArchive.of(app.client, opened)
    private var search: SubstackArchive? = null
    private var searching: Job? = null

    init {
        refresh()
        scope.launch { enrich() }
    }

    fun filter(filter: SubstackPublicationFilter) = mutable.update { it.copy(filter = filter) }

    fun order(order: SubstackOrder) = mutable.update { it.copy(order = order) }

    fun toggleDescription() = mutable.update { it.copy(expanded = !it.expanded) }

    /** Searches the archive for [input] a moment after the last keystroke; an empty one shows the archive again. */
    fun search(input: String) {
        val query = input.trim()
        if (query == state.value.query) return
        searching?.cancel()
        search = null
        mutable.update { it.copy(query = query, search = SubstackArchive.State(loading = query.isNotEmpty())) }
        if (query.isEmpty()) return
        searching = scope.launch {
            delay(DEBOUNCE_MS)
            val found = SubstackArchive.search(app.client, state.value.publication, query).also { search = it }
            val result = found.first()
            if (search === found) mutable.update { it.copy(search = result) }
        }
    }

    fun refresh() = page { it.first() }

    fun loadMore() = page { it.more() }

    fun retry() = page { it.retry() }

    private fun page(read: suspend (SubstackArchive) -> SubstackArchive.State) {
        val searched = state.value.query.isNotEmpty()
        val pages = if (searched) search ?: return else archive
        if (searched) mutable.update { it.copy(search = it.search.copy(loading = true)) } else mutable.update { it.copy(archive = it.archive.copy(loading = true)) }
        scope.launch {
            val result = read(pages)
            if (pages === archive) mutable.update { it.copy(archive = result) } else if (search === pages) mutable.update { it.copy(search = result) }
        }
    }

    private suspend fun enrich() {
        val fresh = try {
            app.client.enrich(state.value.publication)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        mutable.update { it.copy(publication = fresh) }
        if (app.followOf(fresh) != null) app.refreshFollow(fresh)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}

/**
 * A publication's page, as XTA's SubstackArchiveScreen: who it is, Follow
 * (on this device), Find similar and Pin; its archive searched, filtered and
 * ordered; each post's card. Nothing is subscribed to on Substack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubstackPublicationScreen(app: SubstackApp, opened: SubstackPublication) {
    val model = rememberKept("substack-publication-${opened.baseUrl}") { scope -> PublicationModel(app, app.publicationFor(opened), scope) }
    val state by model.state.collectAsState()
    val marks = rememberMarks(app)
    var similar by rememberSaveable { mutableStateOf(false) }
    val page = state.page
    val posts = SubstackFilters.publicationPosts(page.posts, state.filter, state.order, marks.readIds)
    val error = page.error
    PluginScaffold(title = state.publication.displayName, onBack = app.host::back) { padding ->
        PullToRefreshBox(isRefreshing = page.loading && page.posts.isNotEmpty(), onRefresh = model::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "hero") { Hero(app, model, state) { similar = true } }
                item(key = "controls") { PublicationControls(model, state) }
                when {
                    error != null && page.posts.isEmpty() -> item(key = "problem") { FailurePane(error, model::retry, state.publication.baseUrl) }
                    page.loading && page.posts.isEmpty() -> item(key = "loading") { LoadingRow() }
                    posts.isEmpty() -> item(key = "empty") { EmptyPublication(model, state.filter) }
                }
                if (error != null && !page.failedMore && page.posts.isNotEmpty()) item(key = "refresh-failed") { FailureRow(error, model::retry, page = state.publication.baseUrl) }
                items(posts, key = { "post-" + it.publicationBaseUrl + "/" + it.id.ifEmpty { it.slug } }) { post ->
                    SubstackPostCard(app, post, marks, state.publication.logoUrl)
                }
                item(key = "end") {
                    when {
                        error != null && page.failedMore -> FailureRow(error, model::retry, page = state.publication.baseUrl)
                        error == null && page.canLoadMore -> LoadMoreRow(false, null, true, model::loadMore, enabled = !page.loading)
                    }
                    Spacer(Modifier.padding(bottom = 32.dp))
                }
            }
        }
    }
    if (similar) SimilarSheet(app, state.publication) { similar = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(app: SubstackApp, model: PublicationModel, state: PublicationModel.State, onSimilar: () -> Unit) {
    val publication = state.publication
    val description = publication.description?.trim().orEmpty()
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Identity(publication)
        if (description.isNotEmpty()) {
            val long = description.length > LONG_DESCRIPTION
            SelectionContainer {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (state.expanded || !long) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (long) TextButton(onClick = model::toggleDescription) { Text(pluginString(if (state.expanded) Strings.show_less else Strings.show_more)) }
        }
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FollowButton(app, publication)
            GroupButton(app, publication)
            OutlinedButton(onClick = onSimilar) {
                Icon(SubstackIcons.Similar, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.similar))
            }
            PinButton(app, publication)
        }
    }
}

/** Its logo beside its name and site, or above them on a narrow screen or at large text, as XTA's _identity. */
@Composable
private fun Identity(publication: SubstackPublication) {
    val logo = @Composable { PublicationLogo(publication.logoUrl, 64.dp, shape = RoundedCornerShape(12.dp)) }
    val title = @Composable {
        Column {
            Text(publication.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            SelectionContainer { Text(SubstackHomeRules.hostOf(publication), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
    val largeText = LocalDensity.current.fontScale > STACKED_TEXT_SCALE
    BoxWithConstraints {
        if (maxWidth < STACKED_WIDTH || largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                logo()
                title()
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                logo()
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) { title() }
            }
        }
    }
}

/** Follows or unfollows [publication] on this device, saying which it is now. */
@Composable
internal fun FollowButton(app: SubstackApp, publication: SubstackPublication) {
    val follows by app.publications.collectAsState()
    val followed = follows?.let { app.followOf(publication) } != null
    FilledTonalButton(onClick = { if (followed) app.unfollow(publication) else app.follow(publication) }) {
        Icon(if (followed) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluginString(if (followed) Strings.unfollow else Strings.follow))
    }
}

@Composable
private fun PinButton(app: SubstackApp, publication: SubstackPublication) {
    val follows by app.publications.collectAsState()
    val pins by app.pins.state.collectAsState()
    val follow = follows?.let { app.followOf(publication) } ?: return
    val pinned = follow.id in pins
    OutlinedButton(onClick = { app.launch { app.pins.toggle(follow.id) } }) {
        Icon(if (pinned) SubstackIcons.PushPin else SubstackIcons.PushPinOutlined, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluginString(if (pinned) Strings.unpin else Strings.pin))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicationControls(model: PublicationModel, state: PublicationModel.State) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var sorting by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; model.search(it) },
            placeholder = { Text(pluginString(Strings.search_publication)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isEmpty()) null else ({ IconButton(onClick = { query = ""; model.search("") }) { Icon(Icons.Default.Clear, pluginString(Strings.clear_search)) } }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubstackPublicationFilter.entries.forEach { filter ->
                FilterChip(selected = state.filter == filter, onClick = { model.filter(filter) }, label = { Text(pluginString(publicationFilterLabel(filter))) })
            }
        }
        Box {
            TextButton(onClick = { sorting = true }) {
                Icon(SubstackIcons.Sort, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(orderLabel(state.order)))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                SubstackOrder.entries.forEach { order -> DropdownMenuItem(text = { Text(pluginString(orderLabel(order))) }, onClick = { sorting = false; model.order(order) }) }
            }
        }
        Text(pluginString(Strings.loaded_controls), style = MaterialTheme.typography.bodySmall)
        if (state.page.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

private fun publicationFilterLabel(filter: SubstackPublicationFilter): PluginText = when (filter) {
    SubstackPublicationFilter.ALL -> Strings.filter_all
    SubstackPublicationFilter.UNREAD -> Strings.filter_unread
    SubstackPublicationFilter.FREE -> Strings.filter_free
    SubstackPublicationFilter.PODCASTS -> Strings.filter_podcast
    SubstackPublicationFilter.VIDEOS -> Strings.videos
}

@Composable
private fun EmptyPublication(model: PublicationModel, filter: SubstackPublicationFilter) {
    val filtered = filter != SubstackPublicationFilter.ALL
    EmptyState(pluginString(if (filtered) Strings.empty_filter else Strings.feed_empty)) {
        if (filtered) TextButton(onClick = { model.filter(SubstackPublicationFilter.ALL) }) { Text(pluginString(Strings.reset_filters)) }
    }
}

/** What a publication's author recommends, padded with publications like it, each to open, follow or put in groups (substack_similar_sheet.dart). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimilarSheet(app: SubstackApp, publication: SubstackPublication, onDismiss: () -> Unit) {
    val similar = rememberKept("substack-similar-${publication.baseUrl}") { scope -> SimilarModel(app, publication, scope) }
    val state by similar.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            SheetHeading(pluginString(Strings.similar_title), onDismiss)
            Text(pluginString(Strings.similar_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            val error = state.error
            when {
                state.loading -> LoadingRow()
                error != null -> EmptyState(pluginString(Strings.load_error)) { FilledTonalButton(onClick = similar::load) { Text(pluginString(Strings.retry)) } }
                state.recommendations.isEmpty() -> EmptyState(pluginString(Strings.similar_empty))
                else -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.recommendations, key = { it.publication.id + "\n" + it.publication.baseUrl }) { recommendation ->
                        SimilarRow(app, recommendation) { onDismiss(); app.openPublication(recommendation.publication) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarRow(app: SubstackApp, recommendation: SubstackRecommendation, onOpen: () -> Unit) {
    val publication = recommendation.publication
    val subtitle = recommendation.blurb?.takeIf { it.isNotBlank() } ?: publication.description ?: publication.subdomain
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp)) {
            PublicationLogo(publication.logoUrl, 40.dp, shape = CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(publication.displayName, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FollowButton(app, publication)
            GroupButton(app, publication)
        }
    }
}

/** The similar publications of one publication, read once, the publication itself left out. */
internal class SimilarModel(private val app: SubstackApp, private val publication: SubstackPublication, private val scope: CoroutineScope) {
    data class State(val recommendations: List<SubstackRecommendation> = emptyList(), val loading: Boolean = false, val error: Throwable? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (state.value.loading) return
        mutable.update { State(it.recommendations, loading = true) }
        scope.launch {
            mutable.value = try {
                val seen = hashSetOf(publication.id)
                State(app.client.similar(publication).filter { seen.add(it.publication.id) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State(state.value.recommendations, error = e)
            }
        }
    }
}

private const val LONG_DESCRIPTION = 160
private val STACKED_WIDTH = 360.dp
private const val STACKED_TEXT_SCALE = 1.3f
