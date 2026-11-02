package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.post
import com.aimdi.xtanative.ui.generated.resources.stat_bookmarks
import com.aimdi.xtanative.ui.generated.resources.stat_likes
import com.aimdi.xtanative.ui.generated.resources.stat_quotes
import com.aimdi.xtanative.ui.generated.resources.stat_reposts
import com.aimdi.xtanative.ui.generated.resources.views
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(id: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: PostViewModel = viewModel { PostViewModel(graph, id) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()

    // Opened without the post at hand, the list starts at what it answers: move to the post itself once.
    LaunchedEffect(state.before.size) {
        if (state.before.isNotEmpty() && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) {
            list.scrollToItem(state.before.size)
        }
    }
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.replies.size) { if (nearEnd) vm.loadMore() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.post)) },
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
            items(state.before, key = { "before-" + it.id }, contentType = { "post" }) { post ->
                PostCard(post, actions, continues = true)
            }
            state.focal?.let { focal ->
                item(key = "focal", contentType = "focal") { FocalPost(focal, actions) }
            }
            val problem = state.problem
            when {
                state.loading -> item(key = "loading") { LoadingRow() }
                problem != null -> item(key = "problem") { ProblemRow(problem, vm::load) }
            }
            items(state.replies, key = { "reply-" + it.id }, contentType = { "thread" }) { thread ->
                ThreadRows(thread, actions)
            }
            item(key = "footer") {
                val more = state.moreProblem
                when {
                    state.loadingMore -> LoadingRow()
                    more != null -> ProblemRow(more, vm::loadMore)
                }
            }
        }
    }
}

/** The opened post, larger, with its full time and its counts spelled out. */
@Composable
private fun FocalPost(post: XPost, actions: PostActions) {
    val shown = post.shown
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(shown.author.largeAvatarUrl, 44.dp) { actions.openProfile(shown.author.screenName) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shown.author.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (shown.author.verified) {
                        Icon(XIcons.Verified, null, tint = Color.Unspecified, modifier = Modifier.padding(start = 2.dp).size(18.dp))
                    }
                }
                Text("@" + shown.author.screenName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PostText(shown, actions, Modifier.padding(top = 12.dp), style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp))
        if (shown.media.isNotEmpty()) {
            MediaGrid(shown.media, { index -> actions.openMedia(shown.id, index) }, Modifier.padding(top = 12.dp))
        }
        shown.card?.let { CardView(it, Modifier.padding(top = 12.dp)) }
        shown.quoted?.let { QuotedPost(it, actions, Modifier.padding(top = 12.dp)) }
        val views = shown.viewCount
        Text(
            fullTimeText(shown.createdAt) + if (views != null && views > 0) " · " + stringResource(Res.string.views, countText(views)) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Stats(shown)
    }
    Hairline()
}

@Composable
private fun Stats(post: XPost) {
    val stats = listOf(
        post.repostCount to Res.string.stat_reposts,
        post.quoteCount to Res.string.stat_quotes,
        post.likeCount to Res.string.stat_likes,
        post.bookmarkCount to Res.string.stat_bookmarks,
    ).filter { it.first > 0 }
    if (stats.isEmpty()) return
    Hairline(Modifier.padding(top = 12.dp))
    val strong = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        stats.forEach { (n, label) ->
            val number = countText(n)
            val word = stringResource(label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                    append(" ")
                    append(word)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
