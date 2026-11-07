package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** home_timeline_nested_scroll_test.dart's scrollToTop. */
class MotionTest {
    @Test
    fun withMotionDisabledAListJumpsToItsTopAtOnce(): Unit = runBlocking {
        val list = LazyListState(firstVisibleItemIndex = 12, firstVisibleItemScrollOffset = 40)
        list.scrollToTop(reduced = true)
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
    }
}
