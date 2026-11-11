package com.aimdi.xtanative.ui.pluginstore

import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreRowsTest {
    @Test
    fun theStoresFirstLookFollowsNothing() {
        assertNull(StoreRows.newlyInstalled(null, listOf("bluesky")))
    }

    @Test
    fun aPluginThatJoinsInstalledIsFollowed() {
        assertEquals("bluesky", StoreRows.newlyInstalled(emptyList(), listOf("bluesky")))
        assertEquals("bluesky", StoreRows.newlyInstalled(listOf("reddit"), listOf("bluesky", "reddit")))
    }

    @Test
    fun anUninstallOrNoChangeIsNotFollowed() {
        assertNull(StoreRows.newlyInstalled(listOf("bluesky", "reddit"), listOf("reddit")))
        assertNull(StoreRows.newlyInstalled(listOf("reddit"), listOf("reddit")))
    }

    @Test
    fun theFirstInstalledBringsInstalledsHeaderToTheTop() {
        assertEquals(0, StoreRows.afterInstall(0, refreshing = false, unreachable = false))
    }

    @Test
    fun aLaterOneBringsTheRowAboveItWithTheNoticesCounted() {
        // 0 refresh line, 1 unreachable notice, 2 Installed, 3 and 4 installed rows, 5 the new one.
        assertEquals(4, StoreRows.afterInstall(2, refreshing = true, unreachable = true))
        assertEquals(3, StoreRows.afterInstall(2, refreshing = false, unreachable = true))
        assertEquals(1, StoreRows.afterInstall(0, refreshing = false, unreachable = false, searchField = true), "the search field above it all")
    }

    @Test
    fun theStoreListsPluginsInXtasOwnOrderWhateverTheCataloguesIs() {
        val catalogue = listOf("bluesky", "booru", "deepmarks", "ehviewer", "hackernews", "immich", "instagram", "karakeep", "mastodon", "pixiv", "reddit", "rss", "stocks", "substack", "threads", "tiktok", "websources")
        assertEquals(StoreRows.REGISTRY, StoreRows.ordered(catalogue) { it })
        assertEquals(listOf("threads", "rss", "immich"), StoreRows.ordered(listOf("immich", "rss", "threads")) { it })
    }

    @Test
    fun aPluginXtaNeverHadComesAfterItsOwnInTheOrderGiven() {
        assertEquals(listOf("reddit", "zeta", "alpha"), StoreRows.ordered(listOf("zeta", "alpha", "reddit")) { it })
    }

    @Test
    fun aRowStacksItsButtonsInANarrowWindowOrWithLargeText() {
        assertFalse(StoreRows.stacks(420f, 1f))
        assertTrue(StoreRows.stacks(419f, 1f), "under 420 dp the buttons go under the name")
        assertTrue(StoreRows.stacks(720f, 1.3f), "from 1.3 times the text size on, too")
        assertFalse(StoreRows.stacks(720f, 1.29f))
    }
}
