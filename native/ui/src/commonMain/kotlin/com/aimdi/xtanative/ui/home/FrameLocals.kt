package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

// The parts of the wide frame the media viewers read, plugins' among them. The column itself reads Home's
// model and the app graph, so it lives apart, in WideFrame.kt, out of the plugins' reach (tool/plugin_api_reach.py).

/**
 * The navigation column of a wide desktop window ([HomeWide]) standing
 * beside every screen, not Home's alone: it stays put while the screens
 * change beside it. Home, while it is shown in such a window, draws no column
 * of its own and tells this one its bar's page ([homePage]) and what a tap
 * on one of its pages does there ([homeTap]).
 */
@Stable
class WideFrame {
    /** Home's bar page shown, by id; null until Home has said. */
    var homePage by mutableStateOf<String?>(null)

    /** Home's own tap on the page at an index of its bar (its page again goes back to the top); null while Home is not composed. */
    var homeTap by mutableStateOf<((Int) -> Unit)?>(null)
}

/** The wide window's navigation column around the screens; null where there is none (Android, a narrow window, a post's own window). */
val LocalWideFrame = staticCompositionLocalOf<WideFrame?> { null }

/**
 * [content] over the whole window, the frame's column included: the media
 * viewer, which is shown in the screens' place beside the column but grows
 * over it at the start edge (drawn after the column, so above it). Where
 * there is no frame, [content] as it is.
 */
@Composable
fun FullWindow(content: @Composable () -> Unit) {
    if (LocalWideFrame.current == null) return content()
    Box(
        Modifier.layout { measurable, constraints ->
            val extra = HomeWide.FRAME_INSET.dp.roundToPx()
            val wider = constraints.copy(
                minWidth = constraints.minWidth + extra,
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
            )
            val placeable = measurable.measure(wider)
            layout(placeable.width - extra, placeable.height) { placeable.placeRelative(-extra, 0) }
        },
    ) { content() }
}
