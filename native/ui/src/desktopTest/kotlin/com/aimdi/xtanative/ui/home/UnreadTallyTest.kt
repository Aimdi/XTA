package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class UnreadTallyTest {
    @Test
    fun theTotalCountsAFeedWithNoCountOnce() {
        val now = UnreadNow(lit = setOf("following", "for_you", "group:g1"), counts = mapOf("following" to 12, "group:g1" to 3, "group:g2" to 5))
        assertEquals(16, now.total, "For you keeps no posts: one; g2 is not lit")
        assertEquals(12, now.count("following"))
        assertNull(now.count("for_you"))
        assertNull(now.count("group:g2"))
        assertEquals(0, UnreadNow().total)
    }

    @Test
    fun aDeckColumnSharesItsHomeTimelinesMarks() {
        assertEquals(HomeTab.Following, UnreadTally.tabOf(DeckColumn.Following))
        assertEquals(HomeTab.ForYou, UnreadTally.tabOf(DeckColumn.ForYou))
        assertEquals("g1", HomeUnread.feedKey(UnreadTally.tabOf(DeckColumn.Group("g1"))!!))
        assertNull(UnreadTally.tabOf(DeckColumn.Network("rss")))
    }

    @Test
    fun markingAllReadMarksTheLitFeedsOnly() {
        val session = Session(groups = listOf(FollowGroup("g1", "One", emptyList()), FollowGroup("g2", "Two", emptyList())))
        val lit = setOf(HomeTab.Following.key, HomeTab.Group("g2", "").key)
        assertEquals(listOf("following", "g2"), UnreadTally.unreadFeeds(session, lit))
    }

    @Test
    fun onlyASessionChangeOfTheCountedFeedsCountsAgain() {
        val session = Session(groups = listOf(FollowGroup("g1", "One", emptyList())))
        val counted = UnreadTally.countedBy(session)
        assertEquals(counted, UnreadTally.countedBy(session.copy(autoplay = false, deck = listOf("g1"))))
        assertEquals(counted, UnreadTally.countedBy(session.copy(groups = listOf(FollowGroup("g1", "One", listOf("someone"))))), "members feed the posts, not the count")
        assertNotEquals(counted, UnreadTally.countedBy(session.copy(groups = session.groups + FollowGroup("g2", "Two", emptyList()))))
        assertNotEquals(counted, UnreadTally.countedBy(session.copy(feeds = mapOf("g1" to FeedSettings(catchUp = true)))))
        assertNull(UnreadTally.countedBy(null))
    }
}
