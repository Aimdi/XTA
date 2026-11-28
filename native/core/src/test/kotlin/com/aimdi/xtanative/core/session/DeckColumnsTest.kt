package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckColumnsTest {
    @Test
    fun aColumnIsAGroupByItsIdOrOneOfHomesTimelines() {
        assertEquals(DeckColumn.Group("g1"), DeckColumn.of("g1"))
        assertEquals(DeckColumn.Following, DeckColumn.of("@following"))
        assertEquals(DeckColumn.ForYou, DeckColumn.of("@foryou"))
        assertEquals(DeckColumn.Network("reddit"), DeckColumn.of("@network:reddit"))
        assertNull(DeckColumn.of("@network:"), "a network with no id")
        assertNull(DeckColumn.of("@later"), "a timeline this build does not know")
        listOf(DeckColumn.Following, DeckColumn.ForYou, DeckColumn.Network("bluesky"), DeckColumn.Group("g2")).forEach {
            assertEquals(it, DeckColumn.of(it.id), "${it.id} reads back as it was written")
        }
        assertTrue(DeckColumn.isTimeline("@foryou"))
        assertFalse(DeckColumn.isTimeline("g1"), "a group's id is kept only while the group is")
    }

    @Test
    fun theDeckShowsItsTimelinesAndTheGroupsStillThere() {
        val session = Session(groups = listOf(FollowGroup("g1", "News", emptyList())), deck = listOf("@following", "gone", "g1", "@network:reddit"))
        assertEquals(listOf(DeckColumn.Following, DeckColumn.Group("g1"), DeckColumn.Network("reddit")), session.deckColumns)
    }

    @Test
    fun columnsMoveWithinTheEndsAndAreAddedOnce() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), DeckColumns.moved(ids, 1, -1))
        assertEquals(listOf("a", "c", "b"), DeckColumns.moved(ids, 1, 1))
        assertEquals(ids, DeckColumns.moved(ids, 0, -1), "the first stays first")
        assertEquals(ids, DeckColumns.moved(ids, 2, 1), "the last stays last")
        assertEquals(ids, DeckColumns.moved(ids, 7, 1), "no such column")
        assertEquals(listOf("a", "@foryou"), DeckColumns.added(listOf("a"), DeckColumn.ForYou))
        assertEquals(listOf("@foryou"), DeckColumns.added(listOf("@foryou"), DeckColumn.ForYou))
    }

    @Test
    fun columnsFillTheWindowWhileTheyFitAndScrollPastIt() {
        assertEquals(500f, DeckColumns.width(1500f, 3, 380f), "three fit: each takes a third")
        assertEquals(380f, DeckColumns.width(1500f, 5, 380f), "five do not: each keeps its width, the deck scrolls")
    }

    @Test
    fun aColumnCanBeASearchAnAccountAnAntennaOrASavedFolder() {
        val search = DeckColumn.Search("kotlin, compose lang:en")
        assertFalse(',' in search.id, "a search's comma never splits the deck's list")
        assertEquals(search, DeckColumn.of(search.id))
        listOf(DeckColumn.Profile("jack"), DeckColumn.Antenna("a1"), DeckColumn.SavedFolder("f1")).forEach {
            assertEquals(it, DeckColumn.of(it.id), "${it.id} reads back as it was written")
            assertTrue(DeckColumn.isTimeline(it.id), "a restore keeps it, as it keeps Home's timelines")
        }
        assertNull(DeckColumn.of("@search:"))
        assertNull(DeckColumn.of("@search:%zz"), "a broken search reads as no column")
        assertNull(DeckColumn.of("@profile:"))
        val session = Session(deck = listOf(search.id, "@profile:jack", "@antenna:a1", "@saved:f1"))
        assertEquals(4, session.deckColumns.size, "kept whatever the device holds; a column whose antenna is gone says so itself")
    }

    @Test
    fun theReaderNamesASearchOrAnAccount() {
        assertEquals(DeckColumn.Search("cats"), DeckAsks.search("  cats "))
        assertNull(DeckAsks.search("   "))
        assertEquals(DeckColumn.Profile("jack"), DeckAsks.profile("@jack"))
        assertEquals(DeckColumn.Profile("jack"), DeckAsks.profile("https://x.com/jack"))
        assertNull(DeckAsks.profile("not a handle!"))
        assertNull(DeckAsks.profile("@a @b"), "one account a column")
    }
}
