package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginRouteTest {
    @Test
    fun aRouteSurvivesBeingWrittenAndRead() {
        val route = PluginRoute("thread", "url" to "https://example.social/@a/1?x=1&y=2", "acct" to "a@example.social", "missing" to null)
        assertEquals("thread?url=https%3A%2F%2Fexample.social%2F%40a%2F1%3Fx%3D1%26y%3D2&acct=a%40example.social", route.toString())
        assertEquals(route, PluginRoute.parse(route.toString()))
        assertEquals("https://example.social/@a/1?x=1&y=2", PluginRoute.parse(route.toString())["url"])
    }

    @Test
    fun aBareNameHasNoArguments() {
        assertEquals(PluginRoute("saved"), PluginRoute.parse("saved"))
        assertEquals("saved", PluginRoute("saved").toString())
    }

    @Test
    fun blankOrBrokenArgumentsReadAsNone() {
        val route = PluginRoute.parse("profile?acct=&x&bad=%E0%A4%A")
        assertNull(route["acct"])
        assertNull(route["x"])
        assertNull(route["bad"])
        assertEquals("profile", route.name)
    }
}
