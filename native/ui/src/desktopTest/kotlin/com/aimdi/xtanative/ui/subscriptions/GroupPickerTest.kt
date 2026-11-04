package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupPickerTest {
    private val groups = (1..8).map { FollowGroup("g$it", "Group $it", emptyList()) } + FollowGroup("art", "Art", emptyList())

    private fun ids(groups: List<FollowGroup>) = groups.map { it.id }

    @Test
    fun theSheetOpensOnTheAccountsGroupsThenTheRecentThenTheRest() {
        val order = GroupPicker.openOrder(groups, selected = listOf("g5"), recent = listOf("art", "gone", "g5"))
        assertEquals(listOf("g5", "art", "g1", "g2", "g3", "g4", "g6", "g7", "g8"), order)
        val made = groups + FollowGroup("new", "New", emptyList())
        assertEquals(listOf("new", "g5", "art"), ids(GroupPicker.ordered(made, order, listOf("new"))).take(3))
    }

    @Test
    fun theFirstSixShowUntilAllAndASearchShowsWhatMatches() {
        val ordered = GroupPicker.ordered(groups, GroupPicker.openOrder(groups, emptyList(), emptyList()), emptyList())
        val first = GroupPicker.chips(ordered, PickView())
        assertEquals(6, first.groups.size)
        assertTrue(first.hasMore)
        assertEquals(PickSection.RECENT, first.section)
        val all = GroupPicker.chips(ordered, PickView().showingAll())
        assertEquals(9, all.groups.size)
        assertEquals(PickSection.ALL, all.section)
        assertFalse(all.hasMore)
        assertEquals(8, GroupPicker.shownFor(List(8) { "g$it" }))
    }

    @Test
    fun aSearchOffersToMakeTheGroupItDoesNotFind() {
        val chips = GroupPicker.chips(groups, PickView().searching(" ART "))
        assertEquals(listOf("art"), ids(chips.groups))
        assertEquals(PickSection.MATCHES, chips.section)
        assertFalse(chips.canCreate, "a group is called that already")
        val none = GroupPicker.chips(groups, PickView().searching("Japan"))
        assertEquals(PickSection.NO_MATCH, none.section)
        assertTrue(none.canCreate)
        val made = PickView(query = "Japan").created("new")
        assertEquals("", made.query)
        assertEquals(listOf("new"), made.createdIds)
    }

    @Test
    fun theLineUnderTheChipsSaysWhereTheAccountIs() {
        assertEquals(PickStatus.None, GroupPicker.status(groups, emptySet()))
        assertEquals(PickStatus.One("Art"), GroupPicker.status(groups, setOf("art")))
        assertEquals(PickStatus.Two("Group 1", "Art"), GroupPicker.status(groups, setOf("art", "g1")), "in the sheet's order")
        assertEquals(PickStatus.Many(3), GroupPicker.status(groups, setOf("art", "g1", "g2")))
    }

    @Test
    fun closingUntouchedChangesNothingAndWhatWasAddedIsRememberedFirst() {
        assertNull(GroupPicker.answer(setOf("g1"), setOf("g1")))
        assertEquals(setOf("g2"), GroupPicker.answer(setOf("g1"), setOf("g2")))
        assertEquals(setOf("g1", "g2"), GroupPicker.toggled(setOf("g1"), "g2"))
        assertEquals(setOf("g1"), GroupPicker.toggled(setOf("g1", "g2"), "g2"))
        assertEquals("g3,g2,g1", GroupPicker.remembered("g2,g1", initial = setOf("g1"), chosen = setOf("g1", "g3")))
        assertEquals("g2,g1", GroupPicker.remembered("g2,g1", initial = setOf("g1"), chosen = emptySet()))
        assertEquals(listOf("a", "b"), GroupPicker.recent("a,,b"))
    }
}
