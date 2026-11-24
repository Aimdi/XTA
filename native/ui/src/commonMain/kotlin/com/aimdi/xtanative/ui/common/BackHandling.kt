package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/**
 * While [enabled], the system's Back runs [onBack] instead of leaving the
 * screen, as XTA's screens close an open search or a settings page first.
 * The desktop has no system Back: there, while the screen is the one shown,
 * Esc, Alt+← and the mouse's back button do it, after what the screen
 * itself has open over it ([com.aimdi.xtanative.ui.home.DesktopShortcuts])
 * and, for Esc, after leaving a field being typed in; the last enabled is
 * the first run. [byKeys] false leaves those keys out of it, where the
 * back would close the app or what holds the keys itself.
 */
@Composable
internal expect fun BackHandling(enabled: Boolean, byKeys: Boolean = true, onBack: () -> Unit)
