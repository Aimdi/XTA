package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The desktop has no player of its own: a video opens on tiktok.com. */
internal actual val inAppPlayer: Boolean = false

@Composable
internal actual fun DirectPlayer(url: String, headers: Map<String, String>, modifier: Modifier, onError: () -> Unit) = Unit

@Composable
internal actual fun EmbedPlayer(url: String, cookies: Map<String, String>, userAgent: String, modifier: Modifier) = Unit
