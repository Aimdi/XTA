package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsForYouSource
import com.aimdi.xtanative.networks.threads.ThreadsForYouState
import com.aimdi.xtanative.networks.threads.ThreadsPerson
import com.aimdi.xtanative.networks.threads.ThreadsPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the For you section keeps between launches, when opening it reads Meta, and the rows of its list. */
class ThreadsForYouKeptTest {
    private fun post(id: String) = ThreadsPost(id = id, handle = "h$id", authorName = "H$id", text = "t$id", url = "https://www.threads.com/@h$id/post/C$id")

    private val posts = (1..5).map { post("$it") }
    private val readAt = Instant.parse("2026-11-01T09:30:00Z")
    private val person = ThreadsPerson("h1", "H1", null, fromRepost = false)

    @Test
    fun theFeedItsPlaceAndTheSectionReadBackAsTheyWereKept() {
        val kept = ThreadsForYouKept(posts, ThreadsForYouSource.GUEST, readAt, ThreadsPlace("3", 42), ThreadsSection.FOR_YOU)
        assertEquals(kept, ThreadsForYouKept.decode(kept.encode()))
    }

    @Test
    fun nothingOfASessionIsWritten() {
        val text = ThreadsForYouKept(posts, ThreadsForYouSource.SESSION, readAt).encode()
        listOf("IGT", "Bearer", "sessionid", "csrftoken", "Cookie", "Authorization").forEach { assertFalse(text.contains(it, ignoreCase = true), it) }
    }

    @Test
    fun anUnreadableFileKeepsNothing() {
        assertEquals(ThreadsForYouKept(), ThreadsForYouKept.decode(null))
        assertEquals(ThreadsForYouKept(), ThreadsForYouKept.decode("not json"))
        assertEquals(ThreadsForYouKept(), ThreadsForYouKept.decode("""{"v":99,"posts":[{"id":"1"}]}"""))
    }

    @Test
    fun aLongFeedKeepsThePostsAroundWhereTheReaderStopped() {
        val many = (1..200).map { post("$it") }
        val kept = ThreadsForYouKept.kept(many, ThreadsPlace("150"))
        assertEquals(ThreadsForYouKept.CAP, kept.size)
        assertTrue(kept.any { it.id == "150" })
        assertEquals("121", kept.first().id, "the last eighty, the place among them")
        assertEquals("30", ThreadsForYouKept.kept(many, ThreadsPlace("50")).first().id, "a few above the place, most below it")
        assertEquals("1", ThreadsForYouKept.kept(many, ThreadsPlace()).first().id)
        assertEquals("200", ThreadsForYouKept.kept(many, ThreadsPlace("199")).last().id)
        assertEquals(posts, ThreadsForYouKept.kept(posts, ThreadsPlace("3")))
    }

    @Test
    fun openingTheSectionReadsMetaOnlyWhenNothingIsShownOrItIsStaleAtItsTop() {
        val now = readAt.toEpochMilli()
        val hour = 60 * 60 * 1000L
        assertTrue(ThreadsForYouKept.readsOnOpen(ThreadsForYouState(), ThreadsPlace(), now), "nothing shown yet")
        val shown = ThreadsForYouState(posts = posts, readAt = readAt)
        assertFalse(ThreadsForYouKept.readsOnOpen(shown, ThreadsPlace(), now + 60_000), "read a minute ago")
        assertTrue(ThreadsForYouKept.readsOnOpen(shown, ThreadsPlace(), now + hour), "an hour old, at its top")
        assertFalse(ThreadsForYouKept.readsOnOpen(shown, ThreadsPlace("3", 10), now + hour), "part way down: the place is kept")
        assertFalse(ThreadsForYouKept.readsOnOpen(ThreadsForYouState(loaded = true), ThreadsPlace(), now), "Meta already answered with nothing")
        val refused = ThreadsForYouState(loaded = true, problem = ThreadsException(ThreadsFailure.THROTTLED, "429", 429))
        assertFalse(ThreadsForYouKept.readsOnOpen(refused, ThreadsPlace(), now), "a refusal is not asked again by itself")
        assertFalse(ThreadsForYouKept.readsOnOpen(ThreadsForYouState(loading = true), ThreadsPlace(), now))
    }

    @Test
    fun thePublicPageSaysSoAndASessionsOwnDoesNot() {
        val guest = ThreadsForYouRow.of(ThreadsForYouState(posts = posts, source = ThreadsForYouSource.GUEST, loaded = true), emptyList())
        assertEquals(listOf("public-note", "post:1", "post:2", "post:3", "post:4", "post:5", "end"), guest.map { it.key })

        val session = ThreadsForYouRow.of(ThreadsForYouState(posts = posts, source = ThreadsForYouSource.SESSION, next = "c", loaded = true), emptyList())
        assertEquals(listOf("post:1", "post:2", "post:3", "post:4", "post:5", "loading-more"), session.map { it.key })
    }

    @Test
    fun aRefusedSessionIsNamedAboveThePublicPage() {
        val state = ThreadsForYouState(posts = posts, source = ThreadsForYouSource.GUEST, loaded = true, sessionProblem = ThreadsFailure.UNAUTHORIZED)
        val rows = ThreadsForYouRow.of(state, emptyList())
        assertEquals(ThreadsForYouRow.SessionNotice(ThreadsFailure.UNAUTHORIZED), rows.first())
        assertFalse(rows.any { it is ThreadsForYouRow.PublicNote }, "the notice says it is the public page already")
    }

    @Test
    fun peopleToFollowSitAfterTheFirstPostsAndAFailedPageSaysWhyUnderTheFeed() {
        val failed = ThreadsException(ThreadsFailure.UNREACHABLE, "down", 500)
        val state = ThreadsForYouState(posts = posts, source = ThreadsForYouSource.SESSION, next = "c", loaded = true, moreProblem = failed)
        val rows = ThreadsForYouRow.of(state, listOf(person))
        assertEquals(listOf("post:1", "post:2", "post:3", "people", "post:4", "post:5", "more-problem"), rows.map { it.key })
        assertEquals(listOf("post:1", "people"), ThreadsForYouRow.of(state.copy(posts = posts.take(1), moreProblem = null, next = null), listOf(person)).map { it.key }.take(2))
        assertEquals(emptyList(), ThreadsForYouRow.of(ThreadsForYouState(loaded = true), listOf(person)), "no posts: the pane says why, not the list")
    }

    @Test
    fun thePlaceIsFoundAgainByItsPostWhateverStandsAboveIt() {
        val rows = ThreadsForYouRow.of(ThreadsForYouState(posts = posts, source = ThreadsForYouSource.GUEST, loaded = true), listOf(person))
        assertEquals(5, ThreadsForYouRow.indexOf(rows, ThreadsPlace("4", 12)), "under the note, three posts and the people strip")
        assertNull(ThreadsForYouRow.indexOf(rows, ThreadsPlace()), "the top")
        assertNull(ThreadsForYouRow.indexOf(rows, ThreadsPlace("gone")), "a post no longer in the feed")
        assertEquals("4", ThreadsForYouRow.postId("post:4"))
        assertNull(ThreadsForYouRow.postId("people"))
        assertNull(ThreadsForYouRow.postId(7))
    }
}
