package com.aimdi.xtanative.plugins.stocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.stocks.FollowedPart
import com.aimdi.xtanative.networks.stocks.FollowedPosts
import com.aimdi.xtanative.networks.stocks.FollowedSectionKeeper
import com.aimdi.xtanative.networks.stocks.SignalGroups
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.XThreadRow
import com.aimdi.xtanative.ui.plugins.rememberXThreadStacks
import com.aimdi.xtanative.ui.plugins.pagedRows
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberXPostActions
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScopeProvider
import com.aimdi.xtanative.ui.post.ThreadRows
import kotlinx.coroutines.CoroutineScope

/**
 * One feed of X posts about tickers, read as XTA's stocks_posts_feed.dart
 * and ticker_screen.dart read theirs: X's search for [query], page by page;
 * with [signals], each page's signal-group posts last, under a banner that
 * folds them; with [followedFirst] (one ticker), the posts of accounts the
 * reader follows on top. Reads run in [scope], the screen's.
 */
internal class StocksPostsFeed(
    private val app: StocksApp,
    val query: String,
    val signals: Boolean,
    private val followedFirst: Boolean,
    scope: CoroutineScope,
) {
    val keeper = FollowedSectionKeeper()
    val pages = PagedItems(scope, key = XThread::id, read = ::read)

    private suspend fun read(cursor: String?): Page<XThread> =
        if (followedFirst) FollowedPosts.page(cursor, query, app.follows(), keeper, app.followed, read = ::page) else page(cursor)

    private suspend fun page(cursor: String?): Page<XThread> {
        val timeline = app.host.x.search(query, cursor)
        return if (signals) SignalGroups.page(timeline) else Page(timeline.threads, timeline.bottomCursor)
    }
}

/**
 * [feed]'s posts under [header], drawn as the app's feeds draw X's: a list
 * read on as its end comes into view, and pulled to read again, [onRefresh]
 * with it. "Accounts you follow" and "Everyone else" head the two parts of
 * one ticker's posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StocksPostsList(
    app: StocksApp,
    feed: StocksPostsFeed,
    padding: PaddingValues,
    toTop: Int,
    onRefresh: () -> Unit = {},
    header: LazyListScope.() -> Unit = {},
) {
    val paged by feed.pages.state.collectAsState()
    val section by feed.keeper.section.collectAsState()
    val folds by app.signalFolds.collectAsState()
    val foldsByDefault by app.foldSignals.collectAsState()
    val list = rememberLazyListState()
    val actions = rememberXPostActions(app.host, paged.items)
    val stacks = rememberXThreadStacks(paged.items)
    val empty = pluginString(Strings.posts_empty)
    ScrollsToTop(list, toTop)
    ReadsMoreNearEnd(list, paged, feed.pages::more)
    PostScopeProvider {
        PullToRefreshBox(
            isRefreshing = paged.loading && paged.loaded,
            onRefresh = {
                feed.pages.refresh()
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                header()
                pagedRows(paged, feed.pages, XThread::id, X, empty) { thread ->
                    section.partStartingAt(thread.id)?.let { SectionHeader(pluginString(if (it == FollowedPart.FOLLOWED) Strings.posts_followed else Strings.posts_others)) }
                    // The posts sharing one article stack under the newest, as every list of X posts in XTA.
                    XThreadRow(thread, stacks) { shown ->
                        val signal = if (feed.signals) SignalGroups.cashtags(shown) else null
                        PostRow(shown, signal, folds[shown.id] ?: foldsByDefault, actions) { app.fold(shown.id, it) }
                    }
                }
            }
        }
    }
}

/** A thread, under the amber banner when it has a signal group's shape ([signal] cashtags). */
@Composable
private fun PostRow(thread: XThread, signal: Int?, folded: Boolean, actions: PostActions, onFold: (Boolean) -> Unit) {
    Column {
        if (signal == null) {
            ThreadRows(thread, actions)
        } else {
            SignalBanner(signal, folded) { onFold(!folded) }
            val still = LocalReducedMotion.current
            AnimatedVisibility(
                visible = !folded,
                enter = if (still) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (still) ExitTransition.None else shrinkVertically() + fadeOut(),
            ) {
                Column { ThreadRows(thread, actions) }
            }
        }
    }
}

/** "Possible signal group: Telegram invite and 4 cashtags" on an amber tint. Open, it ends in Hide; folded, the whole line opens the post again. */
@Composable
private fun SignalBanner(cashtags: Int, folded: Boolean, onToggle: () -> Unit) {
    val tint = Amber.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.background)
    val ink = warningColor(tint)
    val opens = if (folded) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier
    Row(
        Modifier.fillMaxWidth().background(tint).then(opens).heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Warning, null, Modifier.size(18.dp), tint = ink)
        Spacer(Modifier.width(8.dp))
        Text(
            pluginPlural(Strings.signal_group, cashtags, cashtags),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ink,
        )
        if (folded) {
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(horizontal = 12.dp).size(20.dp), tint = ink)
        } else {
            TextButton(onClick = onToggle, colors = ButtonDefaults.textButtonColors(contentColor = ink), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(pluginString(Strings.hide))
            }
        }
    }
}

/** The network the posts' problems name. */
private const val X = "X"
