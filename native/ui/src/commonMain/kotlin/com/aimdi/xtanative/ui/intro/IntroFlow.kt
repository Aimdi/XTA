package com.aimdi.xtanative.ui.intro

import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.MastodonPrefs
import com.aimdi.xtanative.store.settings.NetworkPrefs
import com.aimdi.xtanative.store.settings.RssPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.XtaPrefs

/** XTA's first-launch cards (intro_screen.dart), in order. */
enum class IntroPage {
    WELCOME,
    DEVICE,
    APPEARANCE,
    ACCOUNT,
    SOURCES,
    ;

    val next: IntroPage? get() = entries.getOrNull(ordinal + 1)

    /** Back steps to the card before; on the first there is none, and Back asks to close the app, as Home does. */
    val previous: IntroPage? get() = entries.getOrNull(ordinal - 1)
}

/** The intro's rules: when it shows, and what it marks once it is done. */
object IntroFlow {
    /** The cards show until they were seen; SettingsMigrations marks an install that ran before as having seen them. */
    fun shows(settings: Settings): Boolean = !settings[GeneralPrefs.introSeen]

    /**
     * Whether the app opens on the cards. An install that already holds an X
     * session ran before, even one from before the settings could remember
     * the cards, so it opens where it always did.
     */
    fun opensOn(settings: Settings, hasAccounts: Boolean): Boolean = shows(settings) && !hasAccounts

    /** Finishing, or skipping, marks the cards seen. */
    fun finished(settings: Settings): Settings = settings.with(GeneralPrefs.introSeen, true)

    /** Settings › About › Show intro again. */
    fun again(settings: Settings): Settings = settings.with(GeneralPrefs.introSeen, false)
}

/**
 * The sources card (intro_sources_page.dart): the networks that can sit on
 * Home and that the store lists openly, in XTA's registry order. A tile turned
 * on switches its network on and pins it on Home's strip; turned off, it
 * comes off the strip and is switched off, keeping what it stored.
 */
object IntroSources {
    /** The networks the store keeps behind "Show private plugins". */
    val PRIVATE = setOf("tiktok", "instagram", "pixiv", "ehviewer")

    val networks: List<NetworkPrefs> = XtaPrefs.networks.filter { it.showTab != null && it.id !in PRIVATE }

    fun selected(settings: Settings, network: NetworkPrefs): Boolean =
        network.id in settings[HomePrefs.feedStripNetworks] && settings[network.enabled]

    fun toggled(settings: Settings, network: NetworkPrefs): Settings {
        val pins = settings[HomePrefs.feedStripNetworks]
        return if (selected(settings, network)) {
            settings.with(HomePrefs.feedStripNetworks, FeedStrip.forgotten(pins, network.id)).with(network.enabled, false)
        } else {
            settings.with(network.enabled, true).with(HomePrefs.feedStripNetworks, FeedStrip.pinned(pins, network.id))
        }
    }

    /** A chosen network that shows nothing until it is set up: Mastodon without an instance, RSS without a feed. */
    fun needsSetup(settings: Settings, network: NetworkPrefs): Boolean = when (network) {
        MastodonPrefs -> settings[MastodonPrefs.instance].isBlank()
        RssPrefs -> J.parse(settings[RssPrefs.feeds]).list.isEmpty()
        else -> false
    }

    /** The chosen networks still to be set up, each with a "Set up" button under the tiles. */
    fun toSetUp(settings: Settings): List<NetworkPrefs> = networks.filter { selected(settings, it) && needsSetup(settings, it) }
}
