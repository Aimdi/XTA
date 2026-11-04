package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.NetworkPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun xThread(id: String, at: Long?): XThread {
    val author = XUser(id = "1", screenName = "someone", name = "Some One")
    return XThread(id, listOf(XPost(id = id, author = author, text = "", createdAt = at?.let(Instant::ofEpochSecond))))
}

internal fun sourced(source: String, id: String, at: Long?): SourcedPost =
    SourcedPost(source, NetworkPost(id, "a", "A", "a", createdAt = at?.let(Instant::ofEpochSecond)))

class FeedMixTest {
    @Test
    fun aPluginPostGoesAboveTheFirstThreadOlderThanIt() {
        val threads = listOf(xThread("3", 300), xThread("2", 200), xThread("1", 100))
        val buckets = FeedMix.place(threads, listOf(sourced("bluesky", "b", 250), sourced("reddit", "r", 400), sourced("bluesky", "c", 150)))
        assertEquals(listOf("r"), buckets[0].map { it.post.id })
        assertEquals(listOf("b"), buckets[1].map { it.post.id })
        assertEquals(listOf("c"), buckets[2].map { it.post.id })
        assertTrue(buckets[3].isEmpty())
    }

    @Test
    fun postsOlderThanEveryThreadRideAtTheEndUntilPagingReachesThem() {
        val threads = listOf(xThread("2", 200), xThread("1", 100))
        val buckets = FeedMix.place(threads, listOf(sourced("bluesky", "old", 50), sourced("bluesky", "older", 10)))
        assertEquals(listOf("old", "older"), buckets.last().map { it.post.id }, "newest first")
        assertEquals(emptyList<SourcedPost>() to listOf("old", "older"), FeedMix.around(buckets, 1).let { it.first to it.second.map { p -> p.post.id } })
        assertTrue(FeedMix.around(buckets, 0).second.isEmpty(), "only the last thread carries them")
    }

    @Test
    fun aThreadWithoutATimeIsPassedOverAndATieGoesBelow() {
        val threads = listOf(xThread("9", null), xThread("2", 200))
        val buckets = FeedMix.place(threads, listOf(sourced("bluesky", "same", 200), sourced("bluesky", "newer", 201)))
        assertTrue(buckets[0].isEmpty(), "nothing to compare with")
        assertEquals(listOf("newer"), buckets[1].map { it.post.id })
        assertEquals(listOf("same"), buckets[2].map { it.post.id }, "not newer than the thread: after it")
    }

    @Test
    fun aThreadSitsAtItsNewestPost() {
        val author = XUser(id = "1", screenName = "someone", name = "Some One")
        val posts = listOf(100L, 300L).mapIndexed { i, at -> XPost(id = "p$i", author = author, createdAt = Instant.ofEpochSecond(at), text = "") }
        val thread = XThread("t", posts)
        assertEquals(300_000L, FeedMix.timeOf(thread))
        assertEquals(null, FeedMix.timeOf(xThread("x", null)))
    }

    @Test
    fun withNoThreadsEverythingIsInTheLastBucket() {
        val buckets = FeedMix.place(emptyList(), listOf(sourced("bluesky", "a", 1)))
        assertEquals(1, buckets.size)
        assertEquals(listOf("a"), buckets.single().map { it.post.id })
    }

    @Test
    fun theHomeTimelinesAskTheFollowedAccountsOfPluginsSwitchedOnAndGroupsTheirMembers() {
        val kinds = FeedKinds(replies = false, reposts = true)
        val home = FeedSources.asks(setOf("reddit", "bluesky"), emptyMap(), home = true, inHome = { it == "bluesky" }, kinds = kinds)
        assertEquals(mapOf("bluesky" to SourceAsk(followed = true, kinds = kinds)), home)
        val group = FeedSources.asks(setOf("reddit", "bluesky"), mapOf("reddit" to listOf("kotlin")), home = false, inHome = { true }, kinds = kinds)
        assertEquals(mapOf("reddit" to SourceAsk(listOf("kotlin"), followed = false, kinds = kinds)), group, "a group reads its members, not the switch")
        val gone = FeedSources.asks(emptySet(), mapOf("reddit" to listOf("kotlin")), home = false, inHome = { true }, kinds = kinds)
        assertTrue(gone.isEmpty(), "a plugin that is not running is asked nothing")
    }

    @Test
    fun anAccountBothAMemberAndFollowedIsAskedOnce() {
        val ask = SourceAsk(listOf("alice", "bob"), followed = true)
        assertEquals(listOf("alice", "bob", "carol"), ask.accounts(listOf("bob", "carol")))
        assertEquals(listOf("alice"), SourceAsk(listOf("alice")).accounts(listOf("bob")), "not followed: the members alone")
    }

    @Test
    fun aRepostFollowsTheRepostSettingAndAReplyTheReplySetting() {
        val noReplies = FeedKinds(replies = false, reposts = true)
        assertTrue(noReplies.takes(isReply = true, isRepost = true), "a repost of a reply is a repost")
        assertEquals(false, noReplies.takes(isReply = true, isRepost = false))
        assertEquals(false, FeedKinds(replies = true, reposts = false).takes(isReply = false, isRepost = true))
        assertTrue(FeedKinds.ALL.takes(isReply = true, isRepost = false))
    }
}
