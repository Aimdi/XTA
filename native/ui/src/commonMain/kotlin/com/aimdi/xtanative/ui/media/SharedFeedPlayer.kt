package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * [content]'s tiles taking turns at one player ([rememberInlinePlayer]), as
 * a feed's do: a video started pauses the one that played, and none plays
 * while the page is out of sight. Without it each tile makes a player of its
 * own and several play at once.
 */
@Composable
fun SharedFeedPlayer(content: @Composable () -> Unit) {
    val inline = rememberInlinePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inline?.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inline?.resume() }
    CompositionLocalProvider(LocalInlinePlayer provides inline, content = content)
}
