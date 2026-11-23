package com.aimdi.xtanative.ui.theme

import androidx.compose.runtime.Composable

/**
 * What the platform draws itself in the X Look of [androidx.compose.material3.MaterialTheme]
 * above: on the desktop, the right-click menu of text (Copy, Select all) and
 * of a highlight, which are otherwise light on every background. Nothing
 * more on Android, whose text menus follow the system.
 */
@Composable
internal expect fun ProvidePlatformLook(content: @Composable () -> Unit)
