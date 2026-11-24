package com.aimdi.xtanative.ui.post

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkHoversTest {
    private val frame = IntSize(1600, 900)
    private val pill = IntSize(300, 30)

    @Test
    fun anAddressWaitsForThePointerToRest() {
        assertEquals(LinkHovers.Step(LinkHovers.SHOW_AFTER_MS, "https://a"), LinkHovers.step("https://a", null, null))
    }

    @Test
    fun theNextLinkShowsAtOnceWhileOneIsUp() {
        assertEquals(LinkHovers.Step(0, "https://b"), LinkHovers.step("https://b", "https://a", null))
    }

    @Test
    fun aLinkJustLeftLetsTheNextShowAtOnce() {
        assertEquals(0, LinkHovers.showDelay(showing = false, sinceHiddenMs = 100))
        assertEquals(LinkHovers.SHOW_AFTER_MS, LinkHovers.showDelay(showing = false, sinceHiddenMs = LinkHovers.QUICK_AGAIN_MS))
        assertEquals(LinkHovers.SHOW_AFTER_MS, LinkHovers.showDelay(showing = false, sinceHiddenMs = -5))
    }

    @Test
    fun leavingHidesAfterAMomentAndTheSameLinkChangesNothing() {
        assertEquals(LinkHovers.Step(LinkHovers.HIDE_AFTER_MS, null), LinkHovers.step(null, "https://a", null))
        assertNull(LinkHovers.step("https://a", "https://a", null))
        assertNull(LinkHovers.step(null, null, null))
    }

    @Test
    fun thePillSitsBottomLeftUnlessThePointerIsThere() {
        assertEquals(LinkHovers.Corner.LEFT, LinkHovers.corner(null, frame, pill, 6f))
        assertEquals(LinkHovers.Corner.LEFT, LinkHovers.corner(Offset(100f, 400f), frame, pill, 6f))
        assertEquals(LinkHovers.Corner.LEFT, LinkHovers.corner(Offset(900f, 890f), frame, pill, 6f))
        assertEquals(LinkHovers.Corner.RIGHT, LinkHovers.corner(Offset(100f, 880f), frame, pill, 6f))
        assertEquals(LinkHovers.Corner.RIGHT, LinkHovers.corner(Offset(311f, 858f), frame, pill, 6f))
        assertEquals(LinkHovers.Corner.LEFT, LinkHovers.corner(Offset(313f, 880f), frame, pill, 6f))
    }

    @Test
    fun otherScriptsReadAsThemselves() {
        assertEquals("https://x.com/hashtag/ä%20b", LinkHovers.readable("https://x.com/hashtag/%C3%A4%20b"))
        assertEquals("https://x.com/hashtag/日本", LinkHovers.readable("https://x.com/hashtag/%E6%97%A5%E6%9C%AC"))
        assertEquals("https://x.com/search?q=%24AAPL", LinkHovers.readable("https://x.com/search?q=%24AAPL"))
        assertEquals("https://x.com/quax_tests/status/1", LinkHovers.readable("https://x.com/quax_tests/status/1"))
    }

    @Test
    fun aHostInOtherScriptsReadsAsPunycodeNotAsTheHostItMimics() {
        // A Cyrillic "а" (U+0430) in place of the Latin one: never shown as "аpple.com".
        assertEquals("https://xn--pple-43d.com/а", LinkHovers.readable("https://%D0%B0pple.com/%D0%B0"))
        assertEquals("https://xn--pple-43d.com:8080?q=1", LinkHovers.readable("https://%D0%B0pple.com:8080?q=1"))
        assertEquals("https://me@xn--pple-43d.com#ä", LinkHovers.readable("https://me@%D0%B0pple.com#%C3%A4"))
        assertEquals("https://xn--mnchen-3ya.de", LinkHovers.readable("https://m%C3%BCnchen.de"))
        assertEquals("https://x.com/", LinkHovers.readable("https://x.com/"))
        // No name a browser would go to: left escaped as written.
        assertEquals("https://%E2%80%AE.com/", LinkHovers.readable("https://%E2%80%AE.com/"))
        assertEquals("https://%C3x.com", LinkHovers.readable("https://%C3x.com"))
    }

    @Test
    fun whatCouldDisguiseAnAddressStaysEscaped() {
        // Right-to-left override, no-break space, a broken sequence, a lone trail byte, a dangling %.
        assertEquals("https://a/%E2%80%AE", LinkHovers.readable("https://a/%E2%80%AE"))
        assertEquals("https://a/%C2%A0", LinkHovers.readable("https://a/%C2%A0"))
        assertEquals("https://a/%C3x", LinkHovers.readable("https://a/%C3x"))
        assertEquals("https://a/%A4ä", LinkHovers.readable("https://a/%A4%C3%A4"))
        assertEquals("https://a/%", LinkHovers.readable("https://a/%"))
        assertEquals("https://a/%E", LinkHovers.readable("https://a/%E"))
    }

    @Test
    fun thePointerIsFollowedOnlyWhileALinkHasIt() {
        val hover = LinkHover { 0 }
        hover.moved(Offset(1f, 1f))
        assertNull(hover.pointer)
        val first = Any()
        val second = Any()
        hover.over(first, "https://a")
        hover.over(second, "https://b")
        hover.left(first)
        hover.moved(Offset(5f, 5f))
        assertEquals(Offset(5f, 5f), hover.pointer)
    }
}
