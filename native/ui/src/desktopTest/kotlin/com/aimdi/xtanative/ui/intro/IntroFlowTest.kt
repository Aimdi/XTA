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
}
