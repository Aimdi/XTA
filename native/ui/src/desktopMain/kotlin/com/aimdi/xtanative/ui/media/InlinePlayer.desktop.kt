package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

/** libmpv's player for the whole feed, shared by its tiles ([FeedPlayer]), where the desktop has libmpv; none without it, and a tile keeps its poster. */
@Composable
actual fun rememberInlinePlayer(): InlinePlayer? = rememberVideoPlayers()?.let { rememberFeedPlayer(it) }
