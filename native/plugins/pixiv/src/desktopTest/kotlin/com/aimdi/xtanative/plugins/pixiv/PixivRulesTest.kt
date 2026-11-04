package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.store.settings.PixivDetailLayout
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixivRulesTest {
    private val table = PixivRankingPins.tableOf(novels = false)

    @Test
    fun pinsDropUnknownBoardsAndFallBackToTheDefaults() {
        val settings = Settings().with(PixivPrefs.rankingModes, listOf("week", "nonsense", "week"))
        assertEquals(listOf("week"), PixivRankingPins.of(settings, novels = false))
        assertEquals(PixivPrefs.rankingModes.default, PixivRankingPins.of(Settings().with(PixivPrefs.rankingModes, listOf("nonsense")), novels = false))
    }

    @Test
    fun theChipsFollowTheTableAndAlwaysShowOne() {
        val pins = listOf("month", "day")
        assertEquals(listOf("day", "month"), PixivRankingPins.visible(pins, table, showR18 = false).map { it.id })
        assertEquals(listOf(table.first().id), PixivRankingPins.visible(emptyList(), table, showR18 = false).map { it.id })
    }

    @Test
    fun aBoardWhoseChipIsGoneGivesWayToTheFirst() {
        val visible = PixivRankingPins.visible(listOf("week"), table, showR18 = false)
        assertEquals("week", PixivRankingPins.effective("day", visible))
        assertEquals("week", PixivRankingPins.effective("week", visible))
    }

    @Test
    fun pinningKeepsTheTablesOrderAndTheLastChipStays() {
        assertEquals(listOf("day", "week"), PixivRankingPins.toggled(listOf("week"), "day", table))
        assertEquals(listOf("week"), PixivRankingPins.toggled(listOf("day", "week"), "day", table))
        assertTrue(PixivRankingPins.lastVisible(listOf("day"), "day", table))
        assertFalse(PixivRankingPins.lastVisible(listOf("day", "week"), "day", table))
    }

    @Test
    fun aPickedColumnCountYieldsOnlyToANarrowPane() {
        assertEquals(3, PixivLayout.columns(400f, 3, largeText = false))
        assertEquals(2, PixivLayout.columns(200f, 4, largeText = false))
        assertEquals(2, PixivLayout.columns(320f, 0, largeText = false))
        assertEquals(4, PixivLayout.columns(2000f, 0, largeText = false))
        assertEquals(1, PixivLayout.columns(320f, 0, largeText = true))
    }

    @Test
    fun aWorkSplitsOnlyWideOrWhenAsked() {
        assertTrue(PixivLayout.splits(900f, PixivDetailLayout.AUTO))
        assertFalse(PixivLayout.splits(700f, PixivDetailLayout.AUTO))
        assertTrue(PixivLayout.splits(700f, PixivDetailLayout.SPLIT))
        assertFalse(PixivLayout.splits(2000f, PixivDetailLayout.VERTICAL))
    }
}
