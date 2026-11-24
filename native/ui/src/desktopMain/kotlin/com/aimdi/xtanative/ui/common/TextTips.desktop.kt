package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val TextTipsShown: Boolean = true

/**
 * The tip goes round the text only while there is one, so the many uncut
 * texts of a feed carry no extra layer. The area only watches the pointer
 * pass, so clicks, drags, right clicks and hovers still reach the text and
 * what holds it. The text fills the area: its own alignment keeps working.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun TextTip(tip: String?, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    if (tip == null) return content(modifier)
    TooltipArea(
        tooltip = { TipBubble(tip) },
        delayMillis = TIP_DELAY_MS,
        tooltipPlacement = TipPlacement,
        modifier = modifier,
    ) { content(Modifier.fillMaxWidth()) }
}
