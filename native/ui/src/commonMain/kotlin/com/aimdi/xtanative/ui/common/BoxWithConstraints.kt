package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints as FoundationBoxWithConstraints

/**
 * The density the app's theme draws at (the screen's, times the desktop's
 * interface size), as a composition local whose readers are recomposed when
 * it changes; null outside the theme.
 */
val LocalLayoutDensity = compositionLocalOf<Density?> { null }

/**
 * Foundation's [FoundationBoxWithConstraints], with its content composed
 * again when the interface size changes. Foundation's composes it again only
 * for other constraints: a window kept at its size while the reader picks
 * another interface size (Ctrl+Alt+0, Accessibility › Interface size)
 * keeps its constraints in pixels, and so every layout chosen by a width in
 * dp (the window's sidebar or rail, Home's columns, Settings' two panes)
 * stayed as it was for the old size until the window was resized.
 */
@Composable
fun BoxWithConstraints(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    FoundationBoxWithConstraints(modifier, contentAlignment, propagateMinConstraints) {
        val density = LocalLayoutDensity.current ?: LocalDensity.current
        val scope = remember(this, constraints, density) { ScaledConstraintsScope(this, density) }
        scope.content()
    }
}

/** [base], its sizes in dp read at [density]: a new one for a new density, so its content is composed again. */
private class ScaledConstraintsScope(private val base: BoxWithConstraintsScope, private val density: Density) : BoxWithConstraintsScope by base {
    override val minWidth: Dp get() = size(constraints.minWidth)
    override val maxWidth: Dp get() = if (constraints.hasBoundedWidth) size(constraints.maxWidth) else Dp.Infinity
    override val minHeight: Dp get() = size(constraints.minHeight)
    override val maxHeight: Dp get() = if (constraints.hasBoundedHeight) size(constraints.maxHeight) else Dp.Infinity

    private fun size(px: Int): Dp = with(density) { px.toDp() }

    override val constraints: Constraints get() = base.constraints
}
