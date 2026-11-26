package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * A page under a top bar, as Material's [Scaffold] with a [topBar] alone
 * lays one out, but with the bar placed first where [barFirst] (the
 * desktop): Compose walks Tab in the order things are placed, and the
 * scaffold places its page before its bar (so the bar draws over it). Beside
 * the window's sidebar Tab then went from the sidebar into the posts and
 * reached the bar only after the last of them; placed first, the walk goes
 * bar, then page, top to bottom as the eye reads. The bar still draws over
 * the page. The page is given no padding here: it is laid out below the bar,
 * which the desktop's window has no system bars beside. Elsewhere the
 * scaffold itself, with the system bars' insets it handles.
 */
@Composable
internal fun BarFirstScaffold(
    barFirst: Boolean,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!barFirst) {
        Scaffold(modifier = modifier, containerColor = containerColor, topBar = topBar, content = content)
        return
    }
    Surface(modifier = modifier, color = containerColor) {
        Layout(content = {
            Box { topBar() }
            Box { content(NoPadding) }
        }) { (bar, page), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val placedBar = bar.measure(constraints.copy(minHeight = 0))
            val below = if (constraints.hasBoundedHeight) (height - placedBar.height).coerceAtLeast(0) else Constraints.Infinity
            val placedPage = page.measure(Constraints(minWidth = width, maxWidth = width, minHeight = if (below == Constraints.Infinity) 0 else below, maxHeight = below))
            layout(width, if (constraints.hasBoundedHeight) height else placedBar.height + placedPage.height) {
                // Placed first for Tab's order, drawn last over the page.
                placedBar.place(0, 0, zIndex = 1f)
                placedPage.place(0, placedBar.height)
            }
        }
    }
}

private val NoPadding = PaddingValues()
