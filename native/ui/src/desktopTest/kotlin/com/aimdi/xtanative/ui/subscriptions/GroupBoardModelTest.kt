package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.GroupsOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupBoardModelTest {
    private fun account(id: String, screenName: String) = FollowedAccount(id, screenName, screenName.uppercase(), "https://pbs/$id.jpg", false, true, 0)

    private val session = Session(
        following = listOf(account("1", "zoe"), account("2", "Anna"), account("3", "bob"), account("4", "carl"), account("5", "dan")),
        groups = listOf(
            FollowGroup("tech", "tech", listOf("1", "2", "3", "4", "5", "list:9"), createdAt = 30, position = 2),
            FollowGroup("ai", "AI", listOf("3"), createdAt = 10, position = 0),
            FollowGroup("art", "Art", listOf("2"), nsfw = true, createdAt = 20, position = 1),
            FollowGroup("news", "News", emptyList(), pinned = true, createdAt = 40),
        ),
        nesting = mapOf("ai" to "tech"),
    )

    private fun ids(groups: List<FollowGroup>) = groups.map { it.id }

    @Test
    fun groupsComeNsfwLastPinnedFirstThenInTheChosenOrder() {
        assertEquals(listOf("news", "ai", "tech", "art"), ids(GroupBoardModel.ordered(session.groups, GroupsOrder.NAME, true)))
        assertEquals(listOf("news", "tech", "ai", "art"), ids(GroupBoardModel.ordered(session.groups, GroupsOrder.NAME, false)))
        assertEquals(listOf("news", "ai", "tech", "art"), ids(GroupBoardModel.ordered(session.groups, GroupsOrder.CREATED_AT, true)))
        assertEquals(listOf("news", "ai", "tech", "art"), ids(GroupBoardModel.ordered(session.groups, GroupsOrder.POSITION, true)), "no place yet comes first")
        assertEquals(listOf("news", "tech", "ai", "art"), ids(GroupBoardModel.ordered(session.groups, GroupsOrder.POSITION, false)))
    }

    @Test
    fun aNestedGroupSitsUnderItsParentUnlessTheReaderIsSearching() {
        val tiles = GroupBoardModel.tiles(session, GroupsOrder.NAME, true, "")
        assertEquals(listOf("news", "tech", "ai", "art"), tiles.map { it.id })
        assertEquals(listOf(0, 0, 1, 0), tiles.map { it.depth })
        val found = GroupBoardModel.tiles(session, GroupsOrder.NAME, true, " A")
        assertEquals(listOf("ai", "art"), found.map { it.id }, "found wherever it lives")
    }

    @Test
    fun aTileCountsEveryMemberAndShowsFourFacesByHandle() {
        val tech = GroupBoardModel.tiles(session, GroupsOrder.NAME, true, "").single { it.id == "tech" }
        assertEquals(6, tech.memberCount, "a saved search is a member too")
        assertEquals(listOf("2", "3", "4", "5"), tech.faces.map { it.id }, "by handle, whatever its case")
        assertEquals("ANNA", tech.faces.first().name)
        assertTrue(GroupBoardModel.tiles(session, GroupsOrder.NAME, true, "", unread = setOf("ai")).single { it.id == "ai" }.unread)
    }

    @Test
    fun aPluginsAccountsShowTheirFacesAfterTheXAccountsInTheGroupsOrder() {
        val mixed = session.copy(groups = listOf(FollowGroup("mixed", "Mixed", listOf("bluesky:did:plc:maya", "3", "threads:aria", "threads:gone"))))
        val plugins = listOf(
            SubscriptionRow("threads:aria", "Aria", "aria", "https://t/a.jpg", plugin = "threads"),
            SubscriptionRow("bluesky:did:plc:maya", "", "maya.bsky.social", plugin = "bluesky"),
        )
        val tile = GroupBoardModel.tiles(mixed, GroupsOrder.NAME, true, "", plugins = plugins).single()
        assertEquals(listOf("3", "bluesky:did:plc:maya", "threads:aria"), tile.faces.map { it.id }, "a plugin not running shows none")
        assertEquals("maya.bsky.social", tile.faces[1].name, "named by its handle when it has no name")
        assertEquals(4, tile.memberCount)
    }

    @Test
    fun nsfwGroupsGetASectionOfTheirOwn() {
        val sections = GroupBoardModel.sections(GroupBoardModel.tiles(session, GroupsOrder.NAME, true, ""))
        assertEquals(listOf("news", "tech", "ai"), sections.safe.map { it.id })
        assertEquals(listOf("art"), sections.nsfw.map { it.id })
        assertFalse(sections.isEmpty)
    }

    @Test
    fun onlyTheReadersOwnOrderCanBeDraggedAndNeverPastCensored() {
        assertTrue(GroupBoardModel.canReorder(GroupsOrder.POSITION, ""))
        assertFalse(GroupBoardModel.canReorder(GroupsOrder.POSITION, "a"))
        assertFalse(GroupBoardModel.canReorder(GroupsOrder.NAME, ""))
        val sections = GroupBoardModel.sections(GroupBoardModel.tiles(session, GroupsOrder.POSITION, true, ""))
        // Rows: news, tech, ai, [Censored], art.
        assertEquals(listOf("tech", "ai", "news", "art"), GroupBoardModel.reordered(sections, 0, 4), "a safe group stops above the header")
        assertEquals(listOf("news", "tech", "ai", "art"), GroupBoardModel.reordered(sections, 4, 0), "an NSFW group stays under it")
        assertNull(GroupBoardModel.reordered(sections, 3, 0), "the header itself does not move")
    }

    @Test
    fun theReadersOrderIsKeptAsEachGroupsPlace() {
        val placed = GroupBoardModel.withPositions(session, listOf("news", "art", "tech"))
        assertEquals(mapOf("tech" to 2, "ai" to 0, "art" to 1, "news" to 0), placed.groups.associate { it.id to it.position })
    }
}
