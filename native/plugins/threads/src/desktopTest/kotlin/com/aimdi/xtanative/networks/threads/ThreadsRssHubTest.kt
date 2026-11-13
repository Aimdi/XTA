package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** An RSSHub instance's JSON Feed (XTA's threads_client.dart and threads_test). */
class ThreadsRssHubTest {
    private val feed = ThreadsRssHub.parseFeed(J.parse(fixture("rsshub_feed.json")), "zuck")

    @Test
    fun theInstanceIsAskedForJsonWhateverTrailingSlashesItWasGiven() {
        assertEquals("https://rsshub.example.org/threads/zuck?format=json", ThreadsRssHub.feedUrl("https://rsshub.example.org", "zuck").toString())
        assertEquals("https://rsshub.example.org/threads/zuck?format=json", ThreadsRssHub.feedUrl(" https://rsshub.example.org/// ", "zuck").toString())
        assertNull(ThreadsRssHub.feedUrl("rsshub without a scheme", "zuck"))
    }

    @Test
    fun anItemGivesItsWordsLinkDateAndPictures() {
        val first = feed.first()
        assertEquals("https://www.threads.com/@zuck/post/DRss000001", first.id)
        assertEquals("https://www.threads.com/@zuck/post/DRss000001", first.url)
        assertEquals("Hello there\nsecond line\nNew paragraph", first.text, "line breaks and paragraph ends are kept")
        assertEquals(listOf("https://example.org/a.jpg", "https://example.org/b.jpg"), first.media.map { it.url },
            "in order, no repeats, and a video is not a picture")
        assertEquals(Instant.parse("2026-08-01T09:00:00Z"), first.publishedAt)
        assertEquals("zuck", first.handle)
        assertFalse(first.hasEngagement, "a feed carries no counts, so none is invented")
    }

    @Test
    fun theAuthorIsTheFeedsTitleWithoutTheSiteUnlessAnItemNamesItsOwn() {
        assertEquals("Mark Zuckerberg", feed.first().authorName)
        assertEquals("https://example.org/avatar.jpg", feed.first().avatarUrl)
        val own = feed[1]
        assertEquals("Mark", own.authorName)
        assertEquals("https://example.org/mark.jpg", own.avatarUrl)
        fun author(title: String) = ThreadsRssHub.parseFeed(j(mapOf("title" to title, "items" to listOf(mapOf("id" to "1", "content_text" to "x")))), "zuck").single().authorName
        assertEquals("zuck", author(""))
        assertEquals("zuck", author("@zuck | Threads"))
        assertEquals("Mark", author("Mark – Threads"))
    }

    @Test
    fun contentTextIsTakenAsGivenAndAnOffsetTimeIsRead() {
        assertEquals("Plain words win", feed[1].text)
        assertNull(feed[1].url)
        assertEquals(Instant.parse("2026-07-30T16:30:00Z"), feed[1].publishedAt)
    }

    @Test
    fun aPictureOnlyItemIsKeptAndAnEmptyOneOrOneWithoutAnyIdIsNot() {
        assertEquals(listOf("https://www.threads.com/@zuck/post/DRss000001", "https://www.threads.com/@zuck/post/DRss000002", "picture-only"), feed.map { it.id })
        assertEquals("", feed.last().text)
        assertEquals(listOf("https://example.org/only.jpg"), feed.last().media.map { it.url })
    }

    @Test
    fun aReshapedFeedYieldsNothingRatherThanThrowing() {
        assertTrue(ThreadsRssHub.parseFeed(J(null), "zuck").isEmpty())
        assertTrue(ThreadsRssHub.parseFeed(j(mapOf("items" to "not a list")), "zuck").isEmpty())
    }

    @Test
    fun theInstanceIsAskedOnceAndItsAnswerRead() = runBlocking {
        val meta = FakeMeta { ok(fixture("rsshub_feed.json")) }
        val posts = ThreadsRssHub(meta).account("https://rsshub.example.org/", "zuck")
        assertEquals("https://rsshub.example.org/threads/zuck?format=json", meta.asked.single().url.toString())
        assertEquals(3, posts.items.size)
        assertNull(posts.next)
        ThreadsRssHub(meta).verify("https://rsshub.example.org")
        assertEquals("/threads/zuck", meta.asked.last().path, "a real feed, not the front page")
    }

    @Test
    fun whatTheInstanceSaidIsNamed() {
        fun failure(answer: XResponse, instance: String = "https://rsshub.example.org"): ThreadsException =
            assertFailsWith { runBlocking { ThreadsRssHub(FakeMeta { answer }).account(instance, "zuck") } }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, failure(XResponse(404, "")).failure)
        val throttled = failure(XResponse(429, ""))
        assertEquals(ThreadsFailure.THROTTLED, throttled.failure)
        assertEquals(ThreadsRetry.MANUAL, throttled.retry, "the instance gave no time to come back")
        assertEquals(ThreadsFailure.UNREACHABLE, failure(XResponse(500, "")).failure)
        assertEquals(ThreadsFailure.UNREACHABLE, failure(ok("<html>Error</html>")).failure, "an error page is no feed")
        assertEquals(ThreadsFailure.NOT_CONFIGURED, failure(ok("{}"), instance = "  ").failure)
        assertEquals(ThreadsFailure.UNREACHABLE, failure(ok("{}"), instance = "no scheme").failure)
    }
}
