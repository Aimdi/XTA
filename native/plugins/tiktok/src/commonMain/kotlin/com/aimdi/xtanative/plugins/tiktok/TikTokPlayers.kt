package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Whether this platform plays TikTok's videos in the app: Android, with its
 * own player and TikTok's embedded one in a web view. The desktop has
 * neither, and opens a video on tiktok.com, as XTA's desktop does.
 */
internal expect val inAppPlayer: Boolean

/** The video file at [url], asked for with [headers] (TikTok's CDN wants its referer and the guest cookies), looping; [onError] when it will not play. */
@Composable
internal expect fun DirectPlayer(url: String, headers: Map<String, String>, modifier: Modifier, onError: () -> Unit)

/** TikTok's own player at [url] (`embed/v3/<id>`), with the guest [cookies] and the browser the client reads as ([userAgent]). */
@Composable
internal expect fun EmbedPlayer(url: String, cookies: Map<String, String>, userAgent: String, modifier: Modifier)
