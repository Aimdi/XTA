package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.aimdi.xtanative.ui.common.RowIndication
import com.aimdi.xtanative.ui.common.SelectingTextMenu
import com.aimdi.xtanative.ui.post.AppContextMenu

/** How strongly an entry under the pointer is lit, as Material's hovered state lights it. */
private const val HOVER_ALPHA = 0.08f

/**
 * How Material's buttons light under the pointer, pressed and with the
 * keyboard's focus. Material's own focus (0.1, as faint as a press) is all a
 * Save or Cancel button showed when Tab reached it, hard to see on a filled
 * button; here the focus is three times that, well apart from the pointer
 * resting on it. A click focuses a button too, so this is only for a focus
 * the keyboard moved ([KeyboardFocusLook]); a clicked one keeps
 * [MouseRipples]. Material's buttons draw their own ripple and read only
 * this, so it reaches every button, in dialogs and sheets too.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal val DesktopRipples = RippleConfiguration(
    rippleAlpha = RippleAlpha(pressedAlpha = 0.1f, focusedAlpha = 0.3f, draggedAlpha = 0.16f, hoveredAlpha = HOVER_ALPHA),
)

/** [DesktopRipples] for a focus a click gave: Material's own faint focus, as faint as the press. */
@OptIn(ExperimentalMaterial3Api::class)
internal val MouseRipples = RippleConfiguration(
    rippleAlpha = RippleAlpha(pressedAlpha = 0.1f, focusedAlpha = 0.1f, draggedAlpha = 0.16f, hoveredAlpha = HOVER_ALPHA),
)

/**
 * [DesktopRipples] for a control that draws the keyboard's ring itself (an
 * icon button with a tip, a post's author picture): its focus shows as that
 * accent ring alone, not also as a grey disc under it, as the app's rows show
 * it ([com.aimdi.xtanative.ui.common.RowIndication]). A click's focus shows
 * nothing either; the pointer and a press light it as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal val RingedRipples = RippleConfiguration(
    rippleAlpha = RippleAlpha(pressedAlpha = 0.1f, focusedAlpha = 0f, draggedAlpha = 0.16f, hoveredAlpha = HOVER_ALPHA),
)

/**
 * The app's ripples, read when the button's focus arrives, not when this is
 * provided: the click or Tab that moved it has already set the mode by then.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal val AppRipples = LocalRippleConfiguration providesComputed { if (KeyboardFocusLook.keyboard) DesktopRipples else MouseRipples }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal actual fun ProvidePlatformLook(content: @Composable () -> Unit) {
    KeyboardFocusLook.start()
    val colors = MaterialTheme.colorScheme
    // Read when a row draws, not here: a key or a click turning the mode recomposes nothing above the rows.
    val keyboard = remember { derivedStateOf { KeyboardFocusLook.keyboard } }
    val rows = remember(colors.onSurface, colors.primary) { RowIndication(colors.onSurface, colors.primary, keyboard) }
    val textMenu = LocalTextContextMenu.current
    val selecting = remember(textMenu) { if (textMenu is SelectingTextMenu) textMenu else SelectingTextMenu(textMenu) }
    CompositionLocalProvider(
        // Selected text's menu (Copy, Select all) drawn as the app's own pointer menus are.
        LocalContextMenuRepresentation provides AppContextMenu,
        LocalTextContextMenu provides selecting,
        // Plain clickable rows (settings, dialog options, tabs) lit as rounded inset blocks with the keyboard's ring.
        LocalIndication provides rows,
        AppRipples,
        content = content,
    )
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

/** The desktop's accent colour, as its settings portal says, followed as it changes; null where it says none. */
@Composable
internal actual fun systemAccent(): Long? {
    val scheme = DesktopColorScheme.desktop
    LaunchedEffect(scheme) { scheme.start() }
    return scheme.accent.collectAsState().value
}

/** Whether the desktop asks for higher contrast, as its settings portal says, followed as it changes. */
@Composable
internal actual fun systemHighContrast(): Boolean {
    val scheme = DesktopColorScheme.desktop
    LaunchedEffect(scheme) { scheme.start() }
    return scheme.highContrast.collectAsState().value
}

/** Whether the keyboard moved the focus last, followed as it changes. */
@Composable
internal actual fun keyboardFocusShown(): Boolean = KeyboardFocusLook.keyboard
