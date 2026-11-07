package com.aimdi.xtanative.ui.plugins

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.aimdi.xtanative.core.feed.ReadingControls
import com.aimdi.xtanative.core.feed.ReadingScroll
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_keep_controls_visible
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The reading-controls row for the network timeline Home is showing
 * (plugin_home_reading_controls.dart). Null on a plugin's own screen, and on
 * Following and For you: the row stays, and nothing is asked to hide.
 */
val LocalHomeReading = staticCompositionLocalOf<HomeReading?> { null }

/**
 * One timeline's reading controls. Home owns it, binds it to the timeline
 * shown, and puts [connection] on the page so a finger scroll is told apart
 * from a jump. The lists report how far they have scrolled.
 */
class HomeReading {
    val controls = ReadingControls()
    var ui by mutableStateOf(controls.state)
    var active = false
    var protected = false
    var focused = false
    var height = ReadingControls.MIN_HEIGHT
    var persist: suspend (Boolean) -> Boolean = { true }

    private var userDelta = 0f

    /** A finger scroll toward the end is positive, as XTA's userDelta is. A fling is not one. */
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (active && source == NestedScrollSource.UserInput && consumed.y != 0f) userDelta += -consumed.y
            return Offset.Zero
        }
    }

    init {
        controls.onChange = { ui = it }
    }

    fun bind(source: String, pinned: Boolean) {
        controls.bind(source, pinned)
        ui = controls.state
    }

    fun reveal() {
        controls.reveal()
        ui = controls.state
    }

    /** The list laid out [metrics]; null while it has no size yet, and the drag waits. */
    fun report(metrics: Pair<Double, Double>?) {
        if (!active || metrics == null) return
        val delta = userDelta.toDouble().also { userDelta = 0f }
        controls.observe(controls.state.source, metrics.first, metrics.second, height, delta, protected || focused)
        ui = controls.state
    }

    fun reportHeight(px: Int) {
        height = maxOf(ReadingControls.MIN_HEIGHT, px.toDouble())
    }

    suspend fun toggle() {
        controls.setPinned(!controls.state.pinned, persist)
        ui = controls.state
    }
}

@Composable
fun rememberHomeReading(): HomeReading = remember { HomeReading() }

/**
 * The secondary row of a plugin timeline. On Home it collapses once the
 * reader has scrolled down and comes back on the way up; off Home it is the
 * row as the plugin drew it. The row stays composed either way, so a chip
 * that was selected still is.
 */
@Composable
fun ReadingControlsBar(content: @Composable () -> Unit) {
    val reading = LocalHomeReading.current
    if (reading == null) {
        content()
        return
    }
    val shown = reading.ui.visible
    val reduced = LocalReducedMotion.current
    val factor by animateFloatAsState(
        if (shown) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(MOTION_MS, easing = EaseInOut),
        label = "reading-controls",
    )
    Layout(
        content = {
            Box(
                Modifier
                    .onSizeChanged { reading.reportHeight(it.height) }
                    .onFocusEvent {
                        reading.focused = it.hasFocus
                        if (it.hasFocus) reading.reveal()
                    }
                    .then(if (shown) Modifier else Modifier.clearAndSetSemantics {}),
            ) { content() }
        },
        modifier = Modifier.clipToBounds().semantics { if (!shown) hideFromAccessibility() },
    ) { measurables, constraints ->
        val placeable = measurables[0].measure(constraints)
        val height = (placeable.height * factor).roundToInt()
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
}

/**
 * "Keep controls visible", in a plugin's menu while that plugin is a timeline
 * on Home. Nothing off Home, where the row does not hide.
 */
@Composable
fun ReadingControlsPinItem() {
    val reading = LocalHomeReading.current ?: return
    val scope = rememberCoroutineScope()
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.home_keep_controls_visible)) },
        trailingIcon = { Checkbox(checked = reading.ui.pinned, onCheckedChange = null) },
        onClick = {
            reading.reveal()
            scope.launch { reading.toggle() }
        },
    )
}

/** Tells Home how far [state] has scrolled, so the row can hide. Off Home it does nothing. */
@Composable
fun ReportsReadingScroll(state: LazyListState) {
    val reading = LocalHomeReading.current ?: return
    LaunchedEffect(state, reading) {
        snapshotFlow { listMetrics(state) }.collect { reading.report(it) }
    }
}

/** [ReportsReadingScroll] for a grid. */
@Composable
fun ReportsReadingScroll(state: LazyGridState) {
    val reading = LocalHomeReading.current ?: return
    LaunchedEffect(state, reading) {
        snapshotFlow { gridMetrics(state) }.collect { reading.report(it) }
    }
}

/** [ReportsReadingScroll] for a staggered grid. */
@Composable
fun ReportsReadingScroll(state: LazyStaggeredGridState) {
    val reading = LocalHomeReading.current ?: return
    LaunchedEffect(state, reading) {
        snapshotFlow { staggeredMetrics(state) }.collect { reading.report(it) }
    }
}

private fun listMetrics(state: LazyListState): Pair<Double, Double>? {
    val info = state.layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()
    return ReadingScroll.of(
        info.viewportStartOffset,
        info.viewportEndOffset,
        state.firstVisibleItemIndex,
        state.firstVisibleItemScrollOffset,
        info.totalItemsCount,
        last?.index ?: -1,
        last?.let { it.offset + it.size } ?: 0,
    )
}

private fun gridMetrics(state: LazyGridState): Pair<Double, Double>? {
    val info = state.layoutInfo
    val items = info.visibleItemsInfo
    val last = items.maxByOrNull { it.offset.y + it.size.height }
    return ReadingScroll.of(
        info.viewportStartOffset,
        info.viewportEndOffset,
        state.firstVisibleItemIndex,
        state.firstVisibleItemScrollOffset,
        info.totalItemsCount,
        items.maxOfOrNull { it.index } ?: -1,
        last?.let { it.offset.y + it.size.height } ?: 0,
    )
}

private fun staggeredMetrics(state: LazyStaggeredGridState): Pair<Double, Double>? {
    val info = state.layoutInfo
    val items = info.visibleItemsInfo
    val last = items.maxByOrNull { it.offset.y + it.size.height }
    return ReadingScroll.of(
        info.viewportStartOffset,
        info.viewportEndOffset,
        state.firstVisibleItemIndex,
        state.firstVisibleItemScrollOffset,
        info.totalItemsCount,
        items.maxOfOrNull { it.index } ?: -1,
        last?.let { it.offset.y + it.size.height } ?: 0,
    )
}

private const val MOTION_MS = 180
