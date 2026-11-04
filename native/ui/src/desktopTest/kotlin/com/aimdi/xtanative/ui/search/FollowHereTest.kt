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
    fun thePickerListsGroupsInXtasOrderWithTheirMembers() {
        val picks = GroupPicker.picks(session, "2")
        assertEquals(listOf("g-art", "g-cats", "g-news"), picks.map { it.id })
        assertEquals(listOf(false, true, true), picks.map { it.member })
        assertEquals("🎨 Art", picks.first().label)
        assertEquals(Placed.Two("Cats", "News"), GroupPicker.placed(picks))
        assertEquals(Placed.None, GroupPicker.placed(GroupPicker.picks(session, "1")))
    }

    @Test
    fun typingFiltersTheGroupsAndOffersANewOne() {
        val picks = GroupPicker.picks(session, "2")
        assertEquals(listOf("g-cats"), GroupPicker.matching(picks, " cAt").map { it.id })
        assertEquals(picks, GroupPicker.matching(picks, ""))
        assertTrue(GroupPicker.canCreate(picks, "Dogs"))
        assertFalse(GroupPicker.canCreate(picks, " news "))
        assertFalse(GroupPicker.canCreate(picks, "  "))
    }

    @Test
    fun puttingInAGroupFollowsFirst() {
        val account = FollowTarget.Account(maya)
        val inArt = GroupPicker.inGroup(session, account, "g-art", member = true, at = 7)
        assertEquals(Followed(followed = true, inFeed = true), account.state(inArt))
        assertEquals(listOf("1"), inArt.groups.first { it.id == "g-art" }.memberIds)

        val out = GroupPicker.inGroup(inArt, account, "g-art", member = false, at = 8)
        assertEquals(emptyList(), out.groups.first { it.id == "g-art" }.memberIds)
        assertTrue(account.state(out).followed)
    }

    @Test
    fun aNewGroupHoldsTheSearchFollowed() {
        val search = FollowTarget.Search("cats")
        val created = GroupPicker.created(session, search, "g-new", " Felines ", at = 1)
        assertEquals("Felines", created.groups.last().name)
        assertEquals(listOf("cats"), created.groups.last().memberIds)
        assertTrue(search.state(created).followed)
        val everywhere = created.copy(groups = created.groups.map { it.copy(memberIds = listOf("cats")) })
        assertEquals(Placed.Many(4), GroupPicker.placed(GroupPicker.picks(everywhere, "cats")))
        assertEquals(Placed.One("Felines"), GroupPicker.placed(GroupPicker.picks(created, "cats")))
    }
}
