package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The placeholder posts an opened post shows while its thread loads, and when it says there are no replies. */
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

    private val post = XPost("1", XUser("2", "maya", "Maya"), null, "text")
    private val answered = PostUi(focal = post, loading = false, answered = true)

    @Test
    fun aLoadedPostWithoutRepliesSaysSo() {
        assertTrue(PostLoading.noReplies(answered))
    }

    @Test
    fun noRepliesWaitsForXAndStaysOffWhileThereIsMoreToShow() {
        assertFalse(PostLoading.noReplies(answered.copy(loading = true)), "still loading")
        assertFalse(PostLoading.noReplies(answered.copy(answered = false)), "drawn from the feed, X not asked yet")
        assertFalse(PostLoading.noReplies(answered.copy(replies = listOf(XThread("3", listOf(post))))), "has replies")
        assertFalse(PostLoading.noReplies(answered.copy(focal = null)), "the post is gone")
        assertFalse(PostLoading.noReplies(answered.copy(showMore = "cursor")), "X withheld some")
        assertFalse(PostLoading.noReplies(answered.copy(repliesMissing = true)), "X counted some it never sent")
        assertFalse(PostLoading.noReplies(answered.copy(loadingMore = true)), "a page is loading")
    }
}
