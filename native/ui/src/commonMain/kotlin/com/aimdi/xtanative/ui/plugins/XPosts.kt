package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.plugins.Destination
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
