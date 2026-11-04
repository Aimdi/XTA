package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** No media session on the desktop, as XTA has none there: what plays has its controls in the app. */
@Composable
actual fun rememberAudioSessions(): AudioSessions = AudioSessions.None

/**
 * libmpv's players for a podcast episode (as XTA's media_kit plays them on
 * Linux), where the desktop has libmpv; none without it, and an episode
 * opens in the system's player instead.
 */
@Composable
actual fun rememberContinuityPlayers(): (() -> ContinuityPlayer)? =
    remember { if (MpvEngine.ready) ({ MpvContinuityPlayer.create() }) else null }
