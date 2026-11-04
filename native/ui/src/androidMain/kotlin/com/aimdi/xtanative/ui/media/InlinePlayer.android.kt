package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

/** Media3's player for the whole feed, shared by its tiles ([FeedPlayer]). */
@Composable
actual fun rememberInlinePlayer(): InlinePlayer? = rememberVideoPlayers()?.let { rememberFeedPlayer(it) }
