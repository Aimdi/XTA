package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
internal actual fun PostMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val atPointer = rememberCursorPositionProvider(offset = DpOffset(2.dp, 2.dp), windowMargin = 8.dp)
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val atPost = remember(margin) { AtPostEnd(margin) }
    Popup(
        popupPositionProvider = if (LocalMenuAtPost.current) atPost else atPointer,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
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
                content = content,
            )
        }
    }
}
