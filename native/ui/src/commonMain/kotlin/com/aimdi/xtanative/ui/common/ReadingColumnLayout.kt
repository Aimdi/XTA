package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [content] given [padding] with the room on either side that keeps a list
 * of posts in a centred column of [ReadingColumn.MAX_WIDTH] dp in a wide
 * window (reading_column.dart's ReadingColumn). The list itself still fills
 * the width, so the wheel and the scrollbar work across all of it. Given
 * the [list], the desktop's scrollbar stands at the window's end edge.
 */
@Composable
fun WithReadingColumn(padding: PaddingValues, list: LazyListState? = null, content: @Composable (PaddingValues) -> Unit) {
    val direction = LocalLayoutDirection.current
    BoxWithConstraints {
        val inset = ReadingColumn.inset(maxWidth.value).dp
        content(
            PaddingValues(
                start = padding.calculateStartPadding(direction) + inset,
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction) + inset,
                bottom = padding.calculateBottomPadding(),
            ),
        )
        if (list != null) {
            ListScrollbar(
                list,
                Modifier.align(Alignment.TopEnd).padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            )
        }
    }
}

/**
 * [content] no wider than [ReadingColumn.MAX_WIDTH] plus [extra], centred in a
 * wide window: for a page that is a list of rows with its own bars, such as
 * Discover, whose search and rows would otherwise run across the screen.
 */
@Composable
fun CenteredPage(extra: Dp = 120.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ReadingColumn.MAX_WIDTH.dp + extra).fillMaxSize()) { content() }
    }
}
