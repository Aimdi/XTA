package com.aimdi.xtanative.ui.pluginstore

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
    }
}
