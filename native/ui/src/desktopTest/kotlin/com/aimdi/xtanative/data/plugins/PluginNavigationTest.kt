package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.ui.plugins.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginNavigationTest {
    @Test
    fun theShelfKeepsTheLastMediaOpenedWithThePluginThatOpenedThem() {
        val shelf = MediaShelf()
        val keys = (1..20).map { shelf.put("bluesky", listOf(MediaItem("https://example.com/$it.jpg"))) }
        val last = shelf.get(keys.last())
        assertEquals("https://example.com/20.jpg", last?.items?.single()?.imageUrl)
        assertEquals("bluesky", last?.source)
        assertNull(shelf.get(keys.first()), "the oldest went")
        assertNull(shelf.get("never"))
    }

    @Test
    fun aSavedFileTakesTheNameThePluginGaveElseThePlugins() {
        assertEquals("alice", MediaShelf.source("reddit", "alice"))
        assertEquals("reddit", MediaShelf.source("reddit", null))
        assertEquals("reddit", MediaShelf.source("reddit", "  "))
    }
}
