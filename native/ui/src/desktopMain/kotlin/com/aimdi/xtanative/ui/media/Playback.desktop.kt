package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

@Composable
actual fun rememberAudioSessions(): AudioSessions = AudioSessions.None

/** The desktop plays no podcast in the app: an episode opens in the system's player instead. */
@Composable
actual fun rememberContinuityPlayers(): (() -> ContinuityPlayer)? = null
