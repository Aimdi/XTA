package com.aimdi.xtanative.plugins.threads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalWindowInfo
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.rememberInlinePlayer

/**
 * A Threads list's one video player, as Home's X feeds have one
 * (`ui.media`'s FeedPlayer): the video tiles of [content] take turns at it,
 * so with Autoplay videos on the one seen most plays by itself, muted as
 * the app's mute stands, its corner counting down, and no two play at once;
 * a tap opens it full screen with its sound, and a tile that comes back
 * plays on from where it was. Without it each tile has a player of its own
 * and plays whenever most of it is in view, as an opened X post's does.
 * While the window is not the one in front (the app left, a sheet over the
 * list, another window on the desktop) the list's video waits, but one full
 * screen or heard under Background play; inside a list that has a player
 * already, [content] plays in that one.
 */
@Composable
internal fun PlaysOneVideo(content: @Composable () -> Unit) {
    if (LocalInlinePlayer.current != null) return content()
    val player = rememberInlinePlayer()
    val seen = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(player, seen) {
        if (seen) player?.resume() else player?.pause()
    }
    CompositionLocalProvider(LocalInlinePlayer provides player, content = content)
}

/** Which Threads screens are lists whose videos take turns at one player ([PlaysOneVideo]). */
internal object ThreadsFeedVideo {
    /**
     * Whether [route]'s screen is such a list: a profile, a search, a
     * community, the posts that quote one, the reader's own lists and
     * Saved. An opened post is none, as X's is not: its videos are the ones
     * the reader came for, and each plays in its tile; the viewer, the
     * lists of people and the sign-in page show no tiles.
     */
    fun shared(route: ThreadsRoute): Boolean = when (route) {
        is ThreadsRoute.Profile, is ThreadsRoute.Search, is ThreadsRoute.Community, is ThreadsRoute.Quotes, is ThreadsRoute.OwnFeed -> true
        ThreadsRoute.Saved, ThreadsRoute.Activity -> true
        is ThreadsRoute.Post, is ThreadsRoute.Media, is ThreadsRoute.People, is ThreadsRoute.Likers, is ThreadsRoute.Reposters -> false
        ThreadsRoute.SignIn -> false
    }
}
