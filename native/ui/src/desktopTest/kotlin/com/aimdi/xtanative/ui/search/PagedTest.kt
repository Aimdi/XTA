package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.data.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagedTest {
    private fun Paged<String>.add(page: List<String>, next: String?, asked: String?, end: PageEnd) = appended(page, next, asked, end) { it }

    @Test
    fun postsFollowTheCursorAndLeaveOutWhatIsShown() {
        val first = Paged<String>().add(listOf("a", "b"), "c1", null, PageEnd.POSTS)
        assertEquals(listOf("a", "b"), first.items)
        assertEquals("c1", first.next)
        assertTrue(first.loaded)

        // Search cursors overlap: an already shown post is not shown twice, and the page still leads on.
        val second = first.add(listOf("b", "c", "c"), "c2", "c1", PageEnd.POSTS)
        assertEquals(listOf("a", "b", "c"), second.items)
        assertEquals("c2", second.next)
    }

    @Test
    fun anAllDuplicatePageOfPostsStillLeadsOn() {
        val shown = Paged<String>().add(listOf("a"), "c1", null, PageEnd.POSTS)
        assertEquals("c2", shown.add(listOf("a"), "c2", "c1", PageEnd.POSTS).next)
    }

    @Test
    fun anEmptyPageEndsPostsButNotMedia() {
        val shown = Paged<String>().add(listOf("a"), "c1", null, PageEnd.POSTS)
        assertNull(shown.add(emptyList(), "c2", "c1", PageEnd.POSTS).next)
        assertEquals("c2", shown.add(emptyList(), "c2", "c1", PageEnd.MEDIA).next)
    }

    @Test
    fun aPageWithNoOneNewEndsPeopleAndCommunities() {
        val shown = Paged<String>().add(listOf("a", "b"), "c1", null, PageEnd.FRESH)
        val again = shown.add(listOf("b", "a"), "c2", "c1", PageEnd.FRESH)
        assertNull(again.next)
        assertEquals(listOf("a", "b"), again.items)
    }

    @Test
    fun aMissingEmptyOrRepeatedCursorEndsEveryList() {
        PageEnd.entries.forEach { end ->
            assertNull(Paged<String>().add(listOf("a"), null, null, end).next)
            assertNull(Paged<String>().add(listOf("a"), "", null, end).next)
            assertNull(Paged<String>().add(listOf("a"), "same", "same", end).next)
        }
    }

    @Test
    fun emptyOnlyOnceXAnsweredWithNothingMore() {
        assertFalse(Paged<String>().empty)
        assertFalse(Paged.reading<String>().empty)
        assertTrue(Paged<String>().add(emptyList(), null, null, PageEnd.POSTS).empty)
        assertFalse(Paged<String>().add(emptyList(), "c1", null, PageEnd.MEDIA).empty)
    }

    @Test
    fun morePagesAreAskedForOneAtATime() {
        val shown = Paged<String>().add(listOf("a"), "c1", null, PageEnd.POSTS)
        assertTrue(shown.canLoadMore)
        assertFalse(shown.askingMore().canLoadMore)
        assertFalse(Paged.reading<String>().canLoadMore)
        assertFalse(shown.copy(next = null).canLoadMore)

        val failed = shown.askingMore().moreFailed(Problem.Offline)
        assertEquals(listOf("a"), failed.items)
        assertEquals(Problem.Offline, failed.moreProblem)
        assertTrue(failed.canLoadMore)
        assertNull(failed.askingMore().moreProblem)
    }

    @Test
    fun aFailedFirstPageSaysWhy() {
        val failed = Paged.reading<String>().failed(Problem.RateLimited)
        assertFalse(failed.loading)
        assertEquals(Problem.RateLimited, failed.problem)
        assertFalse(failed.canLoadMore)
    }
}
