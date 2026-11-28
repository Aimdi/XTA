package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** The wide window's column beside every screen: which of its rows is the screen shown. */
class WideFrameTest {
    private val home = "home"
    private val defaultBar = setOf(HomeBar.FEED, HomeBar.SUBSCRIPTIONS, HomeBar.TRENDING, HomeBar.SAVED)

    private fun mark(route: String?, bar: Set<String> = defaultBar, homePage: String? = HomeBar.FEED, group: String? = null) =
        FrameMarks.of(route, home, homePage, group, bar)

    @Test
    fun onHomeTheBarsPageShownIsMarked() {
        assertEquals(FrameMark(page = HomeBar.FEED), mark(home))
        assertEquals(FrameMark(page = HomeBar.SAVED), mark(home, homePage = HomeBar.SAVED))
    }

    @Test
    fun aMenuScreenIsMarkedWithItsArgumentsLeftOut() {
        assertEquals(FrameMark(screen = "search"), mark("search?q={q}&tab={tab}&focus={focus}"))
        assertEquals(FrameMark(screen = "settings"), mark("settings"))
        assertEquals(FrameMark(screen = "downloads"), mark("downloads"))
        assertEquals(FrameMark(screen = "deck"), mark("deck"))
        assertEquals(FrameMark(screen = "plugins"), mark("plugins"))
    }

    @Test
    fun theScreensUnderAnEntryMarkThatEntry() {
        assertEquals(FrameMark(screen = "settings"), mark("settings/{section}?focus={focus}"))
        assertEquals(FrameMark(screen = "settings"), mark("speech/settings"))
        assertEquals(FrameMark(screen = "settings"), mark("licences/library?module={module}"))
        assertEquals(FrameMark(screen = "antennas"), mark("antenna/{id}"))
    }

    @Test
    fun aBarPageOpenedAsAScreenMarksItsPageWhereTheBarHasIt() {
        assertEquals(FrameMark(page = HomeBar.SAVED), mark("saved/folders"))
        assertEquals(FrameMark(screen = "saved"), mark("saved", bar = setOf(HomeBar.FEED)), "off the bar it is a row of the menu")
        assertEquals(FrameMark(page = HomeBar.TRENDING), mark("trends"))
    }

    @Test
    fun aGroupsScreenMarksTheGroupOrItsPage() {
        assertEquals(FrameMark(group = "g1"), mark("group/{id}", group = "g1"))
        val onBar = defaultBar + BarPage.Group("g1").id
        assertEquals(FrameMark(page = BarPage.Group("g1").id), mark("group/{id}", bar = onBar, group = "g1"))
    }

    @Test
    fun otherScreensMarkNothing() {
        assertEquals(FrameMark(), mark("post/{id}?media={media}"))
        assertEquals(FrameMark(), mark("profile/{screenName}?id={id}&tab={tab}"))
        assertEquals(FrameMark(), mark("community/{id}"))
        assertEquals(FrameMark(), mark("plugin/{pluginId}"), "a plugin's own pages are not the store")
        assertEquals(FrameMark(), mark("searchlike"), "a name that only starts like an entry's")
        assertEquals(FrameMark(), mark(null))
    }

    @Test
    fun aScreenWithNoRowLeavesTheNearestBelowItMarked() {
        val fromHome = listOf(FrameRoute("root"), FrameRoute(home), FrameRoute("post/{id}?media={media}"))
        assertEquals(FrameMark(page = HomeBar.FEED), FrameMarks.ofStack(fromHome, home, HomeBar.FEED, defaultBar))
        val fromSearch = listOf(FrameRoute(home), FrameRoute("search?q={q}&tab={tab}&focus={focus}"), FrameRoute("profile/{screenName}?id={id}&tab={tab}"))
        assertEquals(FrameMark(screen = "search"), FrameMarks.ofStack(fromSearch, home, HomeBar.FEED, defaultBar))
        val group = listOf(FrameRoute(home), FrameRoute("group/{id}", group = "g1"), FrameRoute("post/{id}?media={media}"))
        assertEquals(FrameMark(group = "g1"), FrameMarks.ofStack(group, home, HomeBar.FEED, defaultBar))
        assertEquals(FrameMark(), FrameMarks.ofStack(listOf(FrameRoute("intro")), home, null, defaultBar))
    }

    @Test
    fun aPluginsPageMarksItsOwnRowElseNothingButHomeUnderIt() {
        val pixivPage = BarPage.Network("pixiv").id
        val fromStore = listOf(FrameRoute(home), FrameRoute("plugins"), FrameRoute("plugin/{pluginId}", plugin = "pixiv"))
        assertEquals(FrameMark(), FrameMarks.ofStack(fromStore, home, HomeBar.FEED, defaultBar), "not the store it was opened from")
        assertEquals(FrameMark(page = pixivPage), FrameMarks.ofStack(fromStore, home, HomeBar.FEED, defaultBar + pixivPage))
        val link = listOf(FrameRoute(home), FrameRoute("search?q={q}&tab={tab}&focus={focus}"), FrameRoute("plugin/{pluginId}/screen?route={route}", plugin = "pixiv"))
        assertEquals(FrameMark(), FrameMarks.ofStack(link, home, HomeBar.FEED, defaultBar), "a link found by the search is not Search")
        val post = link + FrameRoute("post/{id}?media={media}")
        assertEquals(FrameMark(page = pixivPage), FrameMarks.ofStack(post, home, HomeBar.FEED, defaultBar + pixivPage), "what it opens leaves its row marked")
        val fromHome = listOf(FrameRoute(home), FrameRoute("plugin/{pluginId}/card?route={route}", plugin = "pixiv"))
        assertEquals(FrameMark(page = HomeBar.FEED), FrameMarks.ofStack(fromHome, home, HomeBar.FEED, defaultBar), "opened from Home's pinned column")
        val settings = listOf(FrameRoute(home), FrameRoute("plugins"), FrameRoute("plugin/{pluginId}/settings"))
        assertEquals(FrameMark(screen = "plugins"), FrameMarks.ofStack(settings, home, HomeBar.FEED, defaultBar), "its settings stay the store's")
    }

    @Test
    fun onlyTheScreensTheColumnOpenedAreItsRootsForAsLongAsTheyAreInTheNavigation() {
        val roots = FrameRoots()
        assertEquals(emptySet(), roots.among(listOf("home", "search")), "Search from a hashtag keeps its Back")
        roots.open("settings")
        assertEquals(setOf("settings"), roots.among(listOf("home", "settings", "settings-page")), "a page pushed over it is no root")
        roots.keep(listOf("home", "settings"))
        assertEquals(setOf("settings"), roots.among(listOf("home", "settings")), "going out, still shown: still a root")
        roots.keep(listOf("home"))
        assertEquals(emptySet(), roots.among(listOf("home", "settings")), "gone, forgotten")
    }

    @Test
    fun aScreenOverTheWholeWindowGrowsOverTheNavigationOnlyWhereThereIsOne() {
        assertEquals(0, fullWindowGrowth(null), "no frame: in the screens' own place")
        val frame = WideFrame()
        assertEquals(HomeWide.FRAME_INSET, fullWindowGrowth(frame))
        frame.inset = HomeWide.inset(FrameKind.RAIL)
        assertEquals(HomeWide.RAIL_WIDTH + 1, fullWindowGrowth(frame), "over the rail, as the window narrows")
    }
}
