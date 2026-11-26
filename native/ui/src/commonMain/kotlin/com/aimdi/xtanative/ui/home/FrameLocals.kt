package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

// The parts of the wide frame the media viewers read, plugins' among them. The column itself reads Home's
// model and the app graph, so it lives apart, in WideFrame.kt, out of the plugins' reach (tool/plugin_api_reach.py).

/**
 * The navigation column of a wide desktop window ([HomeWide]), or the rail
 * of icons of a narrower one, standing beside every screen, not Home's
 * alone: it stays put while the screens change beside it. Home, while it is
 * shown in such a window, draws no column or bottom bar of its own and tells
 * this one its bar's page ([homePage]) and what a tap on one of its pages
 * does there ([homeTap]).
 */
@Stable
class WideFrame {
    /** Home's bar page shown, by id; null until Home has said. */
    var homePage by mutableStateOf<String?>(null)

    /** Home's own tap on the page at an index of its bar (its page again goes back to the top); null while Home is not composed. */
    var homeTap by mutableStateOf<((Int) -> Unit)?>(null)

    /** How far the screens stand from the window's start edge beside the navigation, in dp ([HomeWide.inset]). */
    var inset by mutableIntStateOf(HomeWide.FRAME_INSET)

    /** Opens the whole account menu over the window where the navigation is only a rail ([FrameKind.RAIL]); null beside the column, which shows it all. */
    var openMenu by mutableStateOf<(() -> Unit)?>(null)
}

/** The wide window's navigation column around the screens; null where there is none (Android, a narrow window, a post's own window). */
val LocalWideFrame = staticCompositionLocalOf<WideFrame?> { null }

/**
 * The screens the frame's navigation opened itself (Search, Settings,
 * Downloads, the deck… picked in its column or rail): their places in the
 * navigation, as the owners their screens are composed under
 * ([LocalViewModelStoreOwner]). Empty where there is no frame.
 */
val LocalFrameRoots = compositionLocalOf<Set<Any>> { emptySet() }

/**
 * Whether the screen composed here is one the frame's column or rail opened
 * ([LocalFrameRoots]): the navigation beside it already leads everywhere, so
 * its bar draws no Back of its own. Esc, Alt+Left and the mouse's back
 * button still go back. False on a phone, in a narrow window, and for a
 * screen opened any other way (Search from a hashtag, Settings by Ctrl+comma).
 */
@Composable
fun isFrameRoot(): Boolean {
    val owner = LocalViewModelStoreOwner.current ?: return false
    return LocalWideFrame.current != null && owner in LocalFrameRoots.current
}

/**
 * [content] over the whole window, the frame's column included: the media
 * viewer, which is shown in the screens' place beside the column but grows
 * over it at the start edge (drawn after the column, so above it). Where
 * there is no frame, [content] as it is. Either way [content] sits in the
 * same place of the composition, so a window resized across the frame's
 * width keeps the viewer as it was (its page, zoom and video).
 */
@Composable
fun FullWindow(content: @Composable () -> Unit) {
    val frame = LocalWideFrame.current
    Box(
        Modifier.layout { measurable, constraints ->
            val extra = fullWindowGrowth(frame).dp.roundToPx()
            val wider = constraints.copy(
                minWidth = constraints.minWidth + extra,
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
            )
            val placeable = measurable.measure(wider)
            layout(placeable.width - extra, placeable.height) { placeable.placeRelative(-extra, 0) }
        },
    ) { content() }
}

/** How far, in dp, a screen over the whole window ([FullWindow]) grows past the screens' start edge: over the frame's navigation, or not at all where there is none. */
internal fun fullWindowGrowth(frame: WideFrame?): Int = frame?.inset ?: 0
