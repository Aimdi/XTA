package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/**
 * While [enabled], the system's Back runs [onBack] instead of leaving the
 * screen, as XTA's screens close an open search or a settings page first.
 * The desktop has no system Back: there the bar's own back arrow does it.
 */
@Composable
internal expect fun BackHandling(enabled: Boolean, onBack: () -> Unit)
