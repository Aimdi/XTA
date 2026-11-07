package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.feed.feedThreads
import com.aimdi.xtanative.ui.feed.rememberFeedLayout
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.posts_no_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_unable_to_load_more_retweets
import com.aimdi.xtanative.ui.generated.resources.posts_unable_to_load_the_next_page_of_replies
import com.aimdi.xtanative.ui.generated.resources.posts_unable_to_load_who_retweeted
import com.aimdi.xtanative.ui.generated.resources.posts_no_retweets
import com.aimdi.xtanative.ui.generated.resources.posts_quotes
import com.aimdi.xtanative.ui.generated.resources.posts_retweets
import com.aimdi.xtanative.ui.generated.resources.posts_sort_most_followers
import com.aimdi.xtanative.ui.generated.resources.posts_sort_most_liked
import com.aimdi.xtanative.ui.generated.resources.posts_sort_oldest
import com.aimdi.xtanative.ui.generated.resources.posts_sort_recent
import com.aimdi.xtanative.ui.generated.resources.posts_sort_top
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.profile.PeoplePaging
import com.aimdi.xtanative.ui.profile.UserRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** One list of the screen, page after page. */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = true,
    val problem: Problem? = null,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
)

data class QuotesUi(
    val quotes: Paged<XThread> = Paged(),
    val quoteSort: QuoteSort = QuoteSort.RECENT,
    val reposters: Paged<XUser> = Paged(),
    val reposterSort: ReposterSort = ReposterSort.RECENT,
)

/**
 * The quotes of a post and the people who reposted it (quotes_screen.dart):
 * quotes in X's order (Latest or Top), people newest first, or the most
 * followed first once X said how many follow each. Reading only.
 */
class QuotesViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    private val state = MutableStateFlow(QuotesUi(quoteSort = Sorts.state.value.quotes, reposterSort = Sorts.state.value.reposters))
    val ui: StateFlow<QuotesUi> = state.asStateFlow()
    private val people = PeoplePaging()
    private var quotesJob: Job? = null

    init {
        loadQuotes()
        loadReposters()
    }

    fun loadQuotes(cursor: String? = null) {
        if (cursor != null && (state.value.quotes.loadingMore || state.value.quotes.loading)) return
        if (cursor == null) quotesJob?.cancel()
        quotesJob = viewModelScope.launch {
            setQuotes { if (cursor == null) Paged() else it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = graph.client.quotes(id, cursor, Sorts.quoteOrder(state.value.quoteSort))
                graph.posts.remember(page.threads)
                setQuotes { paged ->
                    val known = paged.items.mapTo(HashSet()) { it.id }
                    val fresh = page.threads.filter { it.id !in known }
                    paged.copy(
                        items = paged.items + fresh,
                        loading = false,
                        loadingMore = false,
                        cursor = page.bottomCursor.takeIf { fresh.isNotEmpty() && it != cursor },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                setQuotes { if (cursor == null) it.copy(loading = false, problem = Problem.of(error)) else it.copy(loadingMore = false, moreProblem = Problem.of(error)) }
            }
        }
    }

    fun loadReposters(cursor: String? = null) {
        if (cursor != null && (state.value.reposters.loadingMore || state.value.reposters.loading)) return
        viewModelScope.launch {
            if (cursor == null) people.reset()
            setReposters { if (cursor == null) Paged() else it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = graph.client.reposters(id, cursor)
                val fresh = people.page(page.users, page.bottomCursor, cursor)
                fresh.people.forEach(graph.posts::remember)
                setReposters { it.copy(items = it.items + fresh.people, loading = false, loadingMore = false, cursor = fresh.next) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                setReposters {
                    if (cursor == null) it.copy(loading = false, problem = Problem.of(error)) else it.copy(loadingMore = false, moreProblem = Problem.of(error))
                }
            }
        }
    }

    fun selectQuotes(sort: QuoteSort) {
        if (sort == state.value.quoteSort) return
        Sorts.selectQuotes(sort)
        state.update { it.copy(quoteSort = sort) }
        loadQuotes()
    }

    fun selectReposters(sort: ReposterSort) {
        Sorts.selectReposters(sort)
        state.update { it.copy(reposterSort = sort) }
    }

    private fun setQuotes(change: (Paged<XThread>) -> Paged<XThread>) = state.update { it.copy(quotes = change(it.quotes)) }

    private fun setReposters(change: (Paged<XUser>) -> Paged<XUser>) = state.update { it.copy(reposters = change(it.reposters)) }
}

/** The two tabs X shows behind a post's repost count: who quoted it and who reposted it. [reposters] opens on the second. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(id: String, reposters: Boolean, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: QuotesViewModel = viewModel(key = "quotes-$id") { QuotesViewModel(graph, id) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = if (reposters) 1 else 0) { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    TabRow(selectedTabIndex = pager.currentPage, containerColor = MaterialTheme.colorScheme.background, divider = {}) {
                        listOf(Res.string.posts_quotes, Res.string.posts_retweets).forEachIndexed { index, label ->
                            Tab(
                                selected = pager.currentPage == index,
                                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                text = { Text(stringResource(label)) },
                                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PostScopeProvider {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { page ->
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding() + 48.dp)
                if (page == 0) QuotesList(state, vm, actions, bottom) else RepostersList(state, vm, actions, bottom)
            }
        }
    }
}

@Composable
private fun QuotesList(state: QuotesUi, vm: QuotesViewModel, actions: PostActions, padding: PaddingValues) {
    val list = rememberLazyListState()
    val quotes = state.quotes
    PageMore(list, quotes.items.size) { quotes.cursor?.let(vm::loadQuotes) }
    val layout = rememberFeedLayout(quotes.items)
    WithReadingColumn(padding) { column ->
        LazyColumn(state = list, contentPadding = column, modifier = Modifier.fillMaxSize()) {
            item(key = "sort") {
                SortChip(state.quoteSort, Sorts.X_QUOTES, { quoteSortLabel(it) }, ::quoteSortIcon, vm::selectQuotes, Modifier.padding(start = 4.dp))
            }
            listState(quotes, { vm.loadQuotes() }, { quotes.cursor?.let(vm::loadQuotes) }, Res.string.posts_no_quotes, moreFailed = Res.string.posts_unable_to_load_the_next_page_of_replies) {
                feedThreads(quotes.items, layout, actions, keyOf = { "quote-" + it.id })
            }
        }
    }
}

@Composable
private fun RepostersList(state: QuotesUi, vm: QuotesViewModel, actions: PostActions, padding: PaddingValues) {
    val list = rememberLazyListState()
    val reposters = state.reposters
    PageMore(list, reposters.items.size) { reposters.cursor?.let(vm::loadReposters) }
    val options = Sorts.reposterSorts(reposters.items)
    val sort = Sorts.effective(state.reposterSort, options)
    val people = remember(reposters.items, sort) { Sorts.sortReposters(reposters.items, sort) }
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        if (options.size > 1) {
            item(key = "sort") { SortChip(sort, options, { reposterSortLabel(it) }, ::reposterSortIcon, vm::selectReposters, Modifier.padding(start = 4.dp)) }
        }
        listState(
            reposters,
            { vm.loadReposters() },
            { reposters.cursor?.let(vm::loadReposters) },
            Res.string.posts_no_retweets,
            failed = Res.string.posts_unable_to_load_who_retweeted,
            moreFailed = Res.string.posts_unable_to_load_more_retweets,
        ) {
            items(people, key = { "user-" + it.id }) { user -> UserRow(user, actions) }
        }
    }
}

/** A list's first load, its failure, its emptiness, its rows and the page under way below them. */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.listState(
    paged: Paged<T>,
    retry: () -> Unit,
    retryMore: () -> Unit,
    empty: org.jetbrains.compose.resources.StringResource,
    failed: org.jetbrains.compose.resources.StringResource? = null,
    moreFailed: org.jetbrains.compose.resources.StringResource? = null,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val problem = paged.problem
    when {
        paged.loading -> item(key = "loading") { LoadingRow() }
        problem != null -> item(key = "problem") { ProblemRow(problem, retry, context = failed?.let { stringResource(it) }) }
        paged.items.isEmpty() -> item(key = "empty") {
            Text(
                stringResource(empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
        else -> rows()
    }
    item(key = "more") {
        val more = paged.moreProblem
        when {
            paged.loadingMore -> LoadingRow()
            more != null -> ProblemRow(more, retryMore, context = moreFailed?.let { stringResource(it) })
        }
    }
}

/** Asks for the next page as the list nears its end. */
@Composable
internal fun PageMore(list: LazyListState, size: Int, more: () -> Unit) {
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, size) { if (nearEnd) more() }
}

@Composable
internal fun quoteSortLabel(sort: QuoteSort): String = stringResource(
    when (sort) {
        QuoteSort.RECENT -> Res.string.posts_sort_recent
        QuoteSort.TOP -> Res.string.posts_sort_top
        QuoteSort.OLDEST -> Res.string.posts_sort_oldest
        QuoteSort.MOST_LIKED -> Res.string.posts_sort_most_liked
    },
)

internal fun quoteSortIcon(sort: QuoteSort): ImageVector = when (sort) {
    QuoteSort.RECENT -> PostIcons.Schedule
    QuoteSort.TOP -> PostIcons.TrendingUp
    QuoteSort.OLDEST -> PostIcons.History
    QuoteSort.MOST_LIKED -> XIcons.Like
}

@Composable
internal fun reposterSortLabel(sort: ReposterSort): String =
    stringResource(if (sort == ReposterSort.RECENT) Res.string.posts_sort_recent else Res.string.posts_sort_most_followers)

internal fun reposterSortIcon(sort: ReposterSort): ImageVector = if (sort == ReposterSort.RECENT) PostIcons.Schedule else PostIcons.People
