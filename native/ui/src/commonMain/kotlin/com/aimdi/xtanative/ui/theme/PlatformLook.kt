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

/** Whether the system prefers dark, for the "System" background: Android's night mode, the desktop's settings portal. */
@Composable
internal expect fun systemPrefersDark(): Boolean

/**
 * Whether the system's own settings turn animations off: the desktop's, as
 * its settings portal says; on Android false, where "Remove animations"
 * already stops Compose's animations.
 */
@Composable
internal expect fun systemReducesMotion(): Boolean

/**
 * The system's accent colour as opaque ARGB, for the "System" accent: the
 * desktop's, as its settings portal says and followed as it changes; null
 * where nothing says, and on Android, which has no dynamic colour here.
 */
@Composable
internal expect fun systemAccent(): Long?
