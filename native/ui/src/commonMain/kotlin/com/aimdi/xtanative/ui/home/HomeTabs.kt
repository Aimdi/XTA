package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedStrip

/**
 * One tab of Home's strip (XTA's feed strip, with the group pages XTA's bar
 * can hold beside it): [key] tells the tabs apart and, for the X feeds, names
 * the feed's own state.
 */
sealed interface HomeTab {
    val key: String

    data object Following : HomeTab {
        override val key: String = FeedKeys.FOLLOWING
    }

    /** X's home timeline, XTA's `x` source. */
    data object ForYou : HomeTab {
        override val key: String = FeedKeys.FOR_YOU
    }

    /** A network a plugin put on the strip, by XTA's plugin id. */
    data class Network(val id: String) : HomeTab {
        override val key: String get() = NETWORK + id
    }

    data class Group(val id: String, val label: String) : HomeTab {
        override val key: String get() = GROUP + id
    }

    companion object {
        const val NETWORK = "network:"
        const val GROUP = "group:"
    }
}

/** What Home's strip holds and where it opens, as XTA's home decides it. */
object HomeStrip {
    /** How many networks "recently opened" keeps (XTA's kNetworkRecentsLimit). */
    const val RECENT_LIMIT = 12

    /** XTA's older name of For you in its default feed setting. */
    private const val LEGACY_FOR_YOU = "foryou"

    /** XTA's initial page for its home feed, and the prefix of a group's page. */
    private const val FEED_PAGE = "feed"
    private const val GROUP_PAGE = "group-"

    /**
     * Following and For you, the pinned networks that can show ([usable]) in
     * pin order, then [groups]: the groups with someone to read, in XTA's order.
     */
    fun tabs(pins: List<String>, usable: (String) -> Boolean, groups: List<FollowGroup>): List<HomeTab> =
        FeedStrip.tabs(pins, usable).map(::sourceTab) + groups.map { HomeTab.Group(it.id, groupLabel(it)) }

    private fun sourceTab(id: String): HomeTab = when (id) {
        FeedStrip.FOLLOWING -> HomeTab.Following
        FeedStrip.FOR_YOU -> HomeTab.ForYou
        else -> HomeTab.Network(id)
    }

    /** A group as its tab names it: its emoji, then its name. */
    fun groupLabel(group: FollowGroup): String = listOfNotNull(group.emoji?.takeIf { it.isNotBlank() }, group.name).joinToString(" ")

    /** The tab XTA's default feed names: `following`, `x` (or the older `foryou`), or a network's id. */
    fun feedKey(defaultFeed: String): String = when (val id = defaultFeed.trim()) {
        "", FeedStrip.FOLLOWING -> HomeTab.Following.key
        FeedStrip.FOR_YOU, LEGACY_FOR_YOU -> HomeTab.ForYou.key
        else -> HomeTab.Network(id).key
    }

    /** The tab XTA's initial page names when it is one of the strip's: a group's page, or a network's. */
    fun pageKey(initialPage: String): String? {
        val page = initialPage.trim()
        return when {
            page.startsWith(GROUP_PAGE) -> HomeTab.GROUP + page.removePrefix(GROUP_PAGE)
            page.isEmpty() || page == FEED_PAGE || startRoute(page) != null -> null
            else -> HomeTab.Network(page).key
        }
    }

    /**
     * Where Home opens: on the tab XTA's initial page names, else on its
     * default feed, else on Following.
     */
    fun initialIndex(tabs: List<HomeTab>, initialPage: String, defaultFeed: String): Int {
        val keys = tabs.map { it.key }
        return listOfNotNull(pageKey(initialPage), feedKey(defaultFeed)).firstNotNullOfOrNull { key -> keys.indexOf(key).takeIf { it >= 0 } } ?: 0
    }

    /** The screen XTA's initial page opens over Home: its bar's pages that are screens of their own here. */
    fun startRoute(initialPage: String): String? = when (initialPage.trim()) {
        "subscriptions" -> HomeDestination.SUBSCRIPTIONS.route
        "trending" -> HomeDestination.TRENDS.route
        "saved" -> HomeDestination.SAVED.route
        else -> null
    }

    /** Where the tab [key] sits now; Following's place when it went, as XTA falls back to Following. */
    fun indexOf(tabs: List<HomeTab>, key: String): Int = tabs.indexOfFirst { it.key == key }.coerceAtLeast(0)

    /** [recent] with [id] first, each once, at most [RECENT_LIMIT]: the networks opened, newest first. */
    fun touched(recent: List<String>, id: String): List<String> =
        if (id.isEmpty()) recent else (listOf(id) + recent.filter { it != id }).take(RECENT_LIMIT)
}
