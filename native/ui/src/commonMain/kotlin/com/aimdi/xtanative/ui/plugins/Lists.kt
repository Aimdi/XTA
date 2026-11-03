package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.NetworkFailure
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.plugin_problem_no_account
import com.aimdi.xtanative.ui.generated.resources.plugin_problem_offline
import com.aimdi.xtanative.ui.generated.resources.plugin_problem_other
import com.aimdi.xtanative.ui.generated.resources.plugin_problem_rate_limited
import com.aimdi.xtanative.ui.generated.resources.plugin_problem_refused
import com.aimdi.xtanative.ui.generated.resources.retry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Why a network's read brought nothing, as the shared problem rows explain it:
 * a refusal by its status (429 a rate limit, 200 an answer that could not be
 * used), no connection, or anything else.
 */
fun problemOf(error: Throwable): Problem = when (error) {
    is NetworkFailure -> when (val code = error.code) {
        null, 200 -> Problem.Other
        429 -> Problem.RateLimited
        else -> Problem.Refused(code)
    }
    else -> Problem.of(error)
}

/** [problem] in plain words, naming [network] ("Bluesky"). */
@Composable
fun networkProblemText(problem: Problem, network: String): String = when (problem) {
    Problem.NoAccount -> stringResource(Res.string.plugin_problem_no_account, network)
    Problem.RateLimited -> stringResource(Res.string.plugin_problem_rate_limited, network)
    Problem.Offline -> stringResource(Res.string.plugin_problem_offline, network)
    is Problem.Refused -> stringResource(Res.string.plugin_problem_refused, network, problem.code)
    Problem.Signing, Problem.Other -> stringResource(Res.string.plugin_problem_other, network)
}

/** A read of [network] that brought nothing: why, and a way to try again. */
@Composable
fun NetworkProblemView(problem: Problem, network: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            networkProblemText(problem, network),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

/** One row in a list: a failed page of [network] under what is already shown. */
@Composable
fun NetworkProblemRow(problem: Problem, network: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            networkProblemText(problem, network),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

/** A message in place of a list: nothing there yet, nothing found. */
@Composable
fun MessageText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
    )
}

/** Where a list read page by page stands. */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    /** Where the next page starts; null when there is none. */
    val next: String? = null,
    /** The first page is being read, or read again. */
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** A first page has arrived. */
    val loaded: Boolean = false,
    /** Why the first page did not arrive. */
    val problem: Problem? = null,
    /** Why the next page did not arrive. */
    val moreProblem: Problem? = null,
)

/**
 * A list a network hands out page by page: [read] gives the page that starts
 * at a cursor (null for the first), and each item is shown once by [key]. The
 * first page is read at once; [refresh] reads it again and [more] the next
 * one. A cursor that comes round again, or a page with nothing new, ends the
 * list. Reads run in [scope]: [rememberKept]'s, or a view model's.
 */
class PagedItems<T : Any>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val read: suspend (cursor: String?) -> Page<T>,
) {
    private val mutable = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = mutable.asStateFlow()

    private var job: Job? = null
    private val used = HashSet<String>()

    init {
        refresh()
    }

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            mutable.update { it.copy(loading = true, problem = null, moreProblem = null) }
            try {
                val page = read(null)
                used.clear()
                mutable.value = PagedState(items = page.items.distinctBy(key), next = page.next?.ifEmpty { null }, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, problem = problemOf(e)) }
            }
        }
    }

    fun more() {
        val now = mutable.value
        val cursor = now.next ?: return
        if (now.loading || now.loadingMore || now.moreProblem != null || job?.isActive == true) return
        job = scope.launch {
            mutable.update { it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = read(cursor)
                used += cursor
                mutable.update { shown ->
                    val known = shown.items.mapTo(HashSet(), key)
                    val fresh = page.items.filter { key(it) !in known }.distinctBy(key)
                    val next = page.next?.takeIf { it.isNotEmpty() && it !in used && fresh.isNotEmpty() }
                    shown.copy(items = shown.items + fresh, next = next, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loadingMore = false, moreProblem = problemOf(e)) }
            }
        }
    }

    /** Reads the page that failed again. */
    fun retryMore() {
        mutable.update { it.copy(moreProblem = null) }
        more()
    }
}

/**
 * [pages] as a list that reads more as its end comes into view and is pulled
 * to read again: [row] draws each item under [header], [empty] says there is
 * nothing, and a failure names [network]. [toTop] grows each time the reader
 * asks for the top again ([PluginFrame.toTop]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> PagedList(
    pages: PagedItems<T>,
    key: (T) -> String,
    network: String,
    empty: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    toTop: Int = 0,
    state: LazyListState = rememberLazyListState(),
    header: LazyListScope.() -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val paged by pages.state.collectAsStateWithLifecycle()
    ScrollsToTop(state, toTop)
    ReadsMoreNearEnd(state, paged) { pages.more() }
    PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            header()
            pagedRows(paged, pages, key, network, empty, row)
        }
    }
}

/** What [paged] shows in a list: why it is empty or failed, its items, and how its next page is coming. */
fun <T : Any> LazyListScope.pagedRows(
    paged: PagedState<T>,
    pages: PagedItems<T>,
    key: (T) -> String,
    network: String,
    empty: String,
    row: @Composable (T) -> Unit,
) {
    val problem = paged.problem
    when {
        paged.items.isEmpty() && problem != null -> item(key = "paged-problem") { NetworkProblemView(problem, network, pages::refresh) }
        paged.items.isEmpty() && paged.loaded && !paged.loading -> item(key = "paged-empty") { MessageText(empty) }
        paged.items.isEmpty() -> item(key = "paged-loading") { LoadingRow() }
        problem != null -> item(key = "paged-problem") { NetworkProblemRow(problem, network, pages::refresh) }
    }
    items(paged.items, key = key, contentType = { "row" }) { row(it) }
    item(key = "paged-footer") {
        val more = paged.moreProblem
        when {
            paged.loadingMore -> LoadingRow()
            more != null -> NetworkProblemRow(more, network, pages::retryMore)
        }
    }
}

/** Asks for [more] as the end of [state]'s list comes into view, once per page of [paged]. */
@Composable
fun ReadsMoreNearEnd(state: LazyListState, paged: PagedState<*>, more: () -> Unit) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, paged.items.size, paged.next) { if (nearEnd) more() }
}

/** Scrolls [state] to the top each time [toTop] grows past what it last answered, and not when a list comes back into view. */
@Composable
fun ScrollsToTop(state: LazyListState, toTop: Int) {
    var answered by rememberSaveable { mutableIntStateOf(toTop) }
    LaunchedEffect(toTop) {
        if (toTop > answered) {
            answered = toTop
            state.animateScrollToItem(0)
        }
    }
}

/** Calls [read] each time [refresh] ([PluginFrame.refresh]) grows past what it last answered, and not when a page comes back into view. */
@Composable
fun RefreshesOn(refresh: Int, read: () -> Unit) {
    var answered by rememberSaveable { mutableIntStateOf(refresh) }
    LaunchedEffect(refresh) {
        if (refresh > answered) {
            answered = refresh
            read()
        }
    }
}
