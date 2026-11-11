package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The list's rows before the comments: the post with its actions, then the comments' header. */
private const val ABOVE = 2

/**
 * A post and its comments, as XTA redesigned its thread: the post under its
 * community, its actions, the comments in the order chosen with their
 * thread lines, folds and "new" marks, the comments Reddit held back loading
 * in place, and a button that jumps to the next top-level comment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadScreen(app: RedditApp, route: RedditRoute.Thread) {
    val thread = rememberKept("reddit-thread:" + route.permalink) { scope ->
        RedditThreadState(app.postFor(route), scope, visit = { app.visit(route.permalink) }, read = app::thread, fill = app::post, more = app::moreComments)
    }
    val state by thread.state.collectAsState()
    val rows = remember(state.comments, state.collapsed) { state.comments?.let { RedditThreadRules.visible(it, state.collapsed) } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    PluginScaffold(
        title = "r/" + state.post.subreddit,
        onBack = app.host::back,
        actions = { IconButton(onClick = { sheet = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more_options)) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = thread::load, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp), modifier = Modifier.fillMaxSize()) {
                    item(key = "post") { PostBlock(app, state, onComments = { scope.launch { list.animateScrollToItem(1) } }) }
                    item(key = "header") {
                        CommentsHeader(
                            count = RedditThreadRules.commentCount(state.post, state.comments),
                            sort = state.sort,
                            onSort = thread::resort,
                            allFolded = state.comments?.let { RedditThreadRules.allFolded(it, state.collapsed) },
                            onToggleAll = thread::toggleAll,
                        )
                    }
                    commentRows(app, thread, state, rows)
                }
            }
            if (rows != null && RedditThreadRules.showsJump(rows)) {
                JumpButton(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp)) { jump(scope, list, rows) }
            }
        }
    }
    if (sheet) PostSheet(app, state.post) { sheet = false }
}

/** The comments, or why they are not there yet. */
private fun LazyListScope.commentRows(app: RedditApp, thread: RedditThreadState, state: RedditThreadState.State, rows: List<VisibleComment>?) {
    val problem = state.problem
    when {
        rows == null && problem != null -> item(key = "problem") { RedditProblemView(problem, onRetry = thread::load, compact = true) }
        rows == null -> item(key = "loading") { LoadingRow(Modifier.padding(vertical = 24.dp)) }
        else -> itemsIndexed(rows, key = { index, row -> "$index:" + row.entry.comment.id }) { index, row ->
            val comment = row.entry.comment
            if (comment.isStub) {
                HeldBack(app, thread, state, row)
            } else {
                CommentRow(
                    app,
                    row,
                    folded = comment.id in state.collapsed,
                    divided = RedditThreadRules.divided(row.entry.depth, index),
                    isNew = RedditVisits.isNew(comment, state.lastVisit),
                    onToggle = { thread.toggle(comment.id) },
                )
            }
        }
    }
}

/** Comments Reddit held back: loaded here when Reddit said where, else on their own page, or on Reddit once it will not hand them over. */
@Composable
private fun HeldBack(app: RedditApp, thread: RedditThreadState, state: RedditThreadState.State, row: VisibleComment) {
    val stub = row.entry.comment
    val outside = rememberOpenOutside()
    val action = RedditThreadRules.heldBack(stub, state.post, state.moreRefused)
    val postUrl = RedditThreadRules.postUrl(state.post)
    HeldBackRow(row.entry.depth, action, loading = stub.id in state.loadingMore) {
        when (action) {
            HeldBack.LOAD_HERE -> thread.loadMore(stub) { outside(postUrl) }
            HeldBack.OPEN_ON_REDDIT -> outside(postUrl)
            HeldBack.OPEN_SUBTREE -> stub.permalink?.let { app.openThread(state.post.copy(permalink = it)) }
            HeldBack.NOTHING -> Unit
        }
    }
}

/** Scrolls to the next top-level comment below the one at the top of the screen, or to the end when none is left. */
private fun jump(scope: CoroutineScope, list: LazyListState, rows: List<VisibleComment>) {
    scope.launch {
        val next = RedditThreadRules.nextTopLevel(rows, list.firstVisibleItemIndex - ABOVE)
        list.animateScrollToItem(if (next == null) rows.size + ABOVE - 1 else next + ABOVE)
    }
}

/** The post at the head of its thread: who wrote it and when, its title, its media and its words, then its actions. */
@Composable
private fun PostBlock(app: RedditApp, state: RedditThreadState.State, onComments: () -> Unit) {
    val post = state.post
    val choices by app.choices.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            PostAuthor(app, post)
            if (post.showsTitle) {
                Text(
                    post.displayTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            PostMediaView(app, post, choices, Modifier.padding(top = 14.dp), thread = true)
            RedditThreadRules.visibleSelfText(post, state.selfText)?.let { RedditText(it, Modifier.padding(top = 12.dp), style = threadBodyStyle()) }
        }
        PostActions(app, post, RedditThreadRules.commentCount(post, state.comments), onComments)
    }
}

@Composable
private fun PostAuthor(app: RedditApp, post: RedditPost) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        post.author?.let { author ->
            FaceAvatar(author, 22.dp, round = true)
            Spacer(Modifier.width(8.dp))
            Text(
                "u/$author",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
        }
        val ago = agoText(post.createdAt)
        if (ago.isNotEmpty()) Text(ago, fontSize = 13.sp, color = muted)
    }
}

/** Under the post: the vote pill and the comments pill, and at the end share and save; a line each where they do not fit beside each other. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PostActions(app: RedditApp, post: RedditPost, comments: Int, onComments: () -> Unit) {
    val archived by app.host.saved.collectAsState()
    val share = rememberShareLink()
    val isSaved = RedditArchive.id(post) in archived
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UpvoteButton(app, post.id, post.score, filled = true) { app.upvote(post) }
            Pill(filled = true, onClick = onComments) {
                Icon(RedditGlyphs.Comment, contentDescription = pluginPlural(Strings.comment_count, comments, comments), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(countText(comments.toLong()), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { share(RedditThreadRules.postUrl(post)) }) {
                Icon(Icons.Outlined.Share, contentDescription = pluginString(Strings.share_link), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            // A tap saves on this device or takes it out; a long press picks its folder, as XTA's RedditThreadSaveButton.
            Box(
                Modifier.size(48.dp).clip(CircleShape).combinedClickable(role = Role.Button, onClick = { app.toggleSaved(post) }, onLongClick = { app.chooseFolder(post) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSaved) RedditGlyphs.Bookmark else RedditGlyphs.BookmarkBorder,
                    contentDescription = pluginString(if (isSaved) Strings.remove_from_device else Strings.save_on_device),
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
