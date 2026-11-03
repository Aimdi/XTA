package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.ui.plugins.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginNavigationTest {
    @Test
    fun theShelfKeepsTheLastMediaOpened() {
        val shelf = MediaShelf()
        val keys = (1..20).map { shelf.put(listOf(MediaItem("https://example.com/$it.jpg"))) }
        assertEquals("https://example.com/20.jpg", shelf.get(keys.last()).single().imageUrl)
        assertTrue(shelf.get(keys.first()).isEmpty(), "the oldest went")
        assertTrue(shelf.get("never").isEmpty())
    }
}
