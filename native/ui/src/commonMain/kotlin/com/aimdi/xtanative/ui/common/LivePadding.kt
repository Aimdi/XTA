package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A padding whose sizes can change without what it is handed to being
 * composed again: it is the same object throughout, and its sizes are read
 * where padding is applied, when a screen is measured. For the room a bar
 * takes while it slides away under a scroll, which changes at every frame:
 * the page under it is measured again, not composed again.
 */
@Stable
class LivePadding : PaddingValues {
    var top by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateTopPadding(): Dp = top
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateBottomPadding(): Dp = bottom
}

/** The top of [padding] alone, and its bottom alone, each as a [LivePadding] that follows it. */
@Composable
fun rememberLiveEdges(padding: PaddingValues): Pair<PaddingValues, PaddingValues> {
    val top = remember { LivePadding() }
    val bottom = remember { LivePadding() }
    top.top = padding.calculateTopPadding()
    bottom.bottom = padding.calculateBottomPadding()
    return top to bottom
}
