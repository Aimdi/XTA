package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How far a [CollapsingHeader]'s header is scrolled away, and how tall it is. */
private class Collapse : NestedScrollConnection {
    var height = 0f
    var offset by mutableFloatStateOf(0f)

    private fun moved(delta: Float): Float {
        val next = (offset + delta).coerceIn(-height, 0f)
        val used = next - offset
        offset = next
        return used
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0) Offset(0f, moved(available.y)) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0) Offset(0f, moved(available.y)) else Offset.Zero
}

/**
 * A [header] that scrolls away before the [body] below it does, and comes
 * back once the body is at its top again, with [pinned] (a tab row) staying
 * between them, as a profile's header and tabs do.
 */
@Composable
internal fun CollapsingHeader(header: @Composable () -> Unit, pinned: @Composable () -> Unit, body: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val collapse = remember { Collapse() }
    Layout(
        content = {
            Box { header() }
            Box { pinned() }
            Box { body() }
        },
        modifier = modifier.nestedScroll(collapse).clipToBounds(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val top = measurables[0].measure(loose)
        collapse.height = top.height.toFloat()
        val offset = collapse.offset.coerceAtLeast(-collapse.height).roundToInt()
        val bar = measurables[1].measure(loose)
        val shownHeader = top.height + offset
        val bodyHeight = (constraints.maxHeight - shownHeader - bar.height).coerceAtLeast(0)
        val rest = measurables[2].measure(constraints.copy(minHeight = bodyHeight, maxHeight = bodyHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            top.place(0, offset)
            bar.place(0, shownHeader)
            rest.place(0, shownHeader + bar.height)
        }
    }
}

/** A switch between a few lists of one tab: illustrations or manga, works or bookmarks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PixivSwitch(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        values.forEachIndexed { index, value ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, values.size),
            ) { Text(label(value), maxLines = 1) }
        }
    }
}
