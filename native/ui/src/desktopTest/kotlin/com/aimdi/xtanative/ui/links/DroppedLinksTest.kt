package com.aimdi.xtanative.ui.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DroppedLinksTest {
    @Test
    fun aDroppedWebAddressIsTaken() {
        assertEquals("https://x.com/flutter/status/1", DroppedLinks.linkIn("https://x.com/flutter/status/1"))
        assertEquals("https://x.com/a", DroppedLinks.linkIn("\n  https://x.com/a  \nsecond line"))
        assertEquals("HTTP://example.org/x", DroppedLinks.linkIn("HTTP://example.org/x"))
    }

    @Test
    fun otherTextIsLeftAlone() {
        assertNull(DroppedLinks.linkIn(null))
        assertNull(DroppedLinks.linkIn(""))
        assertNull(DroppedLinks.linkIn("look at https://x.com/a"))
        assertNull(DroppedLinks.linkIn("file:///home/me/a.jpg"))
        assertNull(DroppedLinks.linkIn("javascript:alert(1)"))
        assertNull(DroppedLinks.linkIn("https://"))
    }
}
