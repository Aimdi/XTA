package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrawnPostsTest {
    private data class Post(val id: String, val text: String = "")

    @Test
    fun aPostIsFoundAgainByItsKeyAsItWasDrawnLast() {
        val drawn = DrawnPosts<Post>(capacity = 3) { it.id }
        drawn.put(Post("a", "first"))
        drawn.put(Post("a", "again"))
        assertEquals("again", drawn["a"]?.text)
        assertNull(drawn["b"])
    }

    @Test
    fun theOldestGoFirstOnceItIsFullAndDrawingAPostAgainKeepsIt() {
        val drawn = DrawnPosts<Post>(capacity = 2) { it.id }
        drawn.put(Post("a"))
        drawn.put(Post("b"))
        drawn.put(Post("a"))
        drawn.put(Post("c"))
        assertNull(drawn["b"], "b was drawn longest ago")
        assertEquals(Post("a"), drawn["a"])
        assertEquals(Post("c"), drawn.put(Post("c")))
    }
}
