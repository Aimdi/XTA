package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.runtime.Composable

/** A desktop window has no screen timeout or system bars of its own to change. */
@Composable
internal actual fun ReaderWindow(keepOn: Boolean, barsHidden: Boolean) = Unit
