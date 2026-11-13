package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** What a signed-in reader reads of their own Threads: the Following feed, saved and liked posts, and activity; how each is asked, read, and only read. */
class ThreadsSignedInTest {
    private val time = FakeTime()
    private val bearer = ThreadsSession(bearer = "Bearer IGT:2:made-up")

    private fun followedBy(pk: String, user: String, following: Boolean?) = postJson(pk, user = user).let { post ->
        val author = (post["user"] as Map<*, *>) + listOfNotNull(following?.let { "friendship_status" to mapOf("following" to it) })
        post + ("user" to author)
    }

    private fun timeline(vararg posts: Map<String, Any?>) =
        json(mapOf("threads" to posts.map { mapOf("thread_items" to listOf(mapOf("post" to it))) }, "next_max_id" to "n")).toString()

    @Test
    fun theFollowingFeedIsAskedAsForYouIsWithItsOwnFeedTypeAndTheListsOnlyWithACursor() {
        assertEquals(ThreadsOwnFeeds.TIMELINE, ThreadsOwnFeeds.paths(ThreadsOwnFeed.FOLLOWING).first())
        val first = ThreadsOwnFeeds.params(ThreadsOwnFeed.FOLLOWING, null, "dev").toMap()
        assertEquals("following", first["feed_type"])
        assertEquals("[]", first["feed_view_info"], "no post is reported as seen")
        assertEquals("cold_start_fetch", first["reason"])
        assertEquals("dev", first["client_session_id"])
        assertNull(first["max_id"])
        val next = ThreadsOwnFeeds.params(ThreadsOwnFeed.FOLLOWING, " c ", "dev").toMap()
        assertEquals("pagination", next["reason"])
        assertEquals("c", next["max_id"])
        assertEquals(listOf("max_id" to "c"), ThreadsOwnFeeds.params(ThreadsOwnFeed.SAVED, "c", "dev"))
        assertEquals(emptyList(), ThreadsOwnFeeds.params(ThreadsOwnFeed.LIKED, "", "dev"))
        ThreadsOwnFeed.entries.forEach { feed -> assertTrue(ThreadsOwnFeeds.paths(feed).all { it.startsWith("/api/v1/") && it.endsWith("/") }) }
    }

    @Test
    fun aPageOfOwnPostsReadsATimelineOrAPlainListUnderMediaOrPostEachOnce() {
        val timeline = ThreadsOwnFeeds.page(j(mapOf("threads" to listOf(mapOf("thread_items" to listOf(mapOf("post" to postJson("1"))))), "next_max_id" to "n")))
        assertEquals(listOf("1"), timeline.items.map { it.id })
        assertEquals("n", timeline.next)
        val saved = ThreadsOwnFeeds.page(
            j(mapOf("items" to listOf(mapOf("media" to postJson("2")), mapOf("post" to postJson("3")), postJson("4"), mapOf("media" to postJson("2"))), "more_available" to true, "next_max_id" to 9)),
        )
        assertEquals(listOf("2", "3", "4"), saved.items.map { it.id })
        assertEquals("9", saved.next)
        assertNull(ThreadsOwnFeeds.page(j(mapOf("items" to listOf(mapOf("media" to postJson("5"))), "more_available" to false, "next_max_id" to "x"))).next)
        assertEquals(emptyList(), ThreadsOwnFeeds.page(j(mapOf("status" to "ok"))).items)
    }

    @Test
    fun aTimelineWhoseAuthorsMetaMarksAsMostlyNotFollowedCannotBeTheFollowingFeed() {
        assertTrue(ThreadsOwnFeeds.isFollowing(j(mapOf("threads" to emptyList<Any>()))), "nothing to tell by")
        assertTrue(ThreadsOwnFeeds.isFollowing(j(ThreadsJson.decode(timeline(followedBy("1", "a", null))))), "no marks")
        assertTrue(ThreadsOwnFeeds.isFollowing(j(ThreadsJson.decode(timeline(followedBy("1", "a", true), followedBy("2", "b", false))))))
        assertFalse(ThreadsOwnFeeds.isFollowing(j(ThreadsJson.decode(timeline(followedBy("1", "a", false), followedBy("2", "b", false), followedBy("3", "c", true))))))
    }

    @Test
    fun theFollowingFeedWithCookiesPassesOverAnAnswerThatIsForYouAndRemembersThePathThatServesIt(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.path) {
                ThreadsOwnFeeds.TIMELINE -> ok(timeline(followedBy("1", "a", false), followedBy("2", "b", false)))
                else -> ok(timeline(followedBy("3", "c", true)))
            }
        }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)
        assertEquals(listOf("3"), client.ownFeed(ThreadsOwnFeed.FOLLOWING).items.map { it.id })
        assertEquals(ThreadsOwnFeeds.paths(ThreadsOwnFeed.FOLLOWING), meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.method == "GET" && it.url.host == "www.threads.com" && it.url.queryParameter("feed_type") == "following" })
        client.ownFeed(ThreadsOwnFeed.FOLLOWING, cursor = "n")
        assertEquals("/api/v1/text_feed/following_timeline/", meta.asked.last().path, "the path that served it is asked first from then on")
        assertEquals(3, meta.asked.size)
    }

    @Test
    fun savedPostsWithABearerReadTheAppApiAndNoPathAnsweringIsNoSuchList(): Unit = runBlocking {
        val meta = FakeMeta { XResponse(404, "") }
        val failure = assertFailsWith<ThreadsException> { direct(meta, bearer, time = time).ownFeed(ThreadsOwnFeed.SAVED) }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, failure.failure)
        assertEquals(ThreadsOwnFeeds.paths(ThreadsOwnFeed.SAVED), meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.method == "GET" && it.url.host == "i.instagram.com" && it.headers["Authorization"] == "Bearer IGT:2:made-up" })
    }

    @Test
    fun activityIsAskedNeverToMarkAnythingSeenWithThreadsOwnFilters() {
        val all = ThreadsActivity.params(ThreadsActivityFilter.ALL, null).toMap()
        assertEquals("false", all["mark_as_seen"])
        assertNull(all["selected_filters"])
        val replies = ThreadsActivity.params(ThreadsActivityFilter.REPLIES, "c").toMap()
        assertEquals("text_post_app_replies", replies["selected_filters"])
        assertEquals("false", replies["mark_as_seen"])
        assertEquals("c", replies["max_id"])
        assertEquals("text_post_app_mentions", ThreadsActivity.params(ThreadsActivityFilter.MENTIONS, null).toMap()["selected_filters"])
    }

    @Test
    fun aPageOfActivityReadsNewThenOldStoriesWithTheirKindsWhoAndThePost() {
        val body = j(
            mapOf(
                "new_stories" to listOf(
                    mapOf(
                        "notif_name" to "text_post_app_reply",
                        "args" to mapOf(
                            "text" to "ann replied to your thread",
                            "profile_name" to "Ann",
                            "profile_image" to "https://cdn.example/a.jpg",
                            "timestamp" to 1_700_000_000.5,
                            "tuuid" to "t1",
                            "media" to listOf(mapOf("id" to "3141_99", "image" to "https://cdn.example/m.jpg")),
                        ),
                    ),
                ),
                "old_stories" to listOf(
                    mapOf("notif_name" to "comment_like", "args" to mapOf("rich_text" to "{bob|000000|1|user?id=2} liked your reply.", "profile_name" to "bob", "media" to listOf(mapOf("shortcode" to "Cx1")))),
                    mapOf("story_type" to 101, "args" to mapOf("text" to "cy started following you", "profile_name" to "cy", "profile_image" to "http://plain.example/c.jpg", "timestamp" to 1_700_000_000_000)),
                    mapOf("notif_name" to "text_post_app_mention", "args" to mapOf("text" to "  ")),
                    mapOf("notif_name" to "text_post_app_reply", "args" to mapOf("text" to "ann replied to your thread", "tuuid" to "t1")),
                ),
                "next_max_id" to "after",
            ),
        )
        val page = ThreadsActivity.page(body)
        assertEquals(3, page.items.size, "a line without words is left out, and each line once")
        val (reply, like, follow) = page.items
        assertEquals(ThreadsActivityKind.REPLY, reply.kind)
        assertEquals("ann", reply.handle)
        assertEquals("https://cdn.example/a.jpg", reply.picture)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_500), reply.at)
        assertEquals(ThreadsPeople.shortcodeOf("3141"), reply.postCode)
        assertEquals("https://www.threads.com/t/${reply.postCode}", reply.postUrl)
        assertEquals(ThreadsActivityKind.LIKE, like.kind, "a like of a reply is a like")
        assertEquals("bob liked your reply.", like.text)
        assertEquals("Cx1", like.postCode)
        assertEquals(ThreadsActivityKind.OTHER, follow.kind, "a story Meta names no kind of")
        assertNull(follow.picture, "a face over plain http is not shown")
        assertNull(follow.postUrl)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), follow.at, "a time in milliseconds")
        assertEquals("after", page.next)
        assertNull(ThreadsActivity.page(j(mapOf("new_stories" to listOf(mapOf("args" to mapOf("text" to "x"))), "next_max_id" to "a", "is_last_page" to true))).next)
        assertNull(ThreadsActivity.page(j(mapOf("status" to "ok", "next_max_id" to "a"))).next, "a page with nothing names no next page")
    }

    @Test
    fun storiesAreKnownByTheWordsInTheirNames() {
        fun kind(name: String) = ThreadsActivity.kindOf(j(mapOf("notif_name" to name)))
        assertEquals(ThreadsActivityKind.MENTION, kind("text_post_app_mention"))
        assertEquals(ThreadsActivityKind.QUOTE, kind("text_post_app_quote"))
        assertEquals(ThreadsActivityKind.REPOST, kind("text_post_app_repost"))
        assertEquals(ThreadsActivityKind.LIKE, kind("text_post_app_like"))
        assertEquals(ThreadsActivityKind.FOLLOW, kind("user_followed"))
        assertEquals(ThreadsActivityKind.REPLY, kind("comment"))
        assertEquals(ThreadsActivityKind.OTHER, kind(""))
        assertEquals("ann and bob", ThreadsActivity.plain("{ann|000000|1|user?id=1} and {bob|000000|1|user?id=2}"))
    }

    @Test
    fun aMediaIdAndItsShortCodeSpellEachOther() {
        listOf("1", "63", "64", "3141592653589793238", "3456789012345678901").forEach { id ->
            assertEquals(id, ThreadsPeople.shortcodeOf(id)?.let(ThreadsPeople::idOfShortcode))
        }
        assertNull(ThreadsPeople.shortcodeOf("0"))
        assertNull(ThreadsPeople.shortcodeOf("C1abc"))
        assertNull(ThreadsPeople.shortcodeOf(""))
    }

    @Test
    fun activityFallsBackToTheInboxPathWithAGetAndAThrottleStopsAtOnceAndParksTheSession(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.path) {
                ThreadsActivity.PATHS.first() -> XResponse(404, "")
                else -> ok("""{"new_stories":[{"args":{"text":"hello","tuuid":"1"}}]}""")
            }
        }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)
        assertEquals(listOf("hello"), client.activity().items.map { it.text })
        assertEquals(ThreadsActivity.PATHS, meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.method == "GET" && it.url.queryParameter("mark_as_seen") == "false" })

        val throttled = FakeMeta { XResponse(429, "") }
        val parked = direct(throttled, ThreadsSession(COOKIES), time = time)
        assertEquals(ThreadsFailure.THROTTLED, assertFailsWith<ThreadsException> { parked.activity(ThreadsActivityFilter.MENTIONS) }.failure)
        assertEquals(1, throttled.asked.size, "a throttle is not taken for a missing path")
        assertTrue(parked.isSessionParked)

        val none = FakeMeta { error("nothing is asked") }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, assertFailsWith<ThreadsException> { direct(none, time = time).activity() }.failure)
        assertEquals(ThreadsFailure.NOT_CONFIGURED, assertFailsWith<ThreadsException> { direct(none, time = time).ownFeed(ThreadsOwnFeed.LIKED) }.failure)
    }
}
