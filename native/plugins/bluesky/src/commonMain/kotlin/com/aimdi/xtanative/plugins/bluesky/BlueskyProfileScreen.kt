package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyProfileTab
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.LinkedText
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.pagedRows
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A Bluesky profile, as bsky.app shows it: its banner, picture, names,
 * description and counts, Follow (here only), and its Posts (the pinned post
 * first), Replies and Media, each read page by page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BlueskyProfileScreen(app: BlueskyApp, actor: String) {
    val profile = rememberKept("bluesky-profile:$actor") { scope -> ProfileState(app, actor, scope) }
    val state by profile.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(BlueskyProfileTab.POSTS) }
    val shown = state.profile
    val pages = remember(shown, tab) { profile.pages(tab) }
    val list = rememberLazyListState()
    val empty = PagedState<BlueskyPost>()
    val paged = pages?.state?.collectAsState()?.value ?: empty
    if (pages != null) ReadsMoreNearEnd(list, paged) { pages.more() }
    val followed = shown != null && follows.orEmpty().any { it.isOf(shown.did) || it.isOf(shown.handle) }
    val noPosts = pluginString(Strings.profile_empty)

    PluginScaffold(title = shown?.displayName ?: actor, onBack = app.host::back) { padding ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 48.dp),
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            item(key = "header") {
                Header(app, shown, actor, followed) {
                    if (shown != null) app.launch { if (followed) app.follows.unfollow(shown.actor) else app.follows.follow(shown, System.currentTimeMillis()) }
                }
            }
            val problem = state.problem
            when {
                problem != null -> item(key = "problem") { NetworkProblemView(problem, app.network, onRetry = profile::load) }
                state.loading -> item(key = "loading") { LoadingRow() }
            }
            if (shown != null && pages != null) {
                stickyHeader(key = "tabs") { Tabs(tab) { tab = it } }
                pagedRows(paged, pages, key = { it.uri }, network = app.network, empty = noPosts) { post ->
                    PostRow(app, post, pinned = tab == BlueskyProfileTab.POSTS && post.uri == shown.pinnedPostUri)
                }
            }
        }
    }
}

@Composable
private fun Header(app: BlueskyApp, profile: BlueskyProfile?, actor: String, followed: Boolean, onFollow: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = profile?.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Avatar(
                profile?.avatarUrl,
                80.dp,
                Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = 40.dp).border(4.dp, background, CircleShape),
            )
        }
        // Beside the picture's lower half, as bsky.app places it.
        Box(Modifier.fillMaxWidth().height(52.dp).padding(start = 112.dp, end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            if (profile != null) {
                if (followed) {
                    OutlinedButton(onClick = onFollow) { Text(pluginString(Strings.unfollow)) }
                } else {
                    Button(onClick = onFollow) { Text(pluginString(Strings.follow)) }
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(
                    profile?.displayName ?: actor,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("@" + (profile?.handle ?: actor), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (profile != null && profile.description.isNotBlank()) {
                val description = remember(profile) { profile.descriptionRuns() }
                LinkedText(description, app.actions.openMention, app.actions.openTag)
            }
            if (profile != null) Counts(profile)
            if (followed) {
                Text(
                    pluginString(Strings.followed_here),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
    }
}

/** Followers, following and posts, as numbers to read. */
@Composable
private fun Counts(profile: BlueskyProfile) {
    val strong = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(
            profile.followersCount to Strings.profile_followers,
            profile.followsCount to Strings.profile_follows,
            profile.postsCount to Strings.profile_post_count,
        ).forEach { (n, label: PluginText) ->
            val number = countText(n)
            val word = pluginString(label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                    append(" ")
                    append(word)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Tabs(selected: BlueskyProfileTab, onSelect: (BlueskyProfileTab) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal, containerColor = MaterialTheme.colorScheme.background, divider = { Hairline() }) {
        BlueskyProfileTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        pluginString(
                            when (tab) {
                                BlueskyProfileTab.POSTS -> Strings.profile_posts
                                BlueskyProfileTab.REPLIES -> Strings.profile_replies
                                BlueskyProfileTab.MEDIA -> Strings.profile_media
                            },
                        ),
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One profile, read once the screen opens, and a paged list per tab, made the first time the tab is shown. */
internal class ProfileState(private val app: BlueskyApp, private val actor: String, private val scope: CoroutineScope) {
    data class State(val profile: BlueskyProfile? = null, val loading: Boolean = true, val problem: Problem? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private val tabs = HashMap<BlueskyProfileTab, PagedItems<BlueskyPost>>()

    init {
        load()
    }

    fun load() {
        scope.launch {
            mutable.update { it.copy(loading = true, problem = null) }
            try {
                mutable.value = State(app.client.profile(actor), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, problem = problemOf(e)) }
            }
        }
    }

    /** [tab]'s posts, once the profile is known. */
    fun pages(tab: BlueskyProfileTab): PagedItems<BlueskyPost>? {
        val profile = state.value.profile ?: return null
        return tabs.getOrPut(tab) { PagedItems(scope, key = { it.uri }) { cursor -> read(profile, tab, cursor) } }
    }

    /** A page of [tab]; the first page of Posts starts with the pinned post. */
    private suspend fun read(profile: BlueskyProfile, tab: BlueskyProfileTab, cursor: String?): Page<BlueskyPost> {
        val page = app.client.profileTab(profile, tab, cursor).shown()
        if (cursor != null || tab != BlueskyProfileTab.POSTS) return page
        return Page(tab.withPin(page.items, pinned(profile)), page.next)
    }

    private suspend fun pinned(profile: BlueskyProfile): BlueskyPost? = try {
        app.client.pinnedPost(profile)?.takeUnless { it.isUnavailable }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
