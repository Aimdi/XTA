package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiddleClickLinksTest {
    @Test
    fun aLinkInAPostIsItsPageOnTheWeb() {
        assertEquals("https://example.org/a?b=1", MiddleClickLinks.webAddressOf("https://example.org/a?b=1"))
        assertEquals("https://x.com/quax_tests", MiddleClickLinks.webAddressOf("@quax_tests"))
        assertEquals("https://x.com/hashtag/FlutterDev", MiddleClickLinks.webAddressOf("#FlutterDev"))
        assertEquals("https://x.com/hashtag/%C3%A4%20b", MiddleClickLinks.webAddressOf("#ä b"))
        assertEquals("https://x.com/search?q=%24TSLA", MiddleClickLinks.webAddressOf("\$TSLA"))
    }

    @Test
    fun whatIsNoPageIsLeftAlone() {
        assertNull(MiddleClickLinks.webAddressOf("contract:0xabc"))
        assertNull(MiddleClickLinks.webAddressOf("@"))
        assertNull(MiddleClickLinks.webAddressOf(null))
        assertNull(MiddleClickLinks.webAddressOf("javascript:alert(1)"))
    }
}
