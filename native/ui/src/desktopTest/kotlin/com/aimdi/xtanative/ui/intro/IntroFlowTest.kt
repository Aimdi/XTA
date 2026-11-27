package com.aimdi.xtanative.ui.intro

import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.MastodonPrefs
import com.aimdi.xtanative.store.settings.RssPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.settings.PluginIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntroFlowTest {
    @Test
    fun theCardsGoInXtasOrderAndBackStopsAtTheFirst() {
        assertEquals(IntroPage.DEVICE, IntroPage.WELCOME.next)
        assertNull(IntroPage.SOURCES.next)
        assertNull(IntroPage.WELCOME.previous)
        assertEquals(IntroPage.ACCOUNT, IntroPage.SOURCES.previous)
    }

    @Test
    fun theCardsShowUntilSeenAndCanBeShownAgain() {
        val first = Settings().with(GeneralPrefs.introSeen, false)
        assertTrue(IntroFlow.shows(first))
        assertFalse(IntroFlow.shows(IntroFlow.finished(first)))
        assertTrue(IntroFlow.shows(IntroFlow.again(IntroFlow.finished(first))))
        assertTrue(IntroFlow.opensOn(first, hasAccounts = false))
        // The prototype's installs had sessions before the settings existed: they open where they always did.
        assertFalse(IntroFlow.opensOn(first, hasAccounts = true))
    }

    @Test
    fun theSourcesAreTheOpenNetworksWithATimeline() {
        val ids = IntroSources.networks.map { it.id }
        assertEquals(listOf("threads", "bluesky", "mastodon", "reddit", "hackernews", "substack", "rss", "websources", "booru", "stocks"), ids)
    }

    @Test
    fun aTileSwitchesItsNetworkOnAndPinsItOnHome() {
        val on = IntroSources.toggled(Settings(), BlueskyPrefs)
        assertTrue(on[BlueskyPrefs.enabled])
        assertEquals(listOf("bluesky"), on[HomePrefs.feedStripNetworks])
        assertTrue(IntroSources.selected(on, BlueskyPrefs))
        val off = IntroSources.toggled(on, BlueskyPrefs)
        assertFalse(off[BlueskyPrefs.enabled])
        assertEquals(emptyList(), off[HomePrefs.feedStripNetworks])
    }

    @Test
    fun mastodonAndRssAskToBeSetUp() {
        val chosen = IntroSources.toggled(IntroSources.toggled(Settings(), MastodonPrefs), RssPrefs)
        assertEquals(listOf("mastodon", "rss"), IntroSources.toSetUp(chosen).map { it.id })
        val ready = chosen.with(MastodonPrefs.instance, "https://mastodon.social").with(RssPrefs.feeds, """[{"url":"https://e/f.xml"}]""")
        assertTrue(IntroSources.toSetUp(ready).isEmpty())
    }

    @Test
    fun aNetworkThatIsAPluginAsksToBeSetUpOnlyOnceInstalled() {
        val chosen = IntroSources.toggled(IntroSources.toggled(Settings(), MastodonPrefs), RssPrefs)
        val offered = PluginIds(listed = setOf("mastodon"))
        assertEquals(listOf("rss"), IntroSources.toSetUp(chosen, offered).map { it.id })
        val installed = offered.copy(installed = setOf("mastodon"))
        assertEquals(listOf("mastodon", "rss"), IntroSources.toSetUp(chosen, installed).map { it.id })
    }

    @Test
    fun turningOnANetworkNotInstalledInstallsItAndAFailedInstallTakesItOffAgain() {
        val off = Settings()
        assertTrue(IntroSources.installs(off, BlueskyPrefs, installed = false))
        assertFalse(IntroSources.installs(off, BlueskyPrefs, installed = true))
        val on = IntroSources.toggled(off, BlueskyPrefs)
        assertFalse(IntroSources.installs(on, BlueskyPrefs, installed = false))
        val withdrawn = IntroSources.withdrawn(on, BlueskyPrefs)
        assertFalse(IntroSources.selected(withdrawn, BlueskyPrefs))
        assertFalse("bluesky" in withdrawn[HomePrefs.feedStripNetworks])
        assertEquals(off, IntroSources.withdrawn(off, BlueskyPrefs))
    }

    @Test
    fun aPhoneHoldsTheRowsThatFitItsScreen() {
        assertEquals(5, IntroDrawing.phoneRows(100.0, 190.0))
        assertEquals(2, IntroDrawing.phoneRows(100.0, 100.0))
        assertEquals(2, IntroDrawing.phoneRows(100.0, 10.0))
    }

    @Test
    fun theOrbitWalksRoundTheMarksEllipses() {
        val (x, y) = IntroDrawing.orbitPoint(100.0, 0.0, 0.0)
        assertEquals(60.8, x, 1e-9)
        assertEquals(0.0, y, 1e-9)
        val (_, top) = IntroDrawing.orbitPoint(100.0, 0.0, 0.25)
        assertEquals(22.9, top, 1e-9)
    }

    @Test
    fun theArrowsTurnTheCardsTheWayTheyPoint() {
        assertEquals(IntroPage.DEVICE, IntroKeys.turned(IntroPage.WELCOME, towardsRight = true, rtl = false))
        assertEquals(IntroPage.WELCOME, IntroKeys.turned(IntroPage.DEVICE, towardsRight = false, rtl = false))
        assertNull(IntroKeys.turned(IntroPage.WELCOME, towardsRight = false, rtl = false))
        assertNull(IntroKeys.turned(IntroPage.SOURCES, towardsRight = true, rtl = false))
    }

    @Test
    fun rightToLeftTheLeftArrowGoesOn() {
        assertEquals(IntroPage.DEVICE, IntroKeys.turned(IntroPage.WELCOME, towardsRight = false, rtl = true))
        assertEquals(IntroPage.WELCOME, IntroKeys.turned(IntroPage.DEVICE, towardsRight = true, rtl = true))
    }

    @Test
    fun aWideWindowWidensTheColumnSoTheAccentsStandInOneRow() {
        assertEquals(IntroLayout.COLUMN, IntroLayout.columnWidth(600f))
        assertEquals(IntroLayout.WIDE_COLUMN, IntroLayout.columnWidth(1600f))
        // Seven 64 dp accents, six 4 dp gaps and the picker's 12 dp edges, in the column less its 24 dp margins and the card's 16 dp padding.
        assertTrue(7 * 64 + 6 * 4 + 2 * 12 <= IntroLayout.WIDE_COLUMN - 2 * 24 - 2 * 16)
    }

    @Test
    fun theDrawingGrowsWithATallWideWindowOnly() {
        assertEquals(IntroLayout.DRAWING, IntroLayout.drawingHeight(600f, 900f))
        assertEquals(IntroLayout.DRAWING, IntroLayout.drawingHeight(1600f, 600f))
        assertEquals(IntroLayout.TALL_DRAWING, IntroLayout.drawingHeight(1600f, 1000f))
        val between = IntroLayout.drawingHeight(1600f, 770f)
        assertTrue(between > IntroLayout.DRAWING && between < IntroLayout.TALL_DRAWING)
    }
}
