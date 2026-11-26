package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Where a list's sticky header (a profile's or a community's tabs) covers
 * the rows under it: from the list's top to the header's end while it
 * sticks there, none while it scrolls with the rows. Plain numbers, so the
 * rule is tested apart from the list.
 */
object StickyHeaderCover {
    /** The pixels at the list's top the header covers, its [top] and [size] as laid out; 0 where it is not shown or not stuck. */
    fun covered(top: Int?, size: Int): Int = when {
        top == null || top > 0 -> 0
        else -> maxOf(0, top + size)
    }

    /**
     * How far [spec] scrolls a list of [container] pixels to show what lies
     * [offset] pixels down it, [size] long, with the top [cover] pixels
     * hidden: as if the list began below the header, so a control the keys
     * reach is never left under it.
     */
    @OptIn(ExperimentalFoundationApi::class)
    fun distance(spec: BringIntoViewSpec, offset: Float, size: Float, container: Float, cover: Float): Float =
        if (cover <= 0f || cover >= container) spec.calculateScrollDistance(offset, size, container)
        else spec.calculateScrollDistance(offset - cover, size, container - cover)
}

/**
 * The scrolls of [list] that bring a focused control into view, kept clear
 * of its sticky header (the item keyed [key]): Tab and Shift+Tab stopped on
 * a post's buttons hidden under the stuck tabs, as the list scrolled them
 * only as far as its top. [outer] is the platform's own way, kept for
 * everything else: the header's own controls, and the scrolling rows inside
 * the list's items (their container is not the list's height).
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class StickyHeaderRoom internal constructor(
    private val list: LazyListState,
    private val key: Any,
    private val outer: BringIntoViewSpec,
) : BringIntoViewSpec {
    private var headerFocused = false

    /** On the sticky header: while its own controls have the focus, nothing is moved clear of it. */
    val header: Modifier = Modifier.onFocusChanged { headerFocused = it.hasFocus }

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val info = list.layoutInfo
        val cover = when {
            headerFocused || containerSize != info.viewportSize.height.toFloat() -> 0
            else -> info.visibleItemsInfo.firstOrNull { it.key == key }.let { StickyHeaderCover.covered(it?.let { item -> item.offset - info.viewportStartOffset }, it?.size ?: 0) }
        }
        return StickyHeaderCover.distance(outer, offset, size, containerSize, cover.toFloat())
    }
}

/** A [StickyHeaderRoom] for [list], whose sticky header is the item keyed [key]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberStickyHeaderRoom(list: LazyListState, key: Any): StickyHeaderRoom {
    val outer = LocalBringIntoViewSpec.current
    return remember(list, key, outer) { StickyHeaderRoom(list, key, outer) }
}

/** [content], the list, scrolling to what the keys focus clear of its sticky header ([room]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepsClearOfStickyHeader(room: StickyHeaderRoom, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBringIntoViewSpec provides room, content = content)
