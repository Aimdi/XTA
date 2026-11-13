package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BooruHistoryTest {
    private val a = "https://a.example"
    private val b = "https://b.example"

    @Test
    fun aSearchGoesFirstInPlaceOfTheSameTagsInAnyOrder() {
        val history = listOf("smile solo", "comic")
        assertEquals(listOf("solo smile", "comic"), BooruHistory.remembered(history, "  solo smile "))
        assertEquals(history, BooruHistory.remembered(history, "   "))
    }

    @Test
    fun theHistoryKeepsTheLatestTwenty() {
        val next = BooruHistory.remembered((1..BooruHistory.CAP).map { "tag$it" }, "new")
        assertEquals(BooruHistory.CAP, next.size)
        assertEquals("new", next.first())
        assertFalse("tag${BooruHistory.CAP}" in next)
    }

    @Test
    fun aForgottenSearchIsGone() {
        assertEquals(listOf("comic"), BooruHistory.forgotten(listOf("solo", "comic"), "solo"))
    }

    @Test
    fun followingNotesTheNewestPostOfEachSite() {
        val seen = BooruVisits.seen("""{"$a": 10, "$b": "x"}""")
        assertEquals(mapOf(a to 10L), seen)
        val posts = listOf(post(id = "12", host = a), post(id = "8", host = a), post(id = "3", host = b), post(id = "md5", host = "https://c.example"))
        val marked = BooruVisits.marked(seen, posts)
        assertEquals(mapOf(a to 12L, b to 3L), marked)
        assertEquals(marked, BooruVisits.seen(BooruVisits.encode(marked)))
    }

    @Test
    fun onlyPostsAfterTheLastVisitAreNew() {
        val since = mapOf(a to 10L)
        assertTrue(BooruVisits.isNew(since, post(id = "11", host = a)))
        assertFalse(BooruVisits.isNew(since, post(id = "10", host = a)))
        assertFalse(BooruVisits.isNew(since, post(id = "99", host = b)))
    }

    @Test
    fun nothingIsNewOnASiteNotVisitedBeforeNorAPostWithoutANumber() {
        assertFalse(BooruVisits.isNew(emptyMap(), post(id = "9", host = a)))
        assertFalse(BooruVisits.isNew(mapOf(a to 10L), post(id = "abc", host = a)))
    }

    @Test
    fun seeingNothingChangesNothingAndABrokenRecordIsNoVisit() {
        val seen = mapOf(a to 20L)
        assertEquals(seen, BooruVisits.marked(seen, emptyList()))
        assertEquals(emptyMap(), BooruVisits.seen("nope"))
        assertEquals(mapOf("c" to 4L), BooruVisits.seen("""{"a":"b","c":4}"""))
        assertEquals(emptyMap(), BooruVisits.seen(""))
    }
}
