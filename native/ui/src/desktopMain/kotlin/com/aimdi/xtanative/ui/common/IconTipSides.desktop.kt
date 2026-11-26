package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun IconTip(label: String, side: TipSide, content: @Composable () -> Unit) {
    if (side == TipSide.POINTER || label.isBlank()) return IconTip(label, content)
    TooltipArea(
        tooltip = { TipBubble(label) },
        delayMillis = TIP_DELAY_MS,
        tooltipPlacement = EndPlacement,
        modifier = Modifier.keyboardFocusRing(),
    ) { content() }
}

/** A tip beside the icon: its start a little past the icon's end edge, its middle on the icon's. */
@OptIn(ExperimentalFoundationApi::class)
private val EndPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.CenterEnd, alignment = Alignment.CenterEnd, offset = DpOffset(8.dp, 0.dp))
