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
import com.aimdi.xtanative.ui.settings.NetworkSections
import com.aimdi.xtanative.ui.settings.NetworkSettingsPlace
import com.aimdi.xtanative.ui.settings.PluginIds

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
 * on switches its network on and pins it on Home's strip, and installs its
 * plugin from the catalogue when it is not installed, as XTA's built-in
 * networks are there already; turned off, it comes off the strip and is
 * switched off, keeping what it stored (and its plugin).
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

    /** Whether turning [network]'s tile in [settings] installs its plugin: turned on, and not [installed]. */
    fun installs(settings: Settings, network: NetworkPrefs, installed: Boolean): Boolean = !selected(settings, network) && !installed

    /** [settings] with [network]'s tile back off, as its plugin could not be installed; as they are when it is off already. */
    fun withdrawn(settings: Settings, network: NetworkPrefs): Settings =
        if (selected(settings, network)) toggled(settings, network) else settings

    /** A chosen network that shows nothing until it is set up: Mastodon without an instance, RSS without a feed. */
    fun needsSetup(settings: Settings, network: NetworkPrefs): Boolean = when (network) {
        MastodonPrefs -> settings[MastodonPrefs.instance].isBlank()
        RssPrefs -> J.parse(settings[RssPrefs.feeds]).list.isEmpty()
        else -> false
    }

    /**
     * The chosen networks still to be set up, each with a "Set up" button
     * under the tiles; not one whose settings would show nothing, as a
     * plugin's that is not installed ([plugins]).
     */
    fun toSetUp(settings: Settings, plugins: PluginIds = PluginIds()): List<NetworkPrefs> = networks.filter {
        selected(settings, it) && needsSetup(settings, it) && NetworkSections.place(it.id, plugins) != NetworkSettingsPlace.NONE
    }
}

/** The measures of the intro's drawings (intro_phone_illustration.dart, xta_mark.dart), in the units they are drawn in. */
object IntroDrawing {
    /** The launcher mark's ellipses as shares of its square: half their long and short axes, and the stroke. */
    const val MARK_SEMI_MAJOR = 0.608
    const val MARK_SEMI_MINOR = 0.229
    const val MARK_STROKE = 0.089

    /** The phone's module: an avatar's size, which every other measure follows, as a share of its width. */
    const val PHONE_UNIT = 0.135

    /**
     * The placeholder posts that fit between the phone's notch and its
     * bottom edge (IntroPhone.rowsFor): a row is its two bars (1.25 units)
     * and a one-unit gap; never fewer than two or more than five.
     */
    fun phoneRows(width: Double, height: Double): Int {
        val unit = width * PHONE_UNIT
        val screen = height - unit * 3.45 - 4
        return ((screen + unit) / (2.25 * unit)).toInt().coerceIn(2, 5)
    }

    /**
     * A point on the centre line of one of the mark's ellipses, from the
     * mark's centre ([xtaMarkOrbitPoint]): [angle] (radians) picks the
     * ellipse, [t] from 0 to 1 walks round it; [mark] is the mark's size.
     */
    fun orbitPoint(mark: Double, angle: Double, t: Double): Pair<Double, Double> {
        val theta = 2 * Math.PI * t
        val x = MARK_SEMI_MAJOR * mark * kotlin.math.cos(theta)
        val y = MARK_SEMI_MINOR * mark * kotlin.math.sin(theta)
        return (x * kotlin.math.cos(angle) - y * kotlin.math.sin(angle)) to (x * kotlin.math.sin(angle) + y * kotlin.math.cos(angle))
    }
}
