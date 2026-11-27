package com.aimdi.xtanative.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.LocalSheetCloser
import com.aimdi.xtanative.ui.common.Motion
import com.aimdi.xtanative.ui.common.SheetCloser
import com.aimdi.xtanative.ui.post.LocalMenuRowMetrics
import com.aimdi.xtanative.ui.post.MenuRowMetrics
import com.aimdi.xtanative.ui.theme.keyboardFocusShown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A button that opens the timeline switcher ([switcherButton]), by its part
 * of Home (Following's picker, a timeline's mark, a network's avatar): where
 * it stands, followed as it moves ([bounds], in the window) and as the
 * button is made afresh in another layout (the window narrowed to the rail,
 * a column fewer), so the desktop's switcher drops down under it rather
 * than rising as a sheet from the window's foot, and the focus that goes
 * back to it once the switcher closes ([focus]).
 */
@Stable
internal class SwitcherButton {
    /** The button's bounds in the window as last laid out; null before. */
    var bounds by mutableStateOf<Rect?>(null)
        internal set

    val focus = FocusRequester()
}

/**
 * Which button opened the timeline switcher: claimed as one is clicked or
 * keyed, taken once as the switcher opens, so a switcher no button opened (a
 * hold of Home on the bar) never drops down under a button clicked before.
 * [scope] is Home's, which gives the focus back to the button.
 */
@Stable
internal class SwitcherAnchor(private val scope: CoroutineScope) {
    private val buttons = HashMap<String, SwitcherButton>()
    private var claimed: SwitcherButton? = null

    /** The button that is Home's part [part], the same one however often it is made afresh. */
    fun button(part: String): SwitcherButton = buttons.getOrPut(part) { SwitcherButton() }

    /** [button] activated now: the one the switcher opens under. */
    fun claim(button: SwitcherButton) {
        claimed = button
    }

    /** The button that opened the switcher, cleared as it is taken; null for a switcher no button opened. */
    fun take(): SwitcherButton? = claimed.also { claimed = null }

    /** The focus back on [button], once the switcher's popup has gone. */
    fun refocus(button: SwitcherButton) {
        scope.launch {
            withFrameNanos {}
            withFrameNanos {}
            runCatching { button.focus.requestFocus() }
        }
    }
}

/** The [SwitcherAnchor] of Home; null outside it. */
internal val LocalSwitcherAnchor = staticCompositionLocalOf<SwitcherAnchor?> { null }

/**
 * A button that opens the timeline switcher: [modifier] goes on it, and
 * [onClick] (its click or key) claims it before opening, so the switcher
 * opens under it ([SwitcherAnchor]). Outside Home, the opening alone.
 */
internal class SwitcherOpener(val modifier: Modifier, val onClick: () -> Unit)

/** The [SwitcherOpener] of Home's part [part] (one name per kind of button: "following", "timeline", a network's id), opening with [open]. */
@Composable
internal fun switcherButton(part: String, open: () -> Unit): SwitcherOpener {
    val anchor = LocalSwitcherAnchor.current
    val opening by rememberUpdatedState(open)
    return remember(anchor, part) {
        if (anchor == null) {
            SwitcherOpener(Modifier) { opening() }
        } else {
            val button = anchor.button(part)
            SwitcherOpener(Modifier.onGloballyPositioned { button.bounds = it.boundsInWindow() }.focusRequester(button.focus)) {
                anchor.claim(button)
                opening()
            }
        }
    }
}

/** Where the desktop's switcher stands and how its keys move about it, apart from the popup. */
internal object SwitcherPopoverPlace {
    /** The popover's width, in dp. */
    const val WIDTH = 360

    /** Its distance from the button and from the window's edges, in dp. */
    const val GAP = 4
    const val MARGIN = 8

    /**
     * The left edge, in px from the window's left: on the button's start edge
     * (its right one right to left), or on its end edge for a button at the
     * end of a bar ([atEnd]), the panel opening back across the page; kept
     * [margin] inside the window.
     */
    fun x(anchorLeft: Float, anchorRight: Float, width: Float, windowWidth: Float, margin: Float, rtl: Boolean, atEnd: Boolean = false): Float {
        val wanted = if (rtl != atEnd) anchorRight - width else anchorLeft
        return wanted.coerceIn(margin, (windowWidth - width - margin).coerceAtLeast(margin))
    }

    /** The top, just under the button, at least [margin] from the window's top. */
    fun y(anchorBottom: Float, gap: Float, margin: Float): Float = (anchorBottom + gap).coerceAtLeast(margin)

    /** The most it may be tall from [top] down, [margin] above the window's foot, never less than [least]. */
    fun maxHeight(top: Float, windowHeight: Float, margin: Float, least: Float): Float = (windowHeight - top - margin).coerceAtLeast(least)

    /** Where a key takes the focus, as a desktop menu's do ([com.aimdi.xtanative.ui.post.MenuKeys]); null for any other key, or one modified. */
    fun move(key: Key, modified: Boolean): FocusDirection? = when {
        modified -> null
        key == Key.DirectionDown -> FocusDirection.Next
        key == Key.DirectionUp -> FocusDirection.Previous
        key == Key.DirectionLeft -> FocusDirection.Left
        key == Key.DirectionRight -> FocusDirection.Right
        else -> null
    }

    /** Whether [key] goes to the first stop (true), the last (false), or neither (null): Home and Page Up, End and Page Down. */
    fun end(key: Key, modified: Boolean): Boolean? = when {
        modified -> null
        key == Key.MoveHome || key == Key.PageUp -> true
        key == Key.MoveEnd || key == Key.PageDown -> false
        else -> null
    }
}

/**
 * The timeline switcher as a desktop's drop-down: under the button that
 * opened it ([button]), following it as the window's layout moves it (the
 * column turning to the rail), [SwitcherPopoverPlace.WIDTH] wide, scrolled where the
 * window is short, its edge the theme's outline. It grows in from the
 * button's corner and fades out (still while motion is reduced). ↑ ↓ move
 * along its stops, ← → across a row, Home and End to either end; Esc or a
 * click beside it closes it and gives the button its focus back. A pick in it
 * closes it through [LocalSheetCloser], as the sheet's do: the work is done
 * once, after it has gone, however often it is clicked meanwhile.
 */
@Composable
internal fun SwitcherPopover(anchor: SwitcherAnchor, button: SwitcherButton, onDismiss: () -> Unit, atEnd: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val closer = remember(scope) {
        SheetCloser(scope, slide = {
            visible.targetState = false
            snapshotFlow { visible.isIdle }.first { it }
        }) { dismiss() }
    }
    closer.reduced = reduced
    val away = { closer.close { anchor.refocus(button) } }
    Popup(popupPositionProvider = WholeWindow, onDismissRequest = away, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // The pointer's way out beside the panel (Esc is the keys'), never a stop of Tab's.
                .focusProperties { canFocus = false }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = away),
        ) {
            val density = LocalDensity.current
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val at = button.bounds ?: Rect.Zero
            val (x, y, tall) = with(density) {
                val width = SwitcherPopoverPlace.WIDTH.dp.toPx()
                val margin = SwitcherPopoverPlace.MARGIN.dp.toPx()
                val top = SwitcherPopoverPlace.y(at.bottom, SwitcherPopoverPlace.GAP.dp.toPx(), margin)
                Triple(
                    SwitcherPopoverPlace.x(at.left, at.right, width, constraints.maxWidth.toFloat(), margin, rtl, atEnd),
                    top,
                    SwitcherPopoverPlace.maxHeight(top, constraints.maxHeight.toFloat(), margin, 160.dp.toPx()).toDp(),
                )
            }
            val corner = TransformOrigin(if (rtl != atEnd) 1f else 0f, 0f)
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.offset { IntOffset(if (rtl) (constraints.maxWidth - x - SwitcherPopoverPlace.WIDTH.dp.toPx()).toInt() else x.toInt(), y.toInt()) },
                enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(Motion.STANDARD_MS)) + scaleIn(tween(Motion.STANDARD_MS), initialScale = 0.95f, transformOrigin = corner),
                exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(Motion.FAST_MS)) + scaleOut(tween(Motion.FAST_MS), targetScale = 0.97f, transformOrigin = corner),
            ) {
                PopoverPanel(closer, tall, content)
            }
        }
    }
}

/**
 * The panel itself: the theme's container with its outline, its stops moved
 * by the keys. Opened with the keyboard its first stop has the focus at once;
 * opened with the mouse an unseen stop holds it, so the keys reach the panel,
 * until a key moves it on.
 */
@Composable
private fun PopoverPanel(closer: SheetCloser, tall: Dp, content: @Composable ColumnScope.() -> Unit) {
    val manager = LocalFocusManager.current
    val modes = LocalInputModeManager.current
    // Opened with the keyboard, its first stop takes the focus; opened with the mouse, an unseen stop holds it
    // until a key moves it on, that key's move standing (the rows light the keyboard's focus as it comes).
    val keyboardNow = keyboardFocusShown()
    val byKeys = remember { keyboardNow }
    var holding by remember { mutableStateOf(!byKeys) }
    val holder = remember { FocusRequester() }
    val held = remember { booleanArrayOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        if (byKeys) {
            manager.moveFocus(FocusDirection.Next)
            return@LaunchedEffect
        }
        // The popup's window may take the keys a few frames late, dropping a focus asked for before: asked again until held.
        repeat(HOLD_TRIES) {
            if (held[0] || !holding) return@LaunchedEffect
            runCatching { holder.requestFocus() }
            withFrameNanos {}
        }
    }
    // A key that found no focus in the panel (the unseen stop never took it) still reaches its first stop.
    LaunchedEffect(keyboardNow) {
        if (keyboardNow && !byKeys && holding && !held[0]) manager.moveFocus(FocusDirection.Next)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(SwitcherPopoverPlace.WIDTH.dp)
            .heightIn(max = tall)
            // A click on the panel is no click beside it.
            .focusProperties { canFocus = false }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onPreviewKeyEvent { event ->
                // A stop the arrows reach is the keyboard's, lit as Tab's is (as the post menu's are).
                (event.type == KeyEventType.KeyDown && keyed(event.key, event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed, manager))
                    .also { moved -> if (moved) modes.requestInputMode(InputMode.Keyboard) }
            },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .focusProperties { canFocus = holding }
                    .focusRequester(holder)
                    .onFocusChanged { if (held[0] && !it.isFocused) holding = false else held[0] = it.isFocused }
                    .focusable(),
            )
            CompositionLocalProvider(LocalSheetCloser provides closer, LocalMenuRowMetrics provides MenuRowMetrics.Pointer) { content() }
        }
    }
}

/** How many frames the unseen stop is asked to take the focus before the panel gives up on it. */
private const val HOLD_TRIES = 10

/** [key] moving the focus with [focus]: true when it did. */
private fun keyed(key: Key, modified: Boolean, focus: FocusManager): Boolean {
    SwitcherPopoverPlace.move(key, modified)?.let { direction ->
        // ← → go across a row; at its end they go on along the stops as ↓ ↑ do.
        return focus.moveFocus(direction) || when (direction) {
            FocusDirection.Right -> focus.moveFocus(FocusDirection.Next)
            FocusDirection.Left -> focus.moveFocus(FocusDirection.Previous)
            else -> false
        }
    }
    val first = SwitcherPopoverPlace.end(key, modified) ?: return false
    focus.clearFocus()
    return focus.moveFocus(if (first) FocusDirection.Next else FocusDirection.Previous)
}

/** A popup laid over the whole window, from its top left corner. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}
