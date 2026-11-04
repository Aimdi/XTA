package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * [content] given [padding] with the room on either side that keeps a list
 * of posts in a centred column of [ReadingColumn.MAX_WIDTH] dp in a wide
 * window (reading_column.dart's ReadingColumn). The list itself still fills
 * the width, so the wheel and the scrollbar work across all of it.
 */
@Composable
fun WithReadingColumn(padding: PaddingValues, content: @Composable (PaddingValues) -> Unit) {
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
    }
}
