package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.ui.feed.FeedLayout
import com.aimdi.xtanative.ui.feed.FeedPart
import com.aimdi.xtanative.ui.feed.LinkStackRow
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.post.PostActions

/**
 * Where the parts of the X posts a plugin shows lead, drawn as the app's own
 * feeds draw them (`ui.post`'s ThreadRows): the app's post, profile and
 * search screens, a post's Quotes and Reposted by tabs and its article as
 * the app's feeds open them, a link as the app opens links, and the media viewer with
 * the media of the post tapped, found among [threads]. Saves and likes stay
 * on the device, as everywhere ([PostActions.keep]).
 */
@Composable
fun rememberXPostActions(host: PluginHost, threads: List<XThread>): PostActions {
    val navigator = LocalPlugins.current.navigator
    val outside = rememberOpenOutside()
    val shown by rememberUpdatedState(threads)
    return remember(host, navigator, outside) {
        PostActions(
            openPost = host::openPost,
            openProfile = host::openProfile,
            openMedia = { id, index -> XPostMedia.find(shown, id)?.let { host.openMedia(XPostMedia.items(it), index) } },
            search = { query -> navigator.go(Destination.XSearch(query)) },
            openQuotes = { id -> navigator.go(Destination.XQuotes(id)) },
            openReposters = { id -> navigator.go(Destination.XReposters(id)) },
            openArticle = { id -> navigator.go(Destination.XArticle(id)) },
            openFollows = { screenName, _ -> host.openProfile(screenName) },
            openLink = host::openLink,
            openInBrowser = outside,
            openImage = { url, _ -> host.openMedia(listOf(MediaItem(url)), 0) },
        )
    }
}

/**
 * The X threads of a plugin's list as the app's feeds lay them out (XTA's
 * feed_link_grouping.dart): the posts sharing one article are stacked under
 * the newest of them. [rememberXThreadStacks] makes it of the threads shown;
 * [XThreadRow] draws one thread's place in it.
 */
class XThreadStacks internal constructor(internal val layout: FeedLayout, internal val threads: Map<String, XThread>)

/** [threads] stacked by the article they share. */
@Composable
fun rememberXThreadStacks(threads: List<XThread>): XThreadStacks =
    remember(threads) { XThreadStacks(FeedLayout.of(threads, collapseReposts = false), threads.associateBy(FeedLayout::xKey)) }

/**
 * What a list draws in [thread]'s place: the thread itself ([row]); the
 * newest of the posts sharing its article over "More posts sharing this
 * link", which lays the others out; or nothing, for one shown in a stack
 * above.
 */
@Composable
fun XThreadRow(thread: XThread, stacks: XThreadStacks, row: @Composable (XThread) -> Unit) {
    when (val part = stacks.layout.ofThread(0, thread)) {
        FeedPart.Hidden -> Unit
        is FeedPart.Stack -> LinkStackRow(part.keys) { key -> stacks.threads[key]?.let { row(it) } }
        else -> row(thread)
    }
}
