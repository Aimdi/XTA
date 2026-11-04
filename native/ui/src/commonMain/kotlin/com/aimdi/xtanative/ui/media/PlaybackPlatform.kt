package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

/** The app's one media session, for whatever plays without a screen (a podcast, a reading); [AudioSessions.None] on the desktop. */
@Composable
expect fun rememberAudioSessions(): AudioSessions

/**
 * A way to make players that keep a podcast episode going across screens
 * (Media3's on Android); null on the desktop, which plays none. Whoever
 * makes one disposes of it.
 */
@Composable
expect fun rememberContinuityPlayers(): (() -> ContinuityPlayer)?
