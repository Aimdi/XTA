package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable

/** Where an [IconTip] shows its label: under the pointer, or beside the icon at its end edge. */
internal enum class TipSide {
    /** Just under the pointer, as most of the app's tips show. */
    POINTER,

    /**
     * Beside the icon at its end edge, centred on it: for a column of icons
     * (the desktop's rail), where a tip under the pointer would cover the
     * icon below.
     */
    END,
}

/**
 * [content] with [label] shown as [IconTip] shows it, at [side]. Kept apart
 * from [IconTip] itself, whose one form plugins call.
 */
@Composable
internal expect fun IconTip(label: String, side: TipSide, content: @Composable () -> Unit)
