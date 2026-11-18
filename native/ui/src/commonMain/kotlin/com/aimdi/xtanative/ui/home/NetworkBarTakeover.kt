package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab

/**
 * Which networks' own sections take over Home's bottom bar while they are
 * shown (the unified plugin shell): three or four sections of its own make
 * the bar, and nothing else is on it; with fewer or more, a network on
 * Home's Feed has no bottom bar at all and its sections run as a feed row
 * under the top bar. XTA's own places are behind the network's button
 * (the plugin switcher) and its menu.
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

    /** The most of a network's sections the bar shows. */
    const val ON_BAR = 4

    /** The fewest that make a bar: one or two are a feed row's tabs, not destinations. */
    const val MIN_ON_BAR = 3

    /** Whether [network], with [own] sections of its own (not joining its menu), takes over the bottom bar: one of [NETWORKS], with three or four. */
    fun takesOver(network: String?, own: Int): Boolean = network in NETWORKS && own in MIN_ON_BAR..ON_BAR

    /**
     * A network's [sections] split between the bar and its menu: those that
     * join the menu ([PluginHomeTab.joinsMore]) go there, the rest are the
     * bar's ([takesOver] says whether they make one).
     */
    fun <T> split(sections: List<T>, joinsMore: (T) -> Boolean): Split<T> {
        val (own, joining) = sections.partition { !joinsMore(it) }
        return Split(own, joining)
    }

    /** Who sets a page's takeover: Home's Feed, or a network's own page on the bar; null for a page that has none. */
    fun owner(page: BarPage): String? = when (page) {
        BarPage.Feed -> FEED
        is BarPage.Network -> "page:" + page.id
        else -> null
    }

    const val FEED = "feed"
}

/** A network's sections on the bar ([bar]) and in its menu ([more]). */
internal data class Split<T>(val bar: List<T>, val more: List<T>)

/**
 * One network shown on a page of the bar: which ([network]) and what its
 * plugin hands Home's bar ([client]). [house] keeps XTA's house first on
 * the bar, for a network's own page of the bar, which has no plugin
 * switcher to lead back; [onHome] is what it does.
 */
@Stable
internal class NetworkBarSections(val network: String, val client: PluginClientBar, val onHome: (() -> Unit)? = null, val house: Boolean = true) {
    /** The network's sections, as its plugin hands them now. */
    val sections: List<PluginHomeTab> get() = client.content?.tabs?.value.orEmpty()

    /** The sections on the bar and those in the network's menu. */
    val split: Split<PluginHomeTab> get() = NetworkBarRules.split(sections) { it.joinsMore }

    /** The plugin's own colour for the section shown, if it names one. */
    val accent: Color? get() = client.content?.accent?.value

    /** Whether the sections are on the bottom bar now. */
    val active: Boolean get() = NetworkBarRules.takesOver(network, split.bar.size)

    /** Whether the plugin has handed Home its sections yet. */
    val handed: Boolean get() = client.content != null
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
    fun show(owner: String, network: String?, client: PluginClientBar?, onHome: (() -> Unit)? = null, house: Boolean = true) {
        if (network == null || client == null) shown.remove(owner) else shown[owner] = NetworkBarSections(network, client, onHome, house)
    }

    /** The sections taking over the bar on [page], if any. */
    fun on(page: BarPage): NetworkBarSections? = NetworkBarRules.owner(page)?.let { shown[it] }?.takeIf { it.active }

    /**
     * Whether [page] has no bottom bar: Home's Feed showing one of the
     * [NetworkBarRules.NETWORKS] whose sections, handed, do not make one
     * (Hacker News' lists run as its feed row). Off the Feed a network's own
     * page keeps the app's bar, the only way back from it.
     */
    fun hidesBar(page: BarPage): Boolean =
        page == BarPage.Feed && shown[NetworkBarRules.FEED]?.let { it.network in NetworkBarRules.NETWORKS && it.handed && !it.active } == true
}

/** Home's bottom-bar takeover, from Home's body to the pages that fill it; null outside Home. */
internal val LocalNetworkBarTakeover = staticCompositionLocalOf<NetworkBarTakeover?> { null }
