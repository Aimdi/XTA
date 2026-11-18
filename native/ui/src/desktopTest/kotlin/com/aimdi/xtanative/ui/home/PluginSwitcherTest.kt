package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginSwitcherTest {
    private val tabs = listOf(HomeTab.Following, HomeTab.Network("reddit"), HomeTab.Network("substack"), HomeTab.Network("bluesky"), HomeTab.Network("hackernews"))

    @Test
    fun theRecentlyUsedComeFirstThenHomesOrder() {
        assertEquals(listOf("bluesky", "reddit", "substack", "hackernews"), PluginSwitcher.networks(tabs, listOf("bluesky", "reddit")))
    }

    @Test
    fun onlyNetworksOnHomeAreOffered() {
        assertEquals(
            listOf("reddit", "substack", "bluesky", "hackernews"),
            PluginSwitcher.networks(tabs, listOf("mastodon")),
            "a network used once but taken off Home is not in the grid",
        )
        assertEquals(emptyList(), PluginSwitcher.networks(listOf(HomeTab.Following), listOf("reddit")))
    }
}
