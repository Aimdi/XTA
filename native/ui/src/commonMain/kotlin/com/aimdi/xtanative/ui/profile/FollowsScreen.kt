package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.posts_followers
import com.aimdi.xtanative.ui.generated.resources.posts_following
import com.aimdi.xtanative.ui.generated.resources.posts_no_followers
import com.aimdi.xtanative.ui.generated.resources.posts_no_follows
import com.aimdi.xtanative.ui.post.PageMore
import com.aimdi.xtanative.ui.post.PostActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class FollowsUi(
    val people: List<XUser> = emptyList(),
    val loading: Boolean = true,
    val problem: Problem? = null,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
)

/**
 * Whom a profile follows, or who follows it (_follows.dart), two hundred a
 * page. A followed account's first Following page is remembered: it answers
 * "Followed by" on other profiles without asking X.
 */
class FollowsViewModel(private val graph: AppGraph, private val screenName: String, private val followers: Boolean) : ViewModel() {
    private val state = MutableStateFlow(FollowsUi())
    val ui: StateFlow<FollowsUi> = state.asStateFlow()
    private val paging = PeoplePaging()
    private var userId: String? = graph.posts.user(screenName)?.id

    init {
        load()
    }

    fun load(cursor: String? = null) {
        if (cursor != null && (state.value.loading || state.value.loadingMore)) return
        viewModelScope.launch {
            if (cursor == null) paging.reset()
            state.update { if (cursor == null) FollowsUi() else it.copy(loadingMore = true, moreProblem = null) }
            try {
                val id = userId ?: graph.client.profile(screenName).first.also(graph.posts::remember).id.also { userId = it }
                val page = if (followers) graph.client.followers(id, cursor, PAGE) else graph.client.following(id, cursor, PAGE)
                if (cursor == null && !followers && graph.sessions.state.value?.followed(id) != null) graph.follows.remember(id, KnownFollows.of(page.users))
                val fresh = paging.page(page.users, page.bottomCursor, cursor)
                fresh.people.forEach(graph.posts::remember)
                state.update { it.copy(people = it.people + fresh.people, loading = false, loadingMore = false, cursor = fresh.next) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update {
                    if (cursor == null) it.copy(loading = false, problem = Problem.of(error)) else it.copy(loadingMore = false, moreProblem = Problem.of(error))
                }
            }
        }
    }

    private companion object {
        const val PAGE = 200
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowsScreen(screenName: String, followers: Boolean, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: FollowsViewModel = viewModel(key = "follows-$screenName-$followers") { FollowsViewModel(graph, screenName, followers) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    PageMore(list, state.people.size) { state.cursor?.let(vm::load) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (followers) Res.string.posts_followers else Res.string.posts_following)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val problem = state.problem
            when {
                state.loading -> item(key = "loading") { LoadingRow() }
                problem != null -> item(key = "problem") { ProblemRow(problem, { vm.load() }) }
                state.people.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(if (followers) Res.string.posts_no_followers else Res.string.posts_no_follows),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                else -> items(state.people, key = { it.id }) { user -> UserRow(user, actions) }
            }
            item(key = "more") {
                val more = state.moreProblem
                when {
                    state.loadingMore -> LoadingRow()
                    more != null -> ProblemRow(more, { state.cursor?.let(vm::load) })
                }
            }
        }
    }
}
