package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.links.XLinks
import com.aimdi.xtanative.ui.article.ArticleBody
import com.aimdi.xtanative.ui.article.ArticleLayout
import com.aimdi.xtanative.ui.article.ArticlePreviewCard
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.fullTimeText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.post
import com.aimdi.xtanative.ui.generated.resources.posts_continue_thread
import com.aimdi.xtanative.ui.generated.resources.posts_more
import com.aimdi.xtanative.ui.generated.resources.posts_replies_missing
import com.aimdi.xtanative.ui.generated.resources.posts_retry
import com.aimdi.xtanative.ui.generated.resources.posts_show_additional_replies
import com.aimdi.xtanative.ui.generated.resources.posts_sort_most_liked
import com.aimdi.xtanative.ui.generated.resources.posts_sort_oldest
import com.aimdi.xtanative.ui.generated.resources.posts_sort_order
import com.aimdi.xtanative.ui.generated.resources.posts_sort_recent
import com.aimdi.xtanative.ui.generated.resources.posts_sort_relevant
import com.aimdi.xtanative.ui.generated.resources.posts_unavailable
import com.aimdi.xtanative.ui.generated.resources.posts_views_count
import com.aimdi.xtanative.ui.generated.resources.posts_zen_replies
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An opened post (status.dart): what it answers, the post drawn as X draws
 * an opened one, its replies in the order the reader chose (nested when
 * "threaded replies" is on), and where they end, X's "Show additional
 * replies". A long press on any translate button translates them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(id: String, actions: PostActions, onBack: () -> Unit, startMedia: Int = 0) {
    val graph = LocalGraph.current
    val vm: PostViewModel = viewModel(key = "post-$id") { PostViewModel(graph, id) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val above = state.before.size + state.withheldBefore.size

    // Opened without the post at hand, the list starts at what it answers: move to the post itself once.
    LaunchedEffect(above) {
        if (above > 0 && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) list.scrollToItem(above)
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val reveal = remember(vm) { { vm.revealReplies() } }
        PostScopeProvider(focalId = id, translateAll = true, revealReplies = reveal) {
            val prefs = postScope().prefs
            val zenHidden = prefs.zen && !state.repliesRevealed
            WithReadingColumn(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp)) { column ->
                LazyColumn(
                    state = list,
                    contentPadding = column,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.withheldBefore.forEach { gap ->
                        item(key = "gap-" + gap.id) { Gap(gap.reason, gap.screenName, gap.id, actions) }
                    }
                    items(state.before, key = { "before-" + it.id }, contentType = { "post" }) { post ->
                        PostCard(post, actions, continues = true, midThread = post !== state.before.first())
                    }
                    focalItems(state, id, actions, vm, prefs, startMedia)
                    val problem = state.problem
                    when {
                        state.loading && state.replies.isEmpty() -> item(key = "loading") { LoadingRow() }
                        problem != null -> item(key = "problem") { ProblemRow(problem, { vm.load() }) }
                    }
                    if (zenHidden) {
                        item(key = "zen") { ZenReplies(vm::revealReplies) }
                    } else {
                        replyItems(state, actions, prefs.threadedReplies)
                        item(key = "footer") { ThreadEnd(state, vm) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.focalItems(state: PostUi, id: String, actions: PostActions, vm: PostViewModel, prefs: PostPrefs, startMedia: Int) {
    val focal = state.focal
    when {
        focal != null -> {
            item(key = "focal", contentType = "focal") { FocalPost(focal, actions, startMedia) }
            if (focal.shown.replyCount > 0 && !(prefs.zen && !state.repliesRevealed)) item(key = "sort") { ReplySortBar(state.sort, vm::select) }
        }
        state.focalWithheld != null || state.gone -> item(key = "focal-gone") {
            val gap = state.focalWithheld
            Gap(gap?.reason, gap?.screenName, id, actions)
        }
    }
}

/** A post X withheld, in its place: why, and the Wayback Machine when its author is known. */
@Composable
private fun Gap(reason: String?, screenName: String?, id: String, actions: PostActions) {
    UnavailablePost(
        reason ?: stringResource(Res.string.posts_unavailable),
        screenName?.let { XLinks.waybackUrl(it, id) },
        actions,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The replies: threads in X's order, or a tree with the deeper branches left to "Continue thread". */
private fun LazyListScope.replyItems(state: PostUi, actions: PostActions, threaded: Boolean) {
    if (!threaded) {
        items(state.replies, key = { "reply-" + it.id }, contentType = { "thread" }) { thread -> ThreadRows(thread, actions) }
        return
    }
    val rows = ThreadTree.rows(ThreadTree.nodes(state.replies))
    items(rows, key = { row -> rowKey(row) }, contentType = { row -> if (row is ThreadRow.Reply) "thread" else "continue" }) { row ->
        when (row) {
            is ThreadRow.Reply -> Box(Modifier.padding(start = (ThreadTree.indentLevels(row.visualDepth) * 40).dp)) {
                ThreadRows(row.node.thread, actions)
            }
            is ThreadRow.Continue -> ContinueThread(row, actions)
        }
    }
}

private fun rowKey(row: ThreadRow): String = when (row) {
    is ThreadRow.Reply -> "reply-" + row.node.thread.id
    is ThreadRow.Continue -> "continue-" + row.target.thread.id
}

/** "Continue thread": the first reply past the depth cap, opened on a screen of its own. */
@Composable
private fun ContinueThread(row: ThreadRow.Continue, actions: PostActions) {
    val first = row.target.thread.posts.firstOrNull() ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { actions.openPost(first.id) }
            .padding(start = (ThreadTree.indentLevels(row.indentDepth) * 40 + 16).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PostIcons.SubdirectoryArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.posts_continue_thread),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Where the replies end: more loading, a failed page, X's withheld replies, or a retry for replies X counted but never sent. */
@Composable
private fun ThreadEnd(state: PostUi, vm: PostViewModel) {
    val more = state.moreProblem
    when {
        state.loadingMore -> LoadingRow()
        more != null -> ProblemRow(more, vm::loadMore)
        state.showMore != null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = vm::showMoreReplies) {
                Icon(PostIcons.MoreHoriz, null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.posts_show_additional_replies), modifier = Modifier.padding(start = 8.dp))
            }
        }
        state.repliesMissing -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.posts_replies_missing), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            TextButton(onClick = { vm.load(bypassCache = true) }) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.posts_retry), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Zen mode: the replies stay hidden until the reader holds the reply button, or taps here. */
@Composable
private fun ZenReplies(onReveal: () -> Unit) {
    Text(
        stringResource(Res.string.posts_zen_replies),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onReveal).padding(24.dp),
    )
}

/** The reply order, in a row of its own under the opened post, closed by a hairline, as on X. */
@Composable
private fun ReplySortBar(sort: ReplySort, onSelect: (ReplySort) -> Unit) {
    Column {
        SortChip(sort, Sorts.X_REPLIES, { replySortLabel(it) }, ::replySortIcon, onSelect, Modifier.padding(start = 4.dp))
        Hairline()
    }
}

/** A compact "order ▾" that shows the current order and opens a menu of the others (sort_menu_button.dart). */
@Composable
internal fun <T> SortChip(value: T, options: List<T>, label: @Composable (T) -> String, icon: (T) -> ImageVector, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = Sorts.effective(value, options)
    val tint = MaterialTheme.colorScheme.primary
    val description = stringResource(Res.string.posts_sort_order)
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Icon(icon(shown), description, tint = tint, modifier = Modifier.size(18.dp))
            Text(label(shown), color = tint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            Icon(Icons.Filled.ArrowDropDown, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    leadingIcon = { Icon(icon(option), null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        open = false
                        if (option != shown) onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The opened post, larger: the author beside the avatar with translate and
 * ⋯, the text across the width (whole unless the reader asked for the
 * compact form), what it carries, its article in full, X's time · date ·
 * views line, and the counts, whose share sheet takes the post as drawn
 * for "Share … as image".
 */
@Composable
private fun FocalPost(post: XPost, actions: PostActions, startMedia: Int) {
    val scope = postScope()
    val shown = post.shown
    val translation = rememberPostTranslation(shown, scope)
    val drawn = translation?.shown ?: shown
    var menu by rememberSaveable(shown.id) { mutableStateOf(false) }
    val article = remember(shown) { ArticleLayout.fullArticle(shown) }
    val picture = rememberPostPicture()
    Column(Modifier.fillMaxWidth().recordedIn(picture).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PostAvatar(shown, scope, actions, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (PostView.showsAuthor(shown, scope.prefs, scope.profileScreenName)) {
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
            translation?.let { TranslateButton(it, scope) }
            HeaderButton(PostIcons.MoreHoriz, stringResource(Res.string.posts_more)) { menu = true }
        }
        PostView.replyingTo(shown, quoted = false, midThread = false)?.let { ReplyingTo(it, shown.replyToPostId, actions) }
        if (shown.article == null) {
            ExpandablePostText(
                drawn,
                actions,
                Modifier.padding(top = 12.dp),
                style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
                maxLines = if (scope.prefs.fullPostContents) null else POST_TEXT_MAX_LINES,
                selectable = true,
            )
        }
        TranslationFailure(translation)
        PostAttachments(shown, scope, actions, playing = false, gap = 12.dp, startMedia = startMedia)
        if (article != null) {
            ArticleBody(article, actions, Modifier.padding(top = 12.dp), header = { PostFooter(shown, actions, FooterCounts.of(shown, scope.prefs, showViews = false), picture = picture) })
        } else if (shown.article != null) {
            ArticlePreviewCard(shown, { actions.openArticle(shown.id) }, Modifier.padding(top = 12.dp))
        }
        FocalMetaLine(shown, scope.prefs)
        if (article == null) PostFooter(shown, actions, FooterCounts.of(shown, scope.prefs, showViews = false), picture = picture)
    }
    Hairline()
    if (menu) PostMenu(post, actions, onDismiss = { menu = false })
}

/** X's line under an opened post: `16:12 · 9 Oct 2026 · 77K Views`, the count in bold; no views in zen or calm mode. */
@Composable
private fun FocalMetaLine(post: XPost, prefs: PostPrefs) {
    val views = post.viewCount?.takeIf { it > 0 && !prefs.hideCounts }
    val time = fullTimeText(post.createdAt)
    val viewsLabel = views?.let { pluralStringResource(Res.plurals.posts_views_count, viewsPluralCount(it), countText(it)) }
    val strong = MaterialTheme.colorScheme.onSurface
    if (time.isEmpty() && viewsLabel == null) return
    val number = views?.let { countText(it) }
    Text(
        buildAnnotatedString {
            append(time)
            if (time.isNotEmpty() && viewsLabel != null) append(" · ")
            if (viewsLabel != null && number != null) {
                val at = viewsLabel.indexOf(number)
                if (at < 0) {
                    append(viewsLabel)
                } else {
                    append(viewsLabel.substring(0, at))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                    append(viewsLabel.substring(at + number.length))
                }
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
internal fun replySortLabel(sort: ReplySort): String = stringResource(
    when (sort) {
        ReplySort.RELEVANT -> Res.string.posts_sort_relevant
        ReplySort.RECENT -> Res.string.posts_sort_recent
        ReplySort.OLDEST -> Res.string.posts_sort_oldest
        ReplySort.MOST_LIKED -> Res.string.posts_sort_most_liked
    },
)

internal fun replySortIcon(sort: ReplySort): ImageVector = when (sort) {
    ReplySort.RELEVANT -> PostIcons.Sparkle
    ReplySort.RECENT -> PostIcons.Schedule
    ReplySort.OLDEST -> PostIcons.History
    ReplySort.MOST_LIKED -> XIcons.Like
}
