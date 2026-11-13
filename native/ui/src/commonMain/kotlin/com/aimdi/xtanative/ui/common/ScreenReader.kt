package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/**
 * Whether a screen reader is on (XTA's MediaQuery.accessibleNavigation):
 * Android's touch exploration (TalkBack and its kind), following it as it is
 * switched; never on the desktop, which tells no such thing. With one on,
 * Home's bars stay put, as a bar that moves is a bar that gets lost.
 */
@Composable
expect fun rememberScreenReaderOn(): Boolean
