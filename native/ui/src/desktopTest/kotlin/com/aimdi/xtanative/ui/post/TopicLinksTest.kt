package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicLinksTest {
    @Test
    fun onlyAHashtagsLinkIsATopic() {
        assertEquals("#kotlin", TopicLinks.topicOf("#kotlin"))
        assertNull(TopicLinks.topicOf("#"))
        assertNull(TopicLinks.topicOf("\$AAPL"))
        assertNull(TopicLinks.topicOf("@reader"))
        assertNull(TopicLinks.topicOf("https://x.com/#top"))
        assertNull(TopicLinks.topicOf(null))
    }

    @Test
    fun followingATopicSavesItsSearchAndASecondPressTakesItAway() {
        val following = TopicLinks.toggled(Session(), "#kotlin", 1_000)
        assertTrue(TopicLinks.isFollowed(following, "#kotlin"))
        assertEquals(1_000, following.followedSearch("#kotlin")?.followedAt)
        val dropped = TopicLinks.toggled(following, "#kotlin", 2_000)
        assertFalse(TopicLinks.isFollowed(dropped, "#kotlin"))
        assertTrue(dropped.searches.isEmpty())
    }
}
