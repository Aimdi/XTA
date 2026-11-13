package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/** The desktop has no system Back to close the window with: its own close button does. */
@Composable
internal actual fun rememberCloseApp(): () -> Unit = {}
