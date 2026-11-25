package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals

/** The placeholder posts an opened post shows while its thread loads. */
class PostLoadingTest {
    @Test
    fun aPostNotAtHandShowsItselfAndTwoReplies() {
        assertEquals(3, PostLoading.placeholders(null))
    }

    @Test
    fun aPostAtHandShowsOnePlaceholderPerCountedReplyUpToThree() {
        assertEquals(0, PostLoading.placeholders(0))
        assertEquals(2, PostLoading.placeholders(2))
        assertEquals(3, PostLoading.placeholders(40_000))
    }
}
