package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkMenusTest {
    @Test
    fun aLinkMentionOrTagHasAMenu() {
        assertTrue(LinkMenus.opensFor("https://example.com/a"))
        assertTrue(LinkMenus.opensFor("@maya"))
        assertTrue(LinkMenus.opensFor("#kotlin"))
        assertTrue(LinkMenus.opensFor("\$TSLA"))
    }

    @Test
    fun aContractAddressOrNothingKeepsThePostMenu() {
        assertFalse(LinkMenus.opensFor("contract:0xabc"), "a contract address is copied by a click, it is no page")
        assertFalse(LinkMenus.opensFor(null))
        assertFalse(LinkMenus.opensFor("@"))
    }
}
