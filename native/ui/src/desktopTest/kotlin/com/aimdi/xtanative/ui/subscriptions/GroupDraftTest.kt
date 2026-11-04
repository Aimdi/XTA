package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupDraftTest {
    private fun account(id: String, name: String) = FollowedAccount(id, name.lowercase(), name, null, false, true, 0)

    private val session = Session(
        following = listOf(account("1", "Anna"), account("2", "Bob"), account("3", "Cleo")),
        searches = listOf(SavedSearch("list:9", 0)),
        groups = listOf(
            FollowGroup("g1", "Friends", listOf("2", "list:9"), emoji = "🙂", color = 0xFF00BA7C, markStyle = GroupMarkStyle.EMOJI, createdAt = 5, position = 1),
            FollowGroup("g2", "Work", emptyList()),
            FollowGroup("g3", "Inside", emptyList()),
        ),
        nesting = mapOf("g3" to "g1"),
    )

    @Test
    fun aGroupOpensAsItIsAndAnUnknownOneAsANewBlankGroup() {
        val draft = GroupEdits.draftOf(session, "g1")
        assertEquals(GroupDraft("g1", "Friends", 0xFF00BA7C, "🙂", null, GroupMarkStyle.EMOJI, setOf("2", "list:9")), draft)
        assertEquals(GroupDraft(), GroupEdits.draftOf(session, null))
        assertEquals(GroupDraft(), GroupEdits.draftOf(session, "gone"))
        assertFalse(GroupDraft(name = "  ").canSave)
    }

    @Test
    fun theMembersOpenFirstAndASearchFindsNameOrHandle() {
        val rows = SubscriptionList.rows(session)
        val candidates = GroupEdits.candidates(rows, setOf("2", "list:9"))
        assertEquals(listOf("2", "list:9", "1", "3"), candidates.map { it.id })
        assertEquals(listOf("3"), GroupEdits.visible(candidates, "CLE").map { it.id })
        assertEquals(listOf("1"), GroupEdits.visible(candidates, "anna").map { it.id })
    }

    @Test
    fun toggleAllPutsEveryoneInOrTakesEveryoneOut() {
        val empty = GroupDraft(name = "x")
        assertEquals(setOf("1", "2"), empty.toggledAll(listOf("1", "2")).members)
        assertEquals(emptySet(), empty.toggled("1", true).toggledAll(listOf("1", "2")).members)
        assertEquals(setOf("2"), empty.toggled("1", true).toggled("2", true).toggled("1", false).members)
    }

    @Test
    fun theMarkIsAnEmojiAnIconOrAutomatic() {
        val emoji = GroupDraft(name = "x").withEmoji(" 👍🏽 and more")
        assertEquals("👍🏽", emoji.emoji)
        assertEquals(GroupMarkStyle.EMOJI, emoji.markStyle)
        assertNull(GroupDraft(name = "x").withEmoji("  ").emoji)
        val icon = emoji.withIcon("star")
        assertEquals(GroupMarkKind.SYMBOL, icon.markKind)
        assertEquals("star", GroupLook.iconKey(icon.icon))
        val automatic = icon.automatic()
        assertNull(automatic.emoji)
        assertEquals(GroupMarkStyle.AUTO, automatic.shownStyle)
        assertEquals(GroupMarkStyle.AUTO, GroupDraft(markStyle = GroupMarkStyle.GENERATED).shownStyle)
    }

    @Test
    fun savingANewGroupMakesItWithItsMarkMembersAndDate() {
        val draft = GroupDraft(name = " Space ", color = 0xFF1D9BF0, members = setOf("3", "1"))
        val saved = GroupEdits.saved(session, draft, newId = "new", now = 42, order = listOf("1", "2", "3"))
        val group = saved.groups.single { it.id == "new" }
        assertEquals("Space", group.name)
        assertEquals(listOf("1", "3"), group.memberIds, "in the list's order")
        assertEquals(0xFF1D9BF0, group.color)
        assertEquals(42, group.createdAt)
    }

    @Test
    fun savingAGroupReplacesItsMembersAndKeepsItsPlaceAndDate() {
        val draft = GroupEdits.draftOf(session, "g1").toggled("1", true).toggled("list:9", false).copy(name = "Pals").automatic()
        val group = GroupEdits.saved(session, draft, newId = "unused", now = 99).groups.single { it.id == "g1" }
        assertEquals("Pals", group.name)
        assertEquals(setOf("2", "1"), group.memberIds.toSet())
        assertNull(group.emoji)
        assertEquals(GroupMarkStyle.AUTO, group.markStyle)
        assertEquals(5, group.createdAt)
        assertEquals(1, group.position)
        assertEquals(3, GroupEdits.saved(session, draft, "unused", 99).groups.size)
    }

    @Test
    fun aGroupMergesIntoAnyOtherButNestsNowhereItWouldContainItself() {
        assertEquals(listOf("g2", "g3"), GroupEdits.mergeTargets(session, "g1").map { it.id })
        assertEquals(listOf("g2"), GroupEdits.nestTargets(session, "g1").map { it.id }, "g3 sits inside g1")
        assertEquals(listOf("g1", "g2"), GroupEdits.nestTargets(session, "g3").map { it.id })
        val created = GroupEdits.created(session, "g9", " Fresh ", now = 3).groups.last()
        assertEquals(FollowGroup("g9", "Fresh", emptyList(), createdAt = 3), created)
        assertTrue(GroupDraft(name = "a").canSave)
    }
}
