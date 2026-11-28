package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.home.LocalWideFrame
import com.aimdi.xtanative.ui.plugins.PluginPlatform
import androidx.compose.material3.Scaffold as MaterialScaffold

/**
 * Material's [MaterialScaffold], with its [topBar] first in Tab's order on
 * the desktop. Compose walks Tab in the order things are placed, and
 * Material places a scaffold's page before its bar (so the bar draws over
 * it): Tab went from the bar out of the page (beside the window's sidebar,
 * into the sidebar's rows) and reached the page only after them. Here the
 * bar is placed first and still drawn over the page, which lies under it as
 * in Material's, padded by the bar's height: Tab goes bar, then page, top to
 * bottom as the eye reads, and the sidebar after the page. Off the desktop,
 * Material's scaffold as it is.
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!PluginPlatform.isDesktop) {
        MaterialScaffold(modifier, topBar, bottomBar, snackbarHost, floatingActionButton, floatingActionButtonPosition, containerColor, contentColor, contentWindowInsets, content)
        return
    }
    val bar = remember { BarHeight() }
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.pageBar()) { topBar() }
            MaterialScaffold(
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
                floatingActionButton = floatingActionButton,
                floatingActionButtonPosition = floatingActionButtonPosition,
                containerColor = containerColor,
                contentColor = contentColor,
                contentWindowInsets = contentWindowInsets,
            ) { inner -> ClaimsPageBar(bar.height > 0.dp) { content(remember(inner) { UnderBar(inner, bar) }) } }
        },
    ) { (top, page), constraints ->
        val placedBar = top.measure(constraints.copy(minWidth = 0, minHeight = 0))
        bar.height = placedBar.height.toDp()
        val placedPage = page.measure(constraints)
        layout(placedPage.width, placedPage.height) {
            // Placed first for Tab's order, drawn last over the page.
            placedBar.place(0, 0, zIndex = 1f)
            placedPage.place(0, 0)
        }
    }
}

/** Whether a page's bar around holds the frame's [com.aimdi.xtanative.ui.home.WideFrame.pageBar]: a scaffold within that page's then does not. */
private val LocalPageBarClaimed = staticCompositionLocalOf { false }

/**
 * Where Tab from the desktop frame's navigation comes into the page
 * ([com.aimdi.xtanative.ui.home.WideFrame.pageBar]): the outermost bar of
 * the page that shows, never a bar nested under it (a scaffold without a bar,
 * as Home's around its timelines, leaves it to the one within).
 */
@Composable
internal fun Modifier.pageBar(): Modifier {
    val frame = LocalWideFrame.current
    return if (frame != null && !LocalPageBarClaimed.current) focusRequester(frame.pageBar) else this
}

/** [content], whose scaffolds leave the frame's page bar to the bar around them when [claims] ([pageBar]). */
@Composable
internal fun ClaimsPageBar(claims: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPageBarClaimed provides (LocalPageBarClaimed.current || claims), content = content)
}

/** The height of a [Scaffold]'s bar, as last measured. */
@Stable
internal class BarHeight {
    var height by mutableStateOf(0.dp)
}

/**
 * The padding Material's scaffold gives its page ([inner]), the bar's height
 * at the top where there is a bar (as Material's own, which then leaves the
 * window's top inset to the bar).
 */
internal class UnderBar(private val inner: PaddingValues, private val bar: BarHeight) : PaddingValues {
    override fun calculateTopPadding(): Dp = bar.height.takeIf { it > 0.dp } ?: inner.calculateTopPadding()

    override fun calculateBottomPadding(): Dp = inner.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = inner.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = inner.calculateRightPadding(layoutDirection)
}
