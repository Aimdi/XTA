package com.aimdi.xtanative.ui.trends

import com.aimdi.xtanative.ui.search.SEARCH_SCOPE_X
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoverScopesTest {
    @Test
    fun withNoPluginThatSearchesDiscoverHasNoChips() {
        assertEquals(emptyList(), DiscoverScopes.chips(emptyList()))
        assertEquals(listOf(SEARCH_SCOPE_X, "bluesky", "reddit"), DiscoverScopes.chips(listOf("bluesky", "reddit")), "X first, then each plugin")
    }

    @Test
    fun aPluginsChipHoldsWhileThePluginSearches() {
        assertEquals("bluesky", DiscoverScopes.effective("bluesky", listOf("bluesky")))
        assertEquals(SEARCH_SCOPE_X, DiscoverScopes.effective("bluesky", emptyList()), "uninstalled, the bar searches X again")
        assertEquals(SEARCH_SCOPE_X, DiscoverScopes.effective(SEARCH_SCOPE_X, emptyList()))
    }

    @Test
    fun theBodyIsXsTrendsOrThePluginsSearch() {
        assertEquals(DiscoverBody.X_TRENDS, DiscoverScopes.body(SEARCH_SCOPE_X, "anything"))
        assertEquals(DiscoverBody.PLUGIN_EMPTY, DiscoverScopes.body("bluesky", "  "))
        assertEquals(DiscoverBody.PLUGIN_SEARCH, DiscoverScopes.body("bluesky", "skeets"))
        assertTrue(DiscoverScopes.opensXSearch(SEARCH_SCOPE_X))
        assertFalse(DiscoverScopes.opensXSearch("bluesky"), "a plugin's results show under the bar")
    }
}
