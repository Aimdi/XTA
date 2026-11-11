package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OwnSearchScreenTest {
    private open class FieldSearch : PluginSearch() {
        override fun hint(language: String): String = "Search"

        @Composable
        override fun Results(query: String, frame: PluginFrame) = Unit
    }

    @Test
    fun aSearchWithoutAScreenOfItsOwnKeepsTheAppsField() {
        assertNull(ownSearchScreen(FieldSearch(), "miku"))
    }

    @Test
    fun aSearchWithItsOwnScreenOpensItOnWhatWasAskedFor() {
        val search = object : FieldSearch() {
            override fun ownScreen(query: String?): String = "search?q=" + (query ?: "-")
        }
        assertEquals("search?q=miku", ownSearchScreen(search, "  miku "))
        assertEquals("search?q=-", ownSearchScreen(search, "  "))
        assertEquals("search?q=-", ownSearchScreen(search, null))
    }

    @Test
    fun aSearchThatFailsToNameItsScreenKeepsTheAppsField() {
        val failing = object : FieldSearch() {
            override fun ownScreen(query: String?): String = error("no screen")
        }
        val blank = object : FieldSearch() {
            override fun ownScreen(query: String?): String = " "
        }
        assertNull(ownSearchScreen(failing, "miku"))
        assertNull(ownSearchScreen(blank, "miku"))
    }
}
