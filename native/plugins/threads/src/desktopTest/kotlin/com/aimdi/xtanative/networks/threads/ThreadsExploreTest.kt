package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * For you past the public page's five posts: a guest's refresh grows the
 * feed, the feed passes over a page with nothing new, it goes on into the
 * topics and communities it explores, and the topics to show come followed
 * first, communities next, the most posted in before the rest.
 */
class ThreadsExploreTest {
    private val time = FakeTime()

    private fun post(id: String) = ThreadsPost(id = id, handle = "h$id", authorName = "H", text = "t")

    private fun guestPage(vararg ids: String, next: String? = null) = ThreadsForYouPage(ThreadsForYouSource.GUEST, ids.map(::post), next)

    @Test
    fun aGuestsRefreshPutsTheNewPostsAboveThoseShownAndASessionsReplacesThem(): Unit = runBlocking {
        var round = 0
        val guest = ThreadsForYouFeed({ if (round++ == 0) guestPage("1", "2") else guestPage("3", "1") })
        guest.refresh()
        guest.refresh()
        assertEquals(listOf("3", "1", "2"), guest.state.value.posts.map { it.id })
        var turn = 0
        val session = ThreadsForYouFeed({
            ThreadsForYouPage(ThreadsForYouSource.SESSION, (if (turn++ == 0) listOf("1", "2") else listOf("3")).map(::post))
        })
        session.refresh()
        session.refresh()
        assertEquals(listOf("3"), session.state.value.posts.map { it.id })
    }

    @Test
    fun aPageWithNothingNewIsPassedOverForTheOneItNames(): Unit = runBlocking {
        val feed = ThreadsForYouFeed({ cursor ->
            when (cursor) {
                null -> guestPage("1", next = "a")
                "a" -> guestPage("1", next = "b")
                else -> guestPage("2")
            }
        })
        feed.refresh()
        feed.more()
        assertEquals(listOf("1", "2"), feed.state.value.posts.map { it.id })
        assertEquals(null, feed.state.value.next)
    }

    @Test
    fun pastTheFrontPageForYouExploresEachTopicOnce(): Unit = runBlocking {
        var fronts = 0
        val meta = FakeMeta { asked ->
            when (asked.path) {
                "/search" -> ok(page(listOf(listOf(postJson("t-${asked.url.queryParameter("q")}", user = "maya")))))
                else -> ok(page(listOf(listOf(postJson("front-${fronts++}", user = "nasa")))))
            }
        }
        val forYou = ThreadsForYou(direct(meta, time = time)) { listOf("Books", "books", "Art") }
        val pages = generateSequence(forYou.page()) { page -> page.next?.let { runBlocking { forYou.page(it) } } }.toList()
        assertEquals(listOf("guest:1", "guest:2", "guest:3", "guest:4", "explore:0", "explore:1", null), pages.map { it.next })
        assertEquals(listOf("t-Books", "t-Art"), pages.takeLast(2).map { it.posts.single().id })
        assertEquals(listOf("Books", "Art"), meta.asked.filter { it.path == "/search" }.map { it.url.queryParameter("q") })
        assertEquals("guest:1", forYou.page().next, "a new selection explores every topic again")
    }

    @Test
    fun aFrontPageThatShowsAGuestNothingGivesWayToExploring(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.path == "/search") ok(page(listOf(listOf(postJson("t1", user = "maya"))))) else ok(page(emptyList()))
        }
        val first = ThreadsForYou(direct(meta, time = time)) { listOf("Books") }.page()
        assertEquals(listOf("t1"), first.posts.map { it.id })
        assertEquals(ThreadsForYouSource.GUEST, first.source)
    }

    @Test
    fun theTopicsToExploreComeFollowedFirstThenCommunitiesThenTheMostPostedIn() {
        fun tagged(id: String, topic: String, community: Boolean = false) =
            post(id).copy(topicTag = topic, facts = ThreadsPostFacts(community = community))
        val posts = listOf(tagged("1", "Books"), tagged("2", "Art"), tagged("3", "Art"), tagged("4", "Cosplayers", community = true), post("5"))
        val topics = ThreadsDiscovery.topics(posts, followed = listOf(ThreadsTopic("Knitting"), ThreadsTopic("books")))
        assertEquals(listOf("knitting", "books", "cosplayers", "art"), topics.map { it.key })
        assertEquals(2, ThreadsDiscovery.topics(posts, cap = 2).size)
        assertEquals("https://www.threads.com/communities/9", ThreadsDiscovery.page(ThreadsTopic("C", community = true, url = "https://www.threads.com/communities/9")))
    }

}
