package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionTest {
    private val session = Session(
        accounts = listOf(XAccount("ct0", "maya", mapOf("Cookie" to "auth_token=a; ct0=ct0", "x-csrf-token" to "ct0"))),
        following = listOf(
            FollowedAccount("1", "nasa", "NASA", "https://pbs.twimg.com/a_normal.jpg", verified = true, inFeed = true, followedAt = 5),
            FollowedAccount("2", "esa", "ESA", null, verified = false, inFeed = false, followedAt = 3),
        ),
        groups = listOf(
            FollowGroup("g2", "space", listOf("1", "2"), emoji = "🚀"),
            FollowGroup("g1", "After dark", listOf("2"), nsfw = true),
            FollowGroup("g3", "Agencies", listOf("1"), pinned = true),
            FollowGroup("g4", "Gone", listOf("9")),
        ),
        includeReplies = false,
        autoplay = false,
    )

    @Test
    fun aSessionReadsBackAsItWasWritten() {
        assertEquals(session, Session.decode(Session.encode(session)))
    }

    @Test
    fun theFeedReadsOnlyTheAccountsXtaShowsInIt() {
        assertEquals(listOf("nasa"), session.feedMembers.map { it.screenName })
    }

    @Test
    fun aGroupReadsItsMembersEvenThoseKeptOutOfTheFollowingFeed() {
        assertEquals(listOf("nasa", "esa"), session.membersOf("g2").map { it.screenName })
        assertEquals(emptyList(), session.membersOf("nope"))
        assertEquals(listOf("nasa"), session.membersOf(null).map { it.screenName })
    }

    @Test
    fun groupsComeInXtasOrderAndOnlyWithSomeoneToRead() {
        assertEquals(listOf("Agencies", "space", "After dark"), session.feedGroups.map { it.name })
    }

    @Test
    fun followingHereAddsTheAccountToTheFollowingFeed() {
        val followed = session.follow(XUser("7", "jaxa", "JAXA", "https://pbs.twimg.com/j_normal.jpg"), at = 9)
        assertEquals(FollowedAccount("7", "jaxa", "JAXA", "https://pbs.twimg.com/j_normal.jpg", verified = false, inFeed = true, followedAt = 9), followed.followed("7"))
        assertEquals(listOf("nasa", "jaxa"), followed.feedMembers.map { it.screenName })
    }

    @Test
    fun followingAgainKeepsThePlaceAndSwitchesButTakesTheNewName() {
        val again = session.follow(XUser("2", "esa", "European Space Agency"), at = 99)
        assertEquals(FollowedAccount("2", "esa", "European Space Agency", null, verified = false, inFeed = false, followedAt = 3), again.followed("2"))
        assertEquals(listOf("1", "2"), again.following.map { it.id })
    }

    @Test
    fun unfollowingTakesTheAccountOutOfEveryGroup() {
        val gone = session.unfollow("1")
        assertNull(gone.followed("1"))
        assertEquals(listOf("2"), gone.groups.first { it.id == "g2" }.memberIds)
        assertEquals(emptyList(), gone.groups.first { it.id == "g3" }.memberIds)
        assertEquals(listOf("space", "After dark"), gone.feedGroups.map { it.name }, "a group with nobody left to read has no tab")
    }

    @Test
    fun anAccountCanLeaveTheFollowingFeedAndStayInItsGroups() {
        val out = session.withInFeed("1", false)
        assertEquals(emptyList(), out.feedMembers)
        assertEquals(listOf("nasa", "esa"), out.membersOf("g2").map { it.screenName })
    }

    @Test
    fun groupsTakeMembersInAndOutOnce() {
        val joined = session.withMembership("g1", "1", member = true).withMembership("g1", "1", member = true)
        assertEquals(listOf("2", "1"), joined.groups.first { it.id == "g1" }.memberIds)
        val left = joined.withMembership("g2", "2", member = false)
        assertEquals(listOf("1"), left.groups.first { it.id == "g2" }.memberIds)
        val made = left.withNewGroup("g5", "  Moon ", listOf("1"))
        assertEquals(FollowGroup("g5", "Moon", listOf("1")), made.groups.last())
        assertEquals(made, Session.decode(Session.encode(made)))
    }

    @Test
    fun importingReplacesSessionsAndFollowsButKeepsTheSwitches() {
        val imported = session.importing(XtaBackup(emptyList(), session.following.take(1), emptyList()))
        assertEquals(emptyList(), imported.accounts)
        assertEquals(1, imported.following.size)
        assertEquals(false, imported.includeReplies)
    }

    @Test
    fun nothingToReadIsNoSession() {
        assertNull(Session.decode(""))
        assertNull(Session.decode("""{"v": 7}"""))
    }
}
