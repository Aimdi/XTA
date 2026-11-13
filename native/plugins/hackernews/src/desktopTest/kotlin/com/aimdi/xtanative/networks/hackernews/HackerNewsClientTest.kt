package com.aimdi.xtanative.networks.hackernews

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class HackerNewsClientTest {
    private fun fixture(name: String) = javaClass.getResource("/hackernews/$name")!!.readText()

    /** Answers by path; records what was asked. */
    private class FakeHn(val answer: (HttpUrl) -> XResponse) : XHttp {
        val asked = ArrayList<HttpUrl>()
        val headers = ArrayList<Map<String, String>>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            synchronized(asked) {
                asked += url
                this.headers += headers
            }
            return answer(url)
        }
    }

    @Test
    fun anAlgoliaHitBecomesAStory() = runBlocking {
        val hn = FakeHn { XResponse(200, fixture("front_page.json")) }

        val page = HackerNewsClient(hn).feed(HnFeed.TOP)

        val url = hn.asked.single()
        assertEquals("hn.algolia.com", url.host)
        assertEquals("/api/v1/search", url.encodedPath)
        assertEquals("front_page", url.queryParameter("tags"))
        assertEquals("0", url.queryParameter("page"))
        assertEquals("30", url.queryParameter("hitsPerPage"))
        assertNull(url.queryParameter("query"))
        assertEquals("XTA Hacker News plugin", hn.headers.single()["User-Agent"])
        assertEquals("application/json", hn.headers.single()["Accept"])

        assertEquals("1", page.next, "nbPages 2: there is a page 1")
        val story = page.items.first()
        assertEquals(8863L, story.id)
        assertEquals("paulgraham.com", story.host)
        assertEquals("pg", story.author)
        assertEquals(100, story.score)
        assertEquals(12, story.commentCount)
        assertEquals(Instant.ofEpochSecond(1175714200), story.createdAt)
        assertEquals("story", story.type)
        assertEquals("", story.text)
    }

    @Test
    fun hitsTakeTheirTypeFromTagsAndAStoryIdStandsInForTheObjectId() = runBlocking {
        val page = HackerNewsClient(FakeHn { XResponse(200, fixture("front_page.json")) }).feed(HnFeed.TOP)

        assertEquals(listOf(8863L, 121003L, 42L), page.items.map { it.id }, "a hit with neither id is left out")
        val ask = page.items[1]
        assertEquals("ask", ask.type)
        assertNull(ask.url)
        assertNull(ask.host)
        assertEquals("or HN: the Next Iteration\n\nI get the impression that with Arc being released a lot of people...", ask.text)
        assertEquals("show", page.items[2].type)
        assertEquals(0, page.items[2].score)
    }

    @Test
    fun eachListAsksAlgoliaForItsTags() = runBlocking {
        val hn = FakeHn { XResponse(200, """{"nbPages": 1, "hits": []}""") }
        val client = HackerNewsClient(hn)

        for (feed in listOf(HnFeed.TOP, HnFeed.NEWEST, HnFeed.ASK, HnFeed.SHOW, HnFeed.JOBS)) client.feed(feed)

        assertEquals(
            listOf(
                "/api/v1/search" to "front_page",
                "/api/v1/search_by_date" to "story",
                "/api/v1/search" to "ask_hn",
                "/api/v1/search" to "show_hn",
                "/api/v1/search_by_date" to "job",
            ),
            hn.asked.map { it.encodedPath to it.queryParameter("tags") },
        )
    }

    @Test
    fun theNextPageIsAskedByItsNumberAndTheLastHasNoNext() = runBlocking {
        val hn = FakeHn { XResponse(200, fixture("front_page.json")) }

        val second = HackerNewsClient(hn).feed(HnFeed.TOP, cursor = "1")

        assertEquals("1", hn.asked.single().queryParameter("page"))
        assertNull(second.next, "page 1 of 2 is the last")
    }

    @Test
    fun searchAndSubmissionsAskForStories() = runBlocking {
        val hn = FakeHn { XResponse(200, """{"nbPages": 3, "hits": []}""") }
        val client = HackerNewsClient(hn)

        client.search("  rust async  ")
        client.search(" ")
        client.submissions(" pg ", cursor = "2")

        val (search, blank, submissions) = hn.asked
        assertEquals("/api/v1/search", search.encodedPath)
        assertEquals("story", search.queryParameter("tags"))
        assertEquals("rust async", search.queryParameter("query"))
        assertNull(blank.queryParameter("query"), "a blank query is left out")
        assertEquals("/api/v1/search_by_date", submissions.encodedPath)
        assertEquals("story,author_pg", submissions.queryParameter("tags"))
        assertEquals("2", submissions.queryParameter("page"))
    }

    @Test
    fun bestReadsFirebaseIdsThenItems() = runBlocking {
        val hn = FakeHn { url ->
            when (url.encodedPath) {
                "/v0/beststories.json" -> XResponse(200, "[1, 2]")
                "/v0/item/1.json" -> XResponse(200, fixture("item_1.json"))
                else -> XResponse(200, """{"id": 2, "type": "comment", "text": "nope"}""")
            }
        }

        val page = HackerNewsClient(hn).feed(HnFeed.BEST)

        assertEquals("hacker-news.firebaseio.com", hn.asked.first().host)
        assertEquals(setOf("/v0/beststories.json", "/v0/item/1.json", "/v0/item/2.json"), hn.asked.map { it.encodedPath }.toSet())
        val story = page.items.single()
        assertEquals("Best", story.title)
        assertEquals("alice", story.author)
        assertEquals(9, story.score)
        assertEquals(3, story.commentCount)
        assertEquals("example.com", story.host)
        assertNull(page.next)
    }

    @Test
    fun bestPagesThirtyIdsAtATime() = runBlocking {
        val ids = (1..45).joinToString(prefix = "[", postfix = "]")
        val hn = FakeHn { url ->
            when (url.encodedPath) {
                "/v0/beststories.json" -> XResponse(200, ids)
                else -> XResponse(200, """{"id": ${url.pathSegments.last().removeSuffix(".json")}, "type": "story", "title": "t"}""")
            }
        }
        val client = HackerNewsClient(hn)

        val first = client.feed(HnFeed.BEST)
        val second = client.feed(HnFeed.BEST, cursor = first.next)
        val past = client.feed(HnFeed.BEST, cursor = "2")

        assertEquals((1L..30L).toList(), first.items.map { it.id })
        assertEquals("1", first.next)
        assertEquals((31L..45L).toList(), second.items.map { it.id })
        assertNull(second.next)
        assertTrue(past.items.isEmpty())
        assertNull(past.next)
    }

    @Test
    fun aThreadKeepsNestedCommentsAndCountsThem() = runBlocking {
        val hn = FakeHn { XResponse(200, fixture("thread.json")) }

        val thread = HackerNewsClient(hn).thread(10)

        assertEquals("/api/v1/items/10", hn.asked.single().encodedPath)
        assertEquals("Ask HN: tools", thread.story.title)
        assertEquals("Which tools do you use?\n\nAsking for a friend.", thread.story.text)
        assertEquals(4, thread.story.commentCount, "every comment with an id, at any depth")
        val first = thread.comments.single()
        assertEquals("Try this", first.text)
        assertEquals(Instant.ofEpochSecond(1175714300), first.createdAt)
        assertEquals(listOf("dan", null), first.children.map { it.author })
        val deleted = first.children[1]
        assertTrue(deleted.deleted)
        assertEquals("", deleted.text)
        assertEquals("erin", deleted.children.single().author, "replies to a deleted comment keep their place")
        assertFalse(first.deleted)
    }

    @Test
    fun aFirebaseUserKeepsKarmaAndAbout() = runBlocking {
        val hn = FakeHn { XResponse(200, fixture("user_pg.json")) }

        val user = HackerNewsClient(hn).user(" pg ")

        assertEquals("/v0/user/pg.json", hn.asked.single().encodedPath)
        assertEquals("pg", user.id)
        assertEquals(155000, user.karma)
        assertEquals("Bug fixer of paulgraham.com.\n\nAbout me", user.about)
        assertEquals(Instant.ofEpochSecond(1160418092), user.createdAt)
        assertEquals(3, user.submittedCount)
    }

    @Test
    fun anUnknownUserIsA404() = runBlocking {
        val failure = assertFailsWith<NetworkRequestException> { HackerNewsClient(FakeHn { XResponse(200, "null") }).user("nobody") }
        assertEquals("hackernews", failure.network)
        assertEquals(404, failure.code)
    }

    @Test
    fun anythingButA200IsANetworkRequestException() = runBlocking {
        val failure = assertFailsWith<NetworkRequestException> { HackerNewsClient(FakeHn { XResponse(503, "") }).feed(HnFeed.TOP) }
        assertEquals(503, failure.code)
        val created = assertFailsWith<NetworkRequestException> { HackerNewsClient(FakeHn { XResponse(204, "") }).thread(1) }
        assertEquals(204, created.code, "XTA accepted only 200")
    }

    @Test
    fun anAnswerThatIsNotJsonIsAnEmptyPage() = runBlocking {
        val page = HackerNewsClient(FakeHn { XResponse(200, "<html>maintenance</html>") }).feed(HnFeed.NEWEST)
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
    }
}
