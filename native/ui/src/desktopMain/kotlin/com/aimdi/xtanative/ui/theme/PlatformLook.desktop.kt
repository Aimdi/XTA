package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

/** How strongly an entry under the pointer is lit, as Material's hovered state lights it. */
private const val HOVER_ALPHA = 0.08f

@Composable
internal actual fun ProvidePlatformLook(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val menus = remember(colors.surfaceContainer, colors.onSurface) {
        DefaultContextMenuRepresentation(
            backgroundColor = colors.surfaceContainer,
            textColor = colors.onSurface,
            itemHoverColor = colors.onSurface.copy(alpha = HOVER_ALPHA),
        )
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides menus, content = content)
}

/** The desktop's color scheme as its settings portal says, followed as it changes; what Java says where nothing does. */
@Composable
internal actual fun systemPrefersDark(): Boolean {
    val scheme = DesktopColorScheme.desktop
    LaunchedEffect(scheme) { scheme.start() }
    val portal = scheme.dark.collectAsState().value
    return portal ?: isSystemInDarkTheme()
}

/** Whether the desktop's own settings turn animations off, as its settings portal says, followed as it changes. */
@Composable
internal actual fun systemReducesMotion(): Boolean {
    val scheme = DesktopColorScheme.desktop
    LaunchedEffect(scheme) { scheme.start() }
    return scheme.animationsOff.collectAsState().value
}
