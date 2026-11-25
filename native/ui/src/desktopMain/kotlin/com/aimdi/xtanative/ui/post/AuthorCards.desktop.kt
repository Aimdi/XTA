package com.aimdi.xtanative.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the pointer rests before the card shows, as the app's tooltips wait (IconTip). */
private const val SHOW_DELAY_MS = 600L

/** How long the card stays once the pointer has left, so it can cross to the card and click it. */
private const val HIDE_DELAY_MS = 300L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun AuthorHoverCard(user: XUser, avatarUrl: String?, onOpen: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    // Plain holders, not state: they only steer the timer below.
    val over = remember { BooleanArray(2) }
    var timer by remember { mutableStateOf<Job?>(null) }
    fun settle() {
        timer?.cancel()
        val wanted = over[0] || over[1]
        if (wanted == shown) return
        timer = scope.launch {
            delay(if (wanted) SHOW_DELAY_MS else HIDE_DELAY_MS)
            shown = wanted
        }
    }
    fun hide() {
        timer?.cancel()
        over[1] = false
        shown = false
    }
    Box(
        Modifier
            .onPointerEvent(PointerEventType.Enter) { over[0] = true; settle() }
            .onPointerEvent(PointerEventType.Exit) { over[0] = false; settle() }
            // A click on the author does what it did, and the card makes way for it.
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { over[0] = false; hide() },
    ) {
        content()
        // Faded in and out as the link hover's pill is; at once while motion is reduced.
        val reduced = LocalReducedMotion.current
        val visible = remember { MutableTransitionState(false) }
        visible.targetState = shown
        if (shown || (!reduced && !visible.isIdle)) {
            val density = LocalDensity.current
            Popup(
                popupPositionProvider = remember(density) { BesideAuthor(density) },
                onDismissRequest = ::hide,
                properties = PopupProperties(focusable = false),
            ) {
                AnimatedVisibility(visible, enter = Motion.fadeEnter(reduced), exit = Motion.fadeExit(reduced)) {
                    Box(
                        Modifier
                            .onPointerEvent(PointerEventType.Enter) { over[1] = true; settle() }
                            .onPointerEvent(PointerEventType.Exit) { over[1] = false; settle() },
                    ) { AuthorCardBody(user, avatarUrl) { hide(); onOpen() } }
                }
            }
        }
    }
}

/** Under the author, or above where the window's foot is near ([AuthorCard.position]). */
private class BesideAuthor(density: Density) : PopupPositionProvider {
    private val gap = with(density) { 4.dp.roundToPx() }
    private val margin = with(density) { 8.dp.roundToPx() }

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        AuthorCard.position(anchorBounds, windowSize, popupContentSize, gap, margin)
}
