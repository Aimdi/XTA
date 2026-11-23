package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** How long the pointer rests before the label shows, as the desktop's own tooltips wait. */
private const val TIP_DELAY_MS = 600

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun IconTip(label: String, content: @Composable () -> Unit) {
    if (label.isBlank()) return content()
    TooltipArea(
        tooltip = {
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, shadowElevation = 2.dp) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        delayMillis = TIP_DELAY_MS,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    ) { content() }
}
