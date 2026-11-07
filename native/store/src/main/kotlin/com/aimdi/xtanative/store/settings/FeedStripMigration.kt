package com.aimdi.xtanative.store.settings

import com.aimdi.xtanative.core.feed.FeedStrip

/**
 * XTA's one-time move of Home's timelines (migrateFeedStripPins), for a backup
 * restored from an install that never hand-picked them. A first launch of this
 * app starts empty and is left alone: native never adds a network to Home on
 * its own. An install that already marked the strip as hand-picked is left as
 * it is.
 */
object FeedStripMigration {
    fun of(settings: Settings): Settings {
        if (settings.isSet(HomePrefs.feedStripHandPicked) && settings[HomePrefs.feedStripHandPicked]) return settings
        val onHome = XtaPrefs.networks.filter { it.showTab != null && settings[it.enabled] }
        val enabled = onHome.map { it.id }
        val hidden = onHome.filter { settings[it.showTab!!] == false }.map { it.id }
        val saved = settings[HomePrefs.feedStripNetworks].takeIf { settings.isSet(HomePrefs.feedStripNetworks) }
        val pins = FeedStrip.migrated(saved, firstLaunch = false, enabled = enabled, hiddenTab = hidden)
        val written = if (saved == pins) settings else settings.with(HomePrefs.feedStripNetworks, pins)
        return written.with(HomePrefs.feedStripHandPicked, true)
    }
}
