package com.aimdi.xtanative.ui

import com.aimdi.xtanative.services.links.XLink
import com.aimdi.xtanative.services.links.XLinks
import com.aimdi.xtanative.services.links.XProfileTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowRoutesTest {
    /** As the app's link rules read an address: X's own pages only. */
    private val xScreens: (String) -> XLink? = { url ->
        XLinks.parse(url).takeIf { url.startsWith("https://x.com/") && it !is XLink.Unknown && it !is XLink.Live }
    }

    @Test
    fun mentionsAndTagsOpenTheirScreens() {
        assertEquals("profile/maya", WindowRoutes.of("@maya", xScreens))
        assertEquals("search?q=%23kotlin", WindowRoutes.of("#kotlin", xScreens))
        assertEquals("search?q=%24TSLA", WindowRoutes.of("\$TSLA", xScreens))
    }

    @Test
    fun anXAddressOpensItsScreenAndAWebPageNone() {
        assertEquals("post/123", WindowRoutes.of("https://x.com/maya/status/123", xScreens))
        assertEquals("profile/maya", WindowRoutes.of("https://x.com/maya", xScreens))
        assertNull(WindowRoutes.of("https://example.com/page", xScreens), "a page on the web stays the browser's")
        assertNull(WindowRoutes.of("contract:0xabc", xScreens), "a contract address is copied, not opened")
        assertNull(WindowRoutes.of("@", xScreens))
        assertNull(WindowRoutes.of("", xScreens))
    }

    @Test
    fun xScreensByKind() {
        assertEquals("community/7", WindowRoutes.of(XLink.Community("7")))
        assertEquals("community/7/about", WindowRoutes.of(XLink.Community("7", about = true)))
        assertEquals("list/9", WindowRoutes.of(XLink.XList("9")))
        assertEquals("profile/maya?tab=media", WindowRoutes.of(XLink.Profile("maya", XProfileTab.MEDIA)))
        assertNull(WindowRoutes.of(XLink.Live("https://x.com/i/spaces/1", isSpace = true)))
    }

    @Test
    fun opensSaysWhetherALinkOpenedInAWindow() {
        val opened = mutableListOf<String>()
        val opens = WindowRoutes.opens(opened::add, xScreens)
        assertNull(opens.link("https://example.com"), "a page on the web has no window")
        assertTrue(opened.isEmpty(), "asking opens nothing")
        assertNotNull(opens.link("@maya")).invoke()
        opens.post("5")
        opens.profile("maya", "42")
        opens.community("8")
        assertEquals(listOf("profile/maya", "post/5", "profile/maya?id=42", "community/8"), opened)
    }
}
