package com.aimdi.xtanative.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A question's default button: the one that takes the focus as its dialog
 * opens, so Enter (or Space) presses it from the keyboard and Esc still
 * dismisses. As GNOME's guidelines ask, a destructive answer (delete,
 * remove, uninstall, discard, clear) is never the default: such dialogs
 * give the focus to Cancel, and the reader Tabs to the destructive button
 * on purpose. Questions that lose nothing (quit, show, keep, OK, Close)
 * default to their confirm button.
 */
@Composable
fun Modifier.dialogDefault(): Modifier {
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    // Once the dialog's window has the keys (or after a moment, where it never says so), so the focus
    // arrives as the keyboard's and shows its ring; a missed request leaves the dialog as it was.
    LaunchedEffect(focus, window) {
        withTimeoutOrNull(WINDOW_FOCUS_WAIT_MS) { snapshotFlow { window.isWindowFocused }.first { it } }
        runCatching { focus.requestFocus() }
    }
    return focusRequester(focus)
}

/**
 * A destructive answer's text button (delete, remove, discard, clear,
 * uninstall) in the theme's error colour, so it reads apart from the Cancel
 * beside it, which stays the default ([dialogDefault]).
 */
@Composable
fun destructiveTextColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)

/** [destructiveTextColors] for a filled button: the error colour behind its words. */
@Composable
fun destructiveButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)

/** How long a default button waits for its dialog's window to take the keys. */
private const val WINDOW_FOCUS_WAIT_MS = 500L
