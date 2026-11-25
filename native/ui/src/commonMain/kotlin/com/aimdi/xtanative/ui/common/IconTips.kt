package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * [content], an icon button, with [label] shown beside the pointer while it
 * rests on it: on the desktop a mouse has no long press to ask what an icon
 * is. The label is the one the button already gives screen readers. Nothing
 * more on Android.
 */
@Composable
expect fun IconTip(label: String, content: @Composable () -> Unit)

/**
 * The ring [IconTip] draws round an icon button the keyboard moved to, for
 * a control drawn without one (a round play button, a toggle): on the
 * desktop only, and never for a focus a click gave. Nothing on Android.
 */
@Composable
internal expect fun Modifier.keyboardFocusRing(): Modifier

/**
 * The same ring, drawn [outset] beyond the bounds, for a word that is a
 * button ("Show more"): the icon ring's inset would cross its letters.
 */
@Composable
internal expect fun Modifier.keyboardFocusRing(outset: Dp): Modifier

/**
 * Focus that a click may give but Tab passes by: selectable text takes the
 * focus when the mouse selects in it, so Ctrl+C copies the selection, but
 * the keys can neither select nor copy there, so a Tab stop on it showed
 * nothing and did nothing. Nothing on Android, where no key moves it.
 */
internal expect fun Modifier.pointerOnlyFocus(): Modifier
