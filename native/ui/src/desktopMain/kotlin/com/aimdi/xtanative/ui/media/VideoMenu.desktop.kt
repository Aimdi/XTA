package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.aimdi.xtanative.ui.post.LocalMenuPosition
import com.aimdi.xtanative.ui.post.PostMenuSurface
import com.aimdi.xtanative.ui.post.ReplyOrderMotion
import com.aimdi.xtanative.ui.theme.LocalXLook
import com.aimdi.xtanative.ui.theme.XLook
import com.aimdi.xtanative.ui.theme.XLookTokens
import com.aimdi.xtanative.ui.theme.colorSchemeOf

/**
 * The app's pointer menu ([PostMenuSurface]: its keys, its rows) standing at
 * the ⋮ button ([VideoMenuPlace]), in Lights out's colours with the reader's
 * accent whatever the theme, so it reads as part of the dark player rather
 * than a white card over it. A row closes it before its work.
 */
@Composable
internal actual fun VideoMenuSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit) {
    val look = LocalXLook.current
    val dark = remember(look) { XLook.scheme(XLookTokens.LIGHTS_OUT.copy(accent = look.tokens.accent)) }
    val density = LocalDensity.current
    val place = remember(density) { VideoMenuPlace(with(density) { 8.dp.roundToPx() }, with(density) { 4.dp.roundToPx() }) }
    MaterialTheme(colorScheme = colorSchemeOf(dark), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalXLook provides dark, LocalMenuPosition provides place) {
            PostMenuSurface(onDismiss) {
                content { then ->
                    onDismiss()
                    then()
                }
            }
        }
    }
}

/**
 * Where the video's menu stands: above its button, its end at the button's
 * end ([ReplyOrderMotion.menuX]), [gap] pixels clear of it; below it when
 * there is no room above; always [margin] inside the window.
 */
internal class VideoMenuPlace(private val margin: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = ReplyOrderMotion.menuX(
            anchorBounds.left.toFloat(), anchorBounds.right.toFloat(), popupContentSize.width.toFloat(),
            windowSize.width.toFloat(), margin.toFloat(), rtl = layoutDirection == LayoutDirection.Rtl,
        )
        return IntOffset(x.toInt(), y(anchorBounds, windowSize.height, popupContentSize.height))
    }

    private fun y(anchor: IntRect, windowHeight: Int, height: Int): Int {
        val above = anchor.top - gap - height
        val wanted = if (above >= margin) above else anchor.bottom + gap
        return wanted.coerceIn(margin, (windowHeight - height - margin).coerceAtLeast(margin))
    }
}
