package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CashtagLinksTest {
    @Test
    fun aCashtagsLinkNamesItsSymbol() {
        assertEquals("\$aapl", CashtagLinks.tag("aapl"))
        assertEquals("aapl", CashtagLinks.symbolOf(CashtagLinks.tag("aapl")))
        assertEquals("BRK.B", CashtagLinks.symbolOf("\$BRK.B"))
    }

    @Test
    fun anyOtherLinkIsNoCashtag() {
        assertNull(CashtagLinks.symbolOf("@someone"), "a mention")
        assertNull(CashtagLinks.symbolOf("#kotlin"), "a hashtag")
        assertNull(CashtagLinks.symbolOf("https://example.com/\$x"), "an address")
        assertNull(CashtagLinks.symbolOf("$"))
        assertNull(CashtagLinks.symbolOf(null))
    }

    @Test
    fun theSheetNamesTheCashtagUpperCasedAsXtasDoes() {
        assertEquals("\$AAPL", CashtagLinks.title("aapl"))
    }
}
