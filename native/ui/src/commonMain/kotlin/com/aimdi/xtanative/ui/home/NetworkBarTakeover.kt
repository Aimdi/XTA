package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab

/**
 * Which networks' own sections take over Home's bottom bar while they are
 * shown (the plugin bottom bars canvas): XTA's house first, which leads
 * back to the app's own bar, then the network's sections, so the top bar
 * keeps only the network's button and its actions.
 */
internal object NetworkBarRules {
    /** The networks whose sections move to the bottom bar. */
    val NETWORKS: Set<String> = setOf("reddit", "bluesky", "mastodon", "threads", "rss", "hackernews", "pixiv", "substack", "stocks")

    /**
     * The networks whose own menu is the bar's only one: Home leaves its menu
     * of the network's options out, since the plugin's has its search and
     * settings already and two menus side by side cannot be told apart.
     */
    val ONE_MENU: Set<String> = setOf("reddit")

    /** How many of a network's sections the bar shows beside More. */
    const val ON_BAR = 4

    /** Whether [network], handing [sections] sections to Home, takes over the bottom bar: one of [NETWORKS], with two or more. */
    fun takesOver(network: String?, sections: Int): Boolean = network in NETWORKS && sections > 1

    /**
     * A network's [sections] split between the bar and More: those that join
     * More ([PluginHomeTab.joinsMore]) go there; of the rest, up to [ON_BAR]
     * stay, and past that the first three stay and the others go to More,
     * before those that join it.
     */
    fun <T> split(sections: List<T>, joinsMore: (T) -> Boolean): Split<T> {
        val (own, joining) = sections.partition { !joinsMore(it) }
        if (own.size <= ON_BAR) return Split(own, joining)
        return Split(own.take(ON_BAR - 1), own.drop(ON_BAR - 1) + joining)
    }

    /** Who sets a page's takeover: Home's Feed, or a network's own page on the bar; null for a page that has none. */
    fun owner(page: BarPage): String? = when (page) {
        BarPage.Feed -> FEED
        is BarPage.Network -> "page:" + page.id
        else -> null
    }

    const val FEED = "feed"
}

/** A network's sections on the bar ([bar]) and in More ([more]). */
internal data class Split<T>(val bar: List<T>, val more: List<T>)

/** One network shown on a page of the bar: which ([network]) and what its plugin hands Home's bar ([client]). */
@Stable
internal class NetworkBarSections(val network: String, val client: PluginClientBar, val onHome: (() -> Unit)? = null) {
    /** The network's sections, as its plugin hands them now. */
    val sections: List<PluginHomeTab> get() = client.content?.tabs?.value.orEmpty()

    /** The plugin's own colour for the section shown, if it names one. */
    val accent: Color? get() = client.content?.accent?.value

    /** Whether the sections are on the bottom bar now. */
    val active: Boolean get() = NetworkBarRules.takesOver(network, sections.size)
}

/**
 * The networks Home's pages show, as its bottom bar needs them: Home's Feed
 * page and each network's own page set theirs while shown and clear them
 * when not, so the bar takes the sections of the page it is on.
 */
@Stable
internal class NetworkBarTakeover {
    private val shown = mutableStateMapOf<String, NetworkBarSections>()

    /**
     * [owner] shows [network] through [client], or nothing when either is
     * null. [onHome] is what the bar's house does there: null leaves it to
     * the bar, which turns to Home's own page.
     */
    fun show(owner: String, network: String?, client: PluginClientBar?, onHome: (() -> Unit)? = null) {
        if (network == null || client == null) shown.remove(owner) else shown[owner] = NetworkBarSections(network, client, onHome)
    }

    /** The sections taking over the bar on [page], if any. */
    fun on(page: BarPage): NetworkBarSections? = NetworkBarRules.owner(page)?.let { shown[it] }?.takeIf { it.active }
}

/** Home's bottom-bar takeover, from Home's body to the pages that fill it; null outside Home. */
internal val LocalNetworkBarTakeover = staticCompositionLocalOf<NetworkBarTakeover?> { null }
