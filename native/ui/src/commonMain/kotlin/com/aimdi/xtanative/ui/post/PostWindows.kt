package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * What opens in a window of its own beside the app's, as a browser opens a
 * link in a new window: a post by its id, a profile, a community, or what a link,
 * mention, hashtag or cashtag of a post's text leads to ([link], by its tag:
 * what opens it, or null for a page on the web, which stays the
 * browser's). Bound by the app's navigation, which knows the screens.
 */
internal class WindowOpens(
    val post: (id: String) -> Unit,
    val profile: (screenName: String, userId: String?) -> Unit,
    val community: (id: String) -> Unit,
    val link: (tag: String) -> (() -> Unit)?,
)

/** The [WindowOpens] of the app's screens; null where the app has the one window (Android), and outside its screens. */
internal val LocalWindowOpens = staticCompositionLocalOf<WindowOpens?> { null }

/** What a mouse press on a post card does about a window of its own. */
internal enum class PostPress {
    /** The card's own tap, or what is inside it, answers it. */
    TAP,

    /** The tap opens the post in a window of its own, rather than in this one: a click with Ctrl held. */
    TAP_IN_WINDOW,

    /** The post opens in a window of its own at once: a middle click that nothing inside the card (a link) took. */
    WINDOW,
}

internal object PostPresses {
    /** A press of the [primary] or [middle] button with [ctrl] held or not, [taken] already by what is inside the card. */
    fun of(primary: Boolean, middle: Boolean, ctrl: Boolean, taken: Boolean): PostPress = when {
        middle && !taken -> PostPress.WINDOW
        primary && ctrl -> PostPress.TAP_IN_WINDOW
        else -> PostPress.TAP
    }
}

/** The last mouse press on a card, for its tap to read as it opens the post. */
internal class CardPresses {
    var last: PostPress = PostPress.TAP

    /**
     * [last], read once by the tap it belongs to: put back to a plain tap,
     * so Enter on the card a Ctrl+click left focused opens the post here,
     * not in another window.
     */
    fun take(): PostPress = last.also { last = PostPress.TAP }
}

/**
 * A middle click on this opens [open] (the post in a window of its own),
 * and a click with Ctrl held is marked in [presses] for the card's tap to
 * read. A middle click on a link inside is the link's (it opens in a
 * window of its own, or the browser); touches are left alone. Nothing without [open].
 */
@Composable
internal fun Modifier.opensInWindow(open: (() -> Unit)?, presses: CardPresses): Modifier {
    if (open == null) return this
    val latest by rememberUpdatedState(open)
    return pointerInput(presses) {
        awaitPointerEventScope {
            while (true) {
                // Main: the innermost first, so a link that took a middle click has consumed it by now.
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type != PointerEventType.Press) continue
                val change = event.changes.firstOrNull() ?: continue
                val press = if (change.type != PointerType.Mouse) {
                    PostPress.TAP
                } else {
                    PostPresses.of(event.buttons.isPrimaryPressed, event.buttons.isTertiaryPressed, event.keyboardModifiers.isCtrlPressed, change.isConsumed)
                }
                presses.last = press
                if (press == PostPress.WINDOW) {
                    change.consume()
                    latest()
                }
            }
        }
    }
}

/**
 * A click on this does [onClick]; a click with Ctrl held, or a middle click,
 * does [inWindow] instead (what it opens, in a window of its own), as a post
 * card's own do. Without [inWindow], a plain click.
 */
@Composable
internal fun Modifier.clickableOrWindow(inWindow: (() -> Unit)?, onClick: () -> Unit): Modifier {
    val presses = remember { CardPresses() }
    val latest by rememberUpdatedState(inWindow)
    return opensInWindow(inWindow, presses).clickable {
        val window = latest
        if (presses.take() == PostPress.TAP_IN_WINDOW && window != null) window() else onClick()
    }
}

/** The post menu's Open in new window: Material's web_asset (Apache 2.0), a window, tinted where it is drawn. */
internal val NewWindowIcon: ImageVector by lazy {
    ImageVector.Builder(name = "NewWindow", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString("M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.89,-2 -2,-2zM19,18H5V8h14v10z").toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
