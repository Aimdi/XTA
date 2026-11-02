package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live Reddit without an account, as XTA's test/live/reddit_smoke_test.dart
 * reads it: a public community's listing and its next page, and one thread.
 * Skipped unless RUN_LIVE=true. Reddit turns some networks away outright,
 * datacenters among them, with "blocked by network security"; the test is
 * skipped there too, saying so, since that answer says nothing about the reader.
 */
class RedditLiveTest {
    private val reddit = RedditClient(OkHttpXHttp())

    @Test
    fun aPublicCommunityListsItsPostsAndPagesOn() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Reddit")
        val first = live { reddit.listing("FlutterDev") }
        assertTrue(first.items.isNotEmpty())
        assertTrue(first.items.all { it.title.isNotEmpty() && "/comments/" in it.permalink })

        val next = live { reddit.listing("FlutterDev", after = assertNotNull(first.next, "a busy community has a next page")) }
        assertTrue(next.items.isNotEmpty())
        assertNotEquals(first.items.first().id, next.items.first().id)
        println("LIVE r/FlutterDev: ${first.items.size} posts, then ${next.items.size} after ${first.next}")
    }

    @Test
    fun aThreadReadsItsComments() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Reddit")
        val post = assertNotNull(live { reddit.listing("AskReddit") }.items.filterNot { it.stickied }.maxByOrNull { it.commentCount })
        val thread = live { reddit.thread(post.permalink) }

        assertTrue(thread.comments.any { !it.isStub && it.body.isNotEmpty() }, "${post.permalink} has comments to read")
        println("LIVE ${post.permalink}: ${thread.comments.size} top-level rows of ${post.commentCount} comments")
    }

    private suspend fun <T> live(read: suspend () -> T): T = try {
        read()
    } catch (e: NetworkRequestException) {
        assumeFalse("network security" in e.message.orEmpty(), "Reddit refuses this network: ${e.message}")
        throw e
    }
}
