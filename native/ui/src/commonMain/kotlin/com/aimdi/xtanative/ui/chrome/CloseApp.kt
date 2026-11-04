package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable

/**
 * Closes the app, as XTA's SystemNavigator.pop does: Android's activity
 * finishes, the desktop's window closes as its close button closes it (the
 * writes it asked for are then finished before the process ends).
 */
@Composable
internal expect fun rememberCloseApp(): () -> Unit
