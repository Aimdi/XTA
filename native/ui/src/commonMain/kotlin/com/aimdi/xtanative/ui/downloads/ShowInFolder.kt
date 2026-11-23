package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable

/**
 * Shows a finished download in the desktop's file manager, the file
 * picked out in its folder, given its saved address; false when that
 * failed. Null where the platform has no such thing (Android, whose
 * "Open file" hands it to an app).
 */
@Composable
internal expect fun rememberShowInFolder(): (suspend (savedUri: String) -> Boolean)?
