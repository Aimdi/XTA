package com.aimdi.xtanative.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShellRowsTest {
    private fun tabs(vararg labels: String) = labels.map { PluginHomeTab(Icons.Filled.Home, it, false) {} }

    @Test
    fun feedsHandedKeepSectionsNotOnTheBarAboveThem() {
        val feeds = tabs("Home", "Popular")
        val rows = ShellRows.of(feeds, tabs("Posts", "Inbox"), onBar = false)
        assertEquals(listOf("Posts", "Inbox"), rows.sections?.map { it.label }, "two sections are not lost to the feeds")
        assertEquals(feeds, rows.feeds)
    }

    @Test
    fun sectionsOnTheBarOrAloneMakeNoRowOfTheirOwn() {
        val feeds = tabs("Home", "Popular")
        assertNull(ShellRows.of(feeds, tabs("A", "B", "C"), onBar = true).sections, "on the bottom bar")
        assertNull(ShellRows.of(feeds, tabs("A"), onBar = false).sections, "one section is no row")
    }

    @Test
    fun withoutFeedsTheSectionsAreTheFeedRow() {
        val rows = ShellRows.of(null, tabs("Top", "New"), onBar = false)
        assertNull(rows.sections)
        assertEquals(listOf("Top", "New"), rows.feeds?.map { it.label })
        assertNull(ShellRows.of(null, tabs("Top", "New"), onBar = true).feeds)
    }
}
