package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aimdi.xtanative.ui.media.mpris.MprisSessions

/** The desktop's media session: an MPRIS player on the session bus while something is bound ([MprisSessions]). */
@Composable
actual fun rememberAudioSessions(): AudioSessions = MprisSessions.service

/**
 * libmpv's players for a podcast episode (as XTA's media_kit plays them on
 * Linux), where the desktop has libmpv; none without it, and an episode
 * opens in the system's player instead.
 */
@Composable
actual fun rememberContinuityPlayers(): (() -> ContinuityPlayer)? =
    remember { if (MpvEngine.ready) ({ MpvContinuityPlayer.create() }) else null }
