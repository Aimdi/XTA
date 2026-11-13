package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.plugins.ScrollsToTop

/**
 * How often the reader tapped the place on Home's bar of the page shown again
 * (home_screen.dart's _onBarDestination): each page of the bar is given its
 * own count, and 0 off the bar.
 */
val LocalBarRetap = compositionLocalOf { 0 }

/**
 * The bar's place tapped again on this page: [list] back to the top, or,
 * already there, [atTop] (Discover's search takes the focus). Nothing for a
 * screen opened on its own, off the bar.
 */
@Composable
fun RetapsToTop(list: LazyListState, atTop: () -> Unit = {}) {
    val reduced = LocalReducedMotion.current
    val onTop by rememberUpdatedState(atTop)
    ScrollsToTop(LocalBarRetap.current) {
        if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) onTop() else list.scrollToTop(reduced)
    }
}

/** A list's state that goes back to the top when Home's bar is tapped again on its page ([RetapsToTop]). */
@Composable
fun rememberRetappedList(): LazyListState = rememberLazyListState().also { RetapsToTop(it) }
