package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import com.aimdi.xtanative.ui.theme.DesktopRipples
import com.aimdi.xtanative.ui.theme.KeyboardFocusLook
import com.aimdi.xtanative.ui.theme.MouseRipples

/** A menu's width on the desktop, as Material's menus keep theirs. */
private val MenuMinWidth = 200.dp
private val MenuMaxWidth = 320.dp

/**
 * A menu the keyboard opened: at the top of the post's end, where its ⋯ is,
 * kept [margin] inside the window.
 */
private class AtPostEnd(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Rtl) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val y = anchorBounds.top
        return IntOffset(
            x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
            y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)),
        )
    }
}

/**
 * The menu's rows go with the keys ([MenuKeys]): ↑ ↓ Home End move among
 * them, Enter and Space press one. Opened with the keyboard (the Menu key,
 * Shift+F10, Enter on a ⋯), its first row has the focus at once; opened with
 * the mouse, none does until a key moves it ([KeyboardFocusLook]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val atPointer = rememberCursorPositionProvider(offset = DpOffset(2.dp, 2.dp), windowMargin = 8.dp)
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val atPost = remember(margin) { AtPostEnd(margin) }
    // The popup's own focus, which its key handler outside the content moves.
    val focus = remember { arrayOfNulls<FocusManager>(1) }
    val modes = remember { arrayOfNulls<InputModeManager>(1) }
    Popup(
        popupPositionProvider = if (LocalMenuAtPost.current) atPost else atPointer,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { event ->
            // As Material's menus do: a focus the keys moved shows, in a menu the mouse opened too.
            focus[0]?.let { MenuKeys.handle(event, it) }?.also { moved -> if (moved) modes[0]?.requestInputMode(InputMode.Keyboard) } ?: false
        },
    ) {
        val manager = LocalFocusManager.current
        focus[0] = manager
        modes[0] = LocalInputModeManager.current
        // The rows' focus is lit by ripples made for the mode the input is in ([KeyboardFocusLook]): a menu the mouse
        // opened is made afresh at the first key, its first row then taking the focus as one the keyboard opened.
        // Never the other way: a click on a row would be lost with the row it pressed.
        val keyed = remember { booleanArrayOf(false) }
        if (KeyboardFocusLook.keyboard) keyed[0] = true
        val byKeys = keyed[0]
        key(byKeys) {
            // Opened with the mouse, an unseen stop at the menu's end holds the focus, so the keys reach the menu at all;
            // ↓ goes on from it to the first row and ↑ to the last, and once a row has had the focus it is no stop any more.
            var holding by remember { mutableStateOf(true) }
            val holder = remember { FocusRequester() }
            val held = remember { booleanArrayOf(false) }
            LaunchedEffect(manager, byKeys) {
                withFrameNanos {}
                if (byKeys) {
                    holding = false
                    modes[0]?.requestInputMode(InputMode.Keyboard)
                    manager.moveFocus(FocusDirection.Next)
                } else {
                    runCatching { holder.requestFocus() }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                // A hairline keeps its edge on the black theme, where the shadow does not show.
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    CompositionLocalProvider(LocalRippleConfiguration provides if (byKeys) DesktopRipples else MouseRipples) { content() }
                    Box(
                        Modifier
                            .size(0.dp)
                            .focusProperties { canFocus = holding }
                            .focusRequester(holder)
                            .onFocusChanged { if (held[0] && !it.isFocused) holding = false else held[0] = it.isFocused }
                            .focusable(),
                    )
                }
            }
        }
    }
}
