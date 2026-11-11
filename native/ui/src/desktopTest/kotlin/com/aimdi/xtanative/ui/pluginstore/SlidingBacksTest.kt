package com.aimdi.xtanative.ui.pluginstore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A plugin's back while one of its cards is on top slides that card out. */
class SlidingBacksTest {
    @Test
    fun theCardOnTopGoesBackByItsOwnSlide() {
        val backs = SlidingBacks()
        var slid = 0
        backs.put("card") { slid++ }
        backs.of("card")?.invoke()
        assertEquals(1, slid)
    }

    @Test
    fun aScreenThatIsNoCardHasNoSlide() {
        val backs = SlidingBacks()
        backs.put("card") {}
        assertNull(backs.of("profile"))
        assertNull(backs.of(null))
    }

    @Test
    fun aCardThatLeftIsForgottenUnlessANewerBackTookItsPlace() {
        val backs = SlidingBacks()
        val first = { }
        val second = { check(true) }
        backs.put("card", first)
        backs.put("card", second)
        backs.remove("card", first)
        assertSame(second, backs.of("card"))
        backs.remove("card", second)
        assertNull(backs.of("card"))
    }

    @Test
    fun aCardsRouteCarriesThePluginAndItsScreen() {
        assertEquals("plugin/threads/card?route=post%3Furl%3Da%20b", PluginRoutes.card("threads", "post?url=a b"))
    }
}
