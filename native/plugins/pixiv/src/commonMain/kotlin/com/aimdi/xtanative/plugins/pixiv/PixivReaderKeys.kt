package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/** What a key does in the reader on the desktop. */
internal enum class ReaderKey { PREVIOUS, NEXT, FIRST, LAST, LINE_UP, LINE_DOWN, SCREEN_UP, SCREEN_DOWN, ZOOM_IN, ZOOM_OUT, ZOOM_RESET, DOWNLOAD, HD, NONE }

/**
 * The reader's keys, as the app's media viewer has them: ← and → and Page Up
 * and Down turn the page (← goes on when the pages read right to left),
 * Home and End go to the first and last, ↑ ↓ and Space (with Shift, back)
 * move the column, + − and 0 zoom the page, D saves it and H switches
 * original quality. Esc is the app's: it closes the reader. Nothing with
 * Ctrl, Alt or Meta held, which are the app's keys too.
 */
internal object ReaderKeys {
    fun of(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, rightToLeft: Boolean): ReaderKey = when {
        ctrl || alt -> ReaderKey.NONE
        key == Key.Equals || key == Key.Plus || key == Key.NumPadAdd -> ReaderKey.ZOOM_IN
        key == Key.Spacebar -> if (shift) ReaderKey.SCREEN_UP else ReaderKey.SCREEN_DOWN
        shift -> ReaderKey.NONE
        key == Key.DirectionLeft -> if (rightToLeft) ReaderKey.NEXT else ReaderKey.PREVIOUS
        key == Key.DirectionRight -> if (rightToLeft) ReaderKey.PREVIOUS else ReaderKey.NEXT
        else -> PLAIN[key] ?: ReaderKey.NONE
    }

    private val PLAIN = mapOf(
        Key.PageUp to ReaderKey.PREVIOUS,
        Key.PageDown to ReaderKey.NEXT,
        Key.MoveHome to ReaderKey.FIRST,
        Key.MoveEnd to ReaderKey.LAST,
        Key.DirectionUp to ReaderKey.LINE_UP,
        Key.DirectionDown to ReaderKey.LINE_DOWN,
        Key.Minus to ReaderKey.ZOOM_OUT,
        Key.NumPadSubtract to ReaderKey.ZOOM_OUT,
        Key.Zero to ReaderKey.ZOOM_RESET,
        Key.NumPad0 to ReaderKey.ZOOM_RESET,
        Key.D to ReaderKey.DOWNLOAD,
        Key.H to ReaderKey.HD,
    )

    /** The keys that turn pages. */
    val TURNS = setOf(ReaderKey.PREVIOUS, ReaderKey.NEXT, ReaderKey.FIRST, ReaderKey.LAST)

    /** The page [key] goes to from [page] of [pages]; null when it turns no page or there is none that way. */
    fun target(key: ReaderKey, page: Int, pages: Int): Int? = when (key) {
        ReaderKey.PREVIOUS -> page - 1
        ReaderKey.NEXT -> page + 1
        ReaderKey.FIRST -> 0
        ReaderKey.LAST -> pages - 1
        else -> null
    }?.takeIf { it in 0 until pages && it != page }
}

/**
 * The reader's keys ([ReaderKeys]) for what is inside: the focus taken as it
 * opens and again once [settled] (a sheet or the overview closed over it,
 * which hands the keys back to the window), each key given to [onKey].
 */
@Composable
internal fun Modifier.readerKeys(settled: Boolean, rightToLeft: Boolean, onKey: (ReaderKey) -> Boolean): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        // After the window's own hand-back, which takes a frame or two.
        delay(REFOCUS_DELAY_MS)
        runCatching { focus.requestFocus() }
    }
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.isMetaPressed) return@onKeyEvent false
        val key = ReaderKeys.of(event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, rightToLeft)
        key != ReaderKey.NONE && onKey(key)
    }.focusRequester(focus).focusable()
}

private const val REFOCUS_DELAY_MS = 150L
