package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowHereTest {
    private val maya = XUser("1", "maya", "Maya Rivers")
    private val jo = FollowedAccount("2", "jo", "Jo", null, false, inFeed = false, followedAt = 5)
    private val session = Session(
        following = listOf(jo),
        groups = listOf(
            FollowGroup("g-news", "News", listOf("2")),
            FollowGroup("g-art", "Art", emptyList(), emoji = "🎨", pinned = true),
            FollowGroup("g-cats", "Cats", listOf("2")),
        ),
    )

    @Test
    fun anAccountIsFollowedHereAndNoLonger() {
        val account = FollowTarget.Account(maya)
        assertEquals(Followed(), account.state(session))
        val followed = account.toggled(session, at = 9)
        assertEquals(Followed(followed = true, inFeed = true), account.state(followed))
        assertEquals(9, followed.followed("1")?.followedAt)
        assertEquals(Followed(), account.state(account.toggled(followed, at = 10)))
    }

    @Test
    fun theFeedSwitchKeepsAnAccountFollowed() {
        val account = FollowTarget.Account(XUser("2", "jo", "Jo"))
        assertEquals(Followed(followed = true, inFeed = false), account.state(session))
        assertEquals(Followed(followed = true, inFeed = true), account.state(account.feedToggled(session)))
        assertTrue(account.hasFeedSwitch)
    }

    @Test
    fun aSearchIsFollowedAsXtaKeepsOne() {
        val search = FollowTarget.Search("  cats lang:de ")
        assertEquals("cats lang:de", search.memberId)
        assertFalse(search.hasFeedSwitch)
        val followed = search.toggled(session, at = 3)
        assertEquals(Followed(followed = true, inFeed = true), search.state(followed))
        assertEquals(Followed(), search.state(search.toggled(followed, at = 4)))
    }

    @Test
    fun followingAgainKeepsAnAccountsPlaceAndSwitches() {
        val again = FollowTarget.Account(XUser("2", "jo", "Jo B")).followed(session, at = 99)
        assertEquals(5, again.followed("2")?.followedAt)
        assertFalse(again.followed("2")?.inFeed ?: true)
        assertEquals(listOf("g-news", "g-cats"), again.groups.filter { "2" in it.memberIds }.map { it.id })
    }
}
