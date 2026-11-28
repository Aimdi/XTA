package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.DeckColumnOptions
import com.aimdi.xtanative.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeckStateTest {
    private val session = Session(
        groups = listOf(FollowGroup("g1", "Art", emptyList())),
        deck = listOf(DeckColumn.Following.id, "g1"),
        deckOptions = mapOf("g1" to DeckColumnOptions(mediaOnly = true)),
    )

    @Test
    fun theDeckIsItsColumnsAndTheirChoices() {
        val deck = DeckState.of(session)
        assertEquals(listOf(DeckColumn.Following, DeckColumn.Group("g1")), deck.columns)
        assertEquals(mapOf("g1" to DeckColumnOptions(mediaOnly = true)), deck.options)
    }

    @Test
    fun noSessionYetIsAnEmptyDeck() {
        assertEquals(DeckState(emptyList(), emptyMap()), DeckState.of(null))
    }

    @Test
    fun aWriteToTheRestOfTheSessionLeavesTheDeckAsItWas() {
        val after = session.copy(includeReplies = false, offFollowing = setOf("g1"))
        assertEquals(DeckState.of(session), DeckState.of(after))
    }

    @Test
    fun aColumnMovedOrAChoiceChangedIsANewDeck() {
        assertNotEquals(DeckState.of(session), DeckState.of(session.copy(deck = session.deck.reversed())))
        assertNotEquals(DeckState.of(session), DeckState.of(session.withDeckOptions("g1", DeckColumnOptions())))
    }

    @Test
    fun aColumnsPlaceIsTheSameWhileItStandsWhereItWas() {
        assertEquals(ColumnPlace(first = true, last = false), ColumnPlace(first = true, last = false))
    }
}
