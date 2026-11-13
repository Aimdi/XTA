package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.runtime.Composable

/**
 * While the reader shows: the screen stays on when [keepOn] (XTA's "Keep
 * screen on while reading"), and the system bars hide when [barsHidden], as
 * XTA hides them with the reader's own (immersive). The desktop has neither
 * to change.
 */
@Composable
internal expect fun ReaderWindow(keepOn: Boolean, barsHidden: Boolean)
