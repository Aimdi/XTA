package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.DeckColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckAddsTest {
    private val names = ColumnNames(
        groups = listOf(FollowGroup("g1", "Art", emptyList()), FollowGroup("g2", "News", emptyList())),
        available = listOf(StripNetwork(FeedStrip.FOLLOWING, "Following"), StripNetwork("reddit", "Reddit"), StripNetwork("rss", "RSS")),
        antennas = mapOf("a1" to "Cats"),
        folders = emptyMap(),
        searches = listOf("kotlin"),
    )

    @Test
    fun aTimelineOfHomeIsTheDecksColumnOfIt() {
        assertEquals(DeckColumn.Following, deckColumnOfStrip(FeedStrip.FOLLOWING))
        assertEquals(DeckColumn.ForYou, deckColumnOfStrip(FeedStrip.FOR_YOU))
        assertEquals(DeckColumn.Network("reddit"), deckColumnOfStrip("reddit"))
        assertEquals(DeckColumn.Group("g1"), deckColumnOfTab(HomeTab.Group("g1", "Art")))
        assertEquals(DeckColumn.Network("rss"), deckColumnOfTab(HomeTab.Network("rss")))
        assertEquals(DeckColumn.Group("g2"), deckColumnOfPage(BarPage.Group("g2")))
        assertEquals(DeckColumn.Network("reddit"), deckColumnOfPage(BarPage.Network("reddit")))
        assertNull(deckColumnOfPage(BarPage.Saved), "Home's own pages are no column")
        assertNull(deckColumnOfPage(BarPage.Feed))
    }

    @Test
    fun aDropLandsBeforeTheColumnWhoseMiddleIsPastIt() {
        val spans = listOf(Triple("a", 100f, 400f), Triple("b", 400f, 700f))
        assertEquals("a", DeckDrops.before(120f, spans))
        assertEquals("b", DeckDrops.before(300f, spans), "past a's middle")
        assertEquals("b", DeckDrops.before(500f, spans))
        assertNull(DeckDrops.before(600f, spans), "past the last column's middle: last")
        assertNull(DeckDrops.before(10f, emptyList()), "an empty deck")
        assertEquals(400f, DeckDrops.lineAt("b", spans), "the line stands at the left edge of the column it goes before")
        assertEquals(700f, DeckDrops.lineAt(null, spans), "or at the last one's right edge")
        assertNull(DeckDrops.lineAt(null, emptyList()))
    }

    @Test
    fun addColumnListsEveryTimelineInSectionsTheColumnsThereMarked() {
        val entries = AddColumnList.of(listOf(DeckColumn.Network("reddit"), DeckColumn.Group("g2")), names)
        val columns = entries.mapNotNull { (it.choice as? AddChoice.Column)?.column }
        assertEquals(
            listOf(
                DeckColumn.Following, DeckColumn.ForYou, DeckColumn.Network("reddit"), DeckColumn.Network("rss"),
                DeckColumn.Group("g1"), DeckColumn.Group("g2"), DeckColumn.Search("kotlin"), DeckColumn.Antenna("a1"),
            ),
            columns,
            "Following is Home's own, not a network's, and stands once",
        )
        assertEquals(setOf("@network:reddit", "g2"), entries.filter { it.added }.mapTo(HashSet()) { it.key })
        assertEquals(listOf(AddSection.TIMELINES, AddSection.NETWORKS, AddSection.GROUPS, AddSection.MORE), entries.map { it.section }.distinct())
        assertEquals(DeckAsk.entries.size, entries.count { it.choice is AddChoice.Ask }, "a search and an account to name stay last")
        assertTrue(entries.takeLast(DeckAsk.entries.size).all { it.choice is AddChoice.Ask })
    }

    @Test
    fun typingNarrowsALongListToTheNamesHoldingIt() {
        val entries = AddColumnList.of(emptyList(), names)
        val labels = mapOf("@network:reddit" to "Reddit", "@network:rss" to "RSS", "g1" to "🎨 Art", "g2" to "News")
        val label = { entry: AddEntry -> labels[entry.key] ?: entry.key }
        val shown = AddColumnList.matching(entries, " re ", label)
        assertEquals(listOf("@network:reddit"), shown.filter { it.choice is AddChoice.Column }.map { it.key }, "in any case, the spaces round it left out")
        assertEquals(DeckAsk.entries.size, shown.count { it.choice is AddChoice.Ask }, "the ways to name a column stay")
        assertEquals("@network:reddit", AddColumnList.first(shown)?.key, "Enter adds the first column left")
        assertEquals(entries, AddColumnList.matching(entries, "  ", label), "nothing typed: every row")
        assertNull(AddColumnList.first(AddColumnList.matching(entries, "zzz", label)), "nothing left to add")
    }

    @Test
    fun theFieldComesWithALongList() {
        assertFalse(AddColumnList.filters(AddColumnList.of(emptyList(), names).take(AddColumnList.FILTER_FROM - 1)))
        assertTrue(AddColumnList.filters(AddColumnList.of(emptyList(), names).let { it + it }.take(AddColumnList.FILTER_FROM)))
    }
}
