package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.feed.FeedPause
import com.aimdi.xtanative.core.feed.FeedStops
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.saved.ProfileFilters
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's For you tab (lib/home/_for_you.dart, the X timeline with includeReplies off). */
class ForYouRulesTest {
    private fun post(id: String, replyTo: String? = null, reposted: XPost? = null) =
        XPost(id, XUser("u$id", "u$id", "U $id"), null, "post $id", replyToPostId = replyTo, reposted = reposted)

    private fun thread(vararg posts: XPost) = XThread(posts.first().id, posts.toList())

    @Test
    fun repliesAreLeftOut() {
        val root = post("1")
        val reply = post("2", replyTo = "1")
        val threads = listOf(thread(root, reply), thread(post("3", replyTo = "9")), thread(post("4")))
        val shown = ForYouRules.withoutReplies(threads)
        assertEquals(listOf("1", "4"), shown.map { it.id })
        assertEquals(listOf("1"), shown.first().posts.map { it.id })
        assertTrue(ForYouRules.withoutReplies(listOf(threads.last())).single() === threads.last())
    }

    @Test
    fun aRepostOfAReplyIsTheReposterAndStays() {
        val repost = post("5", reposted = post("6", replyTo = "1"))
        assertEquals(listOf("5"), ForYouRules.withoutReplies(listOf(thread(repost))).map { it.id })
    }

    @Test
    fun anAccountTheReaderHidIsOutOfForYouAlsoWhenReposted() {
        val hidden = ProfileFilters(hiddenAll = mapOf("u2" to "u2"), hiddenReposts = mapOf("u5" to "u5"))
        val threads = listOf(thread(post("1")), thread(post("2")), thread(post("3", reposted = post("2"))), thread(post("5", reposted = post("6"))))
        assertEquals(listOf("1", "5"), ForYouRules.shown(threads, hidden).map { it.id }, "the reposts' filter is for the feeds of accounts followed")
        assertTrue(ForYouRules.shown(threads, ProfileFilters()) === threads)
    }

    @Test
    fun aWordMutedEverywhereIsOutOfForYouWhetherItHidesOrFolds() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val threads = listOf(thread(post("1")), thread(post("2")), thread(post("3", reposted = post("4"))), thread(post("5")))
        val muted = listOf(MutedKeyword("post 2"), MutedKeyword("4", action = KeywordAction.FOLD), MutedKeyword("5", until = now.minusSeconds(1)))
        assertEquals(listOf("1", "5"), ForYouRules.shown(threads, ProfileFilters(), muted, now).map { it.id }, "For you folds nothing, and an expired word mutes nothing")
        val hidden = ProfileFilters(hiddenAll = mapOf("u1" to "u1"))
        assertEquals(listOf("5"), ForYouRules.shown(threads, hidden, muted, now).map { it.id }, "beside the accounts hidden")
        assertTrue(ForYouRules.shown(threads, ProfileFilters(), emptyList(), now) === threads)
    }

    @Test
    fun morePagesAddEachThreadOnce() {
        val shown = listOf(thread(post("1")), thread(post("2")))
        assertEquals(listOf("1", "2", "3"), ForYouRules.appended(shown, listOf(thread(post("2")), thread(post("3")), thread(post("3")))).map { it.id })
    }

    @Test
    fun zenModePausesAfterItsPages() {
        assertNull(ForYouRules.pageCap(zenMode = false, pages = 5))
        assertEquals(5, ForYouRules.pageCap(zenMode = true, pages = 5))
        assertEquals(1, ForYouRules.pageCap(zenMode = true, pages = 0))
        val threads = listOf(thread(post("1")))
        val first = FeedStops(pageCap = 2).page(threads, first = true, isSeen = null, hasMore = true)
        assertNull(first.stops.pause)
        val second = first.stops.page(threads, first = false, isSeen = null, hasMore = true)
        assertEquals(FeedPause.PAGE_CAP, second.stops.pause)
        assertTrue(second.stops.canContinue)
        assertNull(second.stops.resume().stops.pause)
    }
}
