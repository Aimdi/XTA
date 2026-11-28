package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.antenna.AntennaFeedViewModel
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.common.PostSkeleton
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.feed.feedThreads
import com.aimdi.xtanative.ui.feed.rememberFeedLayout
import com.aimdi.xtanative.ui.feed.rememberNearEnd
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_no_posts_match
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.generated.resources.problem_unable_to_load_the_next_page_of_tweets
import com.aimdi.xtanative.ui.media.SharedFeedPlayer
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.profile.ProfileTab
import com.aimdi.xtanative.ui.profile.ProfileViewModel
import com.aimdi.xtanative.ui.saved.SavedBodyView
import com.aimdi.xtanative.ui.saved.SavedViewModel
import com.aimdi.xtanative.ui.search.EmptyNote
import com.aimdi.xtanative.ui.search.LoadMoreNearEnd
import com.aimdi.xtanative.ui.search.PageRows
import com.aimdi.xtanative.ui.search.Paged
import com.aimdi.xtanative.ui.search.SearchTab
import com.aimdi.xtanative.ui.search.SearchViewModel
import com.aimdi.xtanative.ui.search.pageStatus
import org.jetbrains.compose.resources.stringResource

/*
 * The deck's columns read from what the app has already: each is the view
 * model its own screen uses, under a key of the column's, and reads only as
 * that screen does (when shown, at Refresh, and further as it is scrolled
 * down). None of them reads in the background.
 */

/** The body of a column read by a search, an account's posts, an antenna or a Saved folder. */
@Composable
internal fun LocalColumnBody(column: DeckColumn, actions: PostActions, reads: Int, toTop: Int) {
    when (column) {
        is DeckColumn.Search -> SearchColumnBody(column.query, actions, reads, toTop)
        is DeckColumn.Profile -> ProfileColumnBody(column.screenName, actions, reads, toTop)
        is DeckColumn.Antenna -> AntennaColumnBody(column.antennaId, actions, reads, toTop)
        is DeckColumn.SavedFolder -> SavedFolderColumnBody(column.folderId, actions)
        else -> Unit
    }
}

/** X's latest posts for [query], as Search's Latest tab reads them. */
@Composable
internal fun SearchColumnBody(query: String, actions: PostActions, reads: Int, toTop: Int) {
    val graph = LocalGraph.current
    val vm: SearchViewModel = viewModel(key = "deck-search:$query") { SearchViewModel(graph, query, SearchTab.LATEST) }
    val posts by vm.latest.state.collectAsStateWithLifecycle()
    RefreshesOn(reads) { vm.retry(SearchTab.LATEST) }
    PagedPosts(posts, actions, toTop, onRetry = { vm.retry(SearchTab.LATEST) }, onMore = { vm.more(SearchTab.LATEST) })
}

/** An antenna's feed, as its own screen reads it. */
@Composable
internal fun AntennaColumnBody(id: String, actions: PostActions, reads: Int, toTop: Int) {
    val graph = LocalGraph.current
    val vm: AntennaFeedViewModel = viewModel(key = "deck-antenna:$id") { AntennaFeedViewModel(graph, id) }
    val antenna by vm.antenna.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val posts by vm.posts.state.collectAsStateWithLifecycle()
    RefreshesOn(reads) { vm.retry() }
    when {
        !loaded -> LoadingRow()
        antenna == null -> EmptyNote(stringResource(Res.string.discover_no_results))
        else -> PagedPosts(posts, actions, toTop, onRetry = vm::retry, onMore = vm::more)
    }
}

/** Posts read page by page, the next page as the column nears its end. */
@Composable
private fun PagedPosts(posts: Paged<XThread>, actions: PostActions, toTop: Int, onRetry: () -> Unit, onMore: () -> Unit) {
    val list = rememberColumnList(toTop)
    LoadMoreNearEnd(list, posts, onMore)
    SharedFeedPlayer {
        WithReadingColumn(PaddingValues(), list) { column ->
            val layout = rememberFeedLayout(posts.items)
            LazyColumn(state = list, contentPadding = column, modifier = Modifier.fillMaxSize()) {
                feedThreads(posts.items, layout, actions)
                pageStatus(posts, Res.string.discover_no_posts_match, onRetry, onMore, moreFailed = Res.string.problem_unable_to_load_the_next_page_of_tweets, rows = PageRows.POSTS)
            }
        }
    }
}

/** An account's posts and reposts, as its profile's Posts tab reads them. */
@Composable
internal fun ProfileColumnBody(screenName: String, actions: PostActions, reads: Int, toTop: Int) {
    val graph = LocalGraph.current
    val vm: ProfileViewModel = viewModel(key = "deck-profile:" + screenName.lowercase()) { ProfileViewModel(graph, screenName, openingTab = ProfileTab.POSTS) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val tab = state.of(ProfileTab.POSTS)
    val list = rememberColumnList(toTop)
    val nearEnd by rememberNearEnd(list)
    RefreshesOn(reads) { vm.refresh() }
    LaunchedEffect(nearEnd, tab.threads.size) { if (nearEnd) vm.loadMore() }
    val problem = state.problem ?: tab.problem ?: Problem.Other.takeIf { state.failure != null }
    when {
        tab.threads.isEmpty() && problem != null -> ProblemView(problem, vm::refresh)
        tab.threads.isEmpty() && !tab.loaded -> PostSkeleton()
        tab.threads.isEmpty() -> EmptyNote(stringResource(Res.string.discover_no_posts_match))
        else -> SharedFeedPlayer {
            WithReadingColumn(PaddingValues(), list) { column ->
                val layout = rememberFeedLayout(tab.threads)
                LazyColumn(state = list, contentPadding = column, modifier = Modifier.fillMaxSize()) {
                    feedThreads(tab.threads, layout, actions)
                    item(key = "footer", contentType = "footer") {
                        val more = tab.moreProblem
                        when {
                            tab.loadingMore -> PostSkeleton()
                            more != null -> ProblemRow(more, vm::loadMore, context = stringResource(Res.string.problem_unable_to_load_the_next_page_of_tweets))
                        }
                    }
                }
            }
        }
    }
}

/** One of Saved's folders, as its tab in Saved shows it; nothing to read but the device. */
@Composable
internal fun SavedFolderColumnBody(folderId: String, actions: PostActions) {
    val graph = LocalGraph.current
    val vm: SavedViewModel = viewModel(key = "deck-saved:$folderId") { SavedViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(folderId) { vm.select(folderId) }
    when {
        !state.loaded -> LoadingRow()
        state.library.folders.none { it.id == folderId } -> EmptyNote(stringResource(Res.string.discover_no_results))
        else -> SharedFeedPlayer {
            SavedBodyView(state, actions, PaddingValues(), onWriteNote = {}, onNote = {}, onDeleteNote = {}, onOpenThread = {}, vm = vm)
        }
    }
}

/** A column's list, back at its top each time [toTop] grows. */
@Composable
private fun rememberColumnList(toTop: Int): LazyListState {
    val list = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    ScrollsToTop(toTop) { list.scrollToTop(reduced) }
    return list
}
