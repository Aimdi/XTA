package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckOptionsTest {
    private val ann = XUser("1", "ann", "Ann")
    private val bob = XUser("2", "bob", "Bob")

    private fun thread(post: XPost) = XThread(post.id, listOf(post))

    private val plain = thread(XPost("p", ann, null, "hi"))
    private val repost = thread(XPost("r", ann, null, "", reposted = XPost("o", bob, null, "orig")))
    private val reply = thread(XPost("a", ann, null, "@bob yes", replyToPostId = "b1", replyToScreenName = "bob"))
    private val ownThread = thread(XPost("t", ann, null, "2/2", replyToPostId = "t1", replyToScreenName = "Ann"))

    @Test
    fun aColumnLeavesOutRepostsAndRepliesToOthersAsItIsTold() {
        val all = listOf(plain, repost, reply, ownThread)
        assertEquals(all, all.filter(DeckColumnOptions()::keeps))
        assertEquals(listOf(plain, reply, ownThread), all.filter(DeckColumnOptions(hideReposts = true)::keeps))
        assertEquals(listOf(plain, repost, ownThread), all.filter(DeckColumnOptions(hideReplies = true)::keeps), "a thread its author goes on with stays")
        assertTrue(DeckColumnOptions(hideReplies = true).filters)
        assertFalse(DeckColumnOptions(mediaOnly = true, width = "wide").filters)
    }

    @Test
    fun theOptionsAreKeptBesideTheDeckAndGoWithTheirColumn() {
        val options = DeckColumnOptions(width = "narrow", hideReposts = true, mediaOnly = true, unreadCount = true)
        val session = Session(groups = listOf(FollowGroup("g1", "News", emptyList())), deck = listOf("@following", "g1"))
            .withDeckOptions("g1", options).withDeckOptions("@following", DeckColumnOptions())
        assertEquals(mapOf("g1" to options), session.deckOptions, "a plain column keeps nothing")
        val read = SessionCodec.decode(SessionCodec.encode(session))!!
        assertEquals(session.deck, read.deck)
        assertEquals(session.deckOptions, read.deckOptions)
        assertEquals(DeckColumnOptions(), SessionCodec.decode(SessionCodec.encode(Session()))!!.deckOptionsOf("g1"))
        assertEquals(emptyMap(), session.withoutDeckColumn("g1").deckOptions)
        assertEquals(listOf("@following"), session.withoutDeckColumn("g1").deck)
        assertEquals(emptyMap(), session.withoutGroup("g1").deckOptions, "a group gone takes its column's choices")
    }

    @Test
    fun columnsOfTheirOwnWidthShareTheSpareRoom() {
        assertEquals(listOf(320f, 460f), DeckColumns.widths(700f, listOf(320f, 460f)), "they do not fit: each keeps its own")
        assertEquals(listOf(370f, 510f), DeckColumns.widths(880f, listOf(320f, 460f)))
        assertEquals(emptyList(), DeckColumns.widths(880f, emptyList()))
    }
}
