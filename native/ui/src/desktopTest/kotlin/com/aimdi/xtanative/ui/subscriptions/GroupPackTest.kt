package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.GroupPack
import com.aimdi.xtanative.core.session.PackMember
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupPackTest {
    private val session = Session(
        following = listOf(
            FollowedAccount("1", "nasa", "NASA", null, false, true, 0),
            FollowedAccount("2", "esa", "ESA", null, false, true, 0),
        ),
        searches = listOf(SavedSearch("list:9", 0)),
    )

    @Test
    fun thePackIsWhatTheSheetHoldsSavedOrNotWithoutAnySignIn() {
        val draft = GroupDraft(name = "Space", members = setOf("2", "list:9", "gone"))
        val pack = GroupActions.packOf(session, draft)
        assertEquals(GroupPack("Space", listOf(PackMember(PackMember.USER, "2", "esa"), PackMember(PackMember.SEARCH, "list:9"))), pack)
        assertEquals(pack, GroupPack.decode(GroupPack.encode(pack)), "a pack reads back as it was written")
        assertEquals("xta-pack-42.json", GroupActions.packFileName(42))
    }

    @Test
    fun aMessageLeftForTheTabIsSaidOnceAndANewerOneStays() {
        val placed = GroupsMessage.Placed(3)
        GroupsMessenger.leave(placed)
        assertEquals(placed, GroupsMessenger.message.value)
        val newer = GroupsMessage.Placed(4)
        GroupsMessenger.leave(newer)
        GroupsMessenger.said(placed)
        assertEquals(newer, GroupsMessenger.message.value)
        GroupsMessenger.said(newer)
        assertNull(GroupsMessenger.message.value)
    }
}
