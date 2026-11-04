package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.common.TabSwipes
import com.aimdi.xtanative.ui.settings.HomePages

/** One page of Home's bottom bar (home_screen.dart's NavigationPage), by the id Settings › Home keeps it under. */
sealed interface BarPage {
    val id: String

    /** The timelines: the strip, under Following's bar or a timeline's own. */
    data object Feed : BarPage {
        override val id: String = HomeBar.FEED
    }

    data object Subscriptions : BarPage {
        override val id: String = HomeBar.SUBSCRIPTIONS
    }

    /** Discover, which XTA keeps under its old id. */
    data object Trending : BarPage {
        override val id: String = HomeBar.TRENDING
    }

    data object Saved : BarPage {
        override val id: String = HomeBar.SAVED
    }

    /** A network's page, by XTA's plugin id. */
    data class Network(override val id: String) : BarPage

    data class Group(val groupId: String) : BarPage {
        override val id: String get() = HomePages.GROUP_PREFIX + groupId
    }
}

/** One place on the bar (network_switcher.dart's BottomBarSlot): a page, or Networks, which holds the network pages it has no room for. */
sealed interface BarSlot {
    data class Page(val index: Int) : BarSlot

    data object Networks : BarSlot
}

/** Home's bottom bar as XTA lays it out (home_screen.dart, home_model.dart, network_switcher.dart). */
object HomeBar {
    const val FEED = "feed"
    const val SUBSCRIPTIONS = "subscriptions"
    const val TRENDING = "trending"
    const val SAVED = "saved"

    /** A bar holds at least two pages (pagesForNavigationBar). */
    const val MINIMUM = HomePages.MINIMUM

    /** Network pages the bar shows before the rest go behind Networks (kBottomBarRecentPlugins). */
    const val NETWORK_SLOTS = 1

    /** How far a swipe across the bar must go, and how fast a flick, to turn its page (reader_swipe_navigation.dart), in dp and dp/s. */
    const val SWIPE_DISTANCE = TabSwipes.DISTANCE
    const val SWIPE_MINIMUM = TabSwipes.MINIMUM
    const val FLICK_VELOCITY = TabSwipes.FLICK

    val DEFAULT_PAGES: List<BarPage> = listOf(BarPage.Feed, BarPage.Subscriptions, BarPage.Trending, BarPage.Saved)

    /** The page [id] names, when Home can show it: one of its four, a group that exists, or a network it can show ([networks]). */
    fun pageOf(id: String, networks: (String) -> Boolean, groups: Set<String>): BarPage? = when (id) {
        FEED -> BarPage.Feed
        SUBSCRIPTIONS -> BarPage.Subscriptions
        TRENDING -> BarPage.Trending
        SAVED -> BarPage.Saved
        else -> when (val group = HomePages.groupOf(id)) {
            null -> BarPage.Network(id).takeIf { networks(id) }
            else -> BarPage.Group(group).takeIf { group in groups }
        }
    }

    /**
     * The pages the reader put on the bar ([ids], in Settings › Home's
     * order) that Home can show; fewer than two, and the bar is Home's four,
     * as XTA's bar never builds with less.
     */
    fun pages(ids: List<String>, networks: (String) -> Boolean, groups: Set<String>): List<BarPage> {
        val pages = ids.distinct().mapNotNull { pageOf(it, networks, groups) }
        return if (pages.size < MINIMUM) DEFAULT_PAGES else pages
    }

    /** Where Home opens (_initialPageOf): the page XTA's initial page names when it is on the bar, else the first. */
    fun initialIndex(pages: List<BarPage>, initialTab: String): Int = pages.indexOfFirst { it.id == initialTab.trim() }.coerceAtLeast(0)

    /** Whether [initialTab] is one of the bar's pages, which the strip then leaves to the bar. */
    fun opensOnBar(pages: List<BarPage>, initialTab: String): Boolean = pages.any { it.id == initialTab.trim() }

    /**
     * The bar's places (layoutBottomBar): every page that is not a network,
     * then the networks; with more networks than [NETWORK_SLOTS], the one
     * opened last ([recent], newest first) stands for them, then Networks.
     */
    fun slots(pages: List<BarPage>, recent: List<String>): List<BarSlot> {
        val (networks, others) = pages.indices.partition { pages[it] is BarPage.Network }
        val first = others.map { BarSlot.Page(it) }
        if (networks.size <= NETWORK_SLOTS) return first + networks.map { BarSlot.Page(it) }
        val kept = recent.firstNotNullOfOrNull { id -> networks.firstOrNull { pages[it].id == id } } ?: networks.first()
        return first + BarSlot.Page(kept) + BarSlot.Networks
    }

    /** The place that lights up for page [page] (destinationIndexForPage): its own, else Networks, which holds it. */
    fun slotOf(slots: List<BarSlot>, page: Int): Int {
        val own = slots.indexOfFirst { it is BarSlot.Page && it.index == page }
        return if (own >= 0) own else slots.indexOf(BarSlot.Networks).coerceAtLeast(0)
    }

    /** The bar's network pages, by id, in its order. */
    fun networks(pages: List<BarPage>): List<String> = pages.filterIsInstance<BarPage.Network>().map { it.id }

    /** The Networks sheet's two sections: those opened lately ([recent], newest first), then the rest in the bar's order. */
    fun networkSections(networks: List<String>, recent: List<String>): Pair<List<String>, List<String>> {
        val lately = recent.distinct().filter { it in networks }
        return lately to networks.filter { it !in lately }
    }

    /**
     * The page a swipe across the bar ends on (pageAfterNavigationSwipe):
     * [distance] dp travelled (left negative) and [velocity] dp/s at release.
     * A swipe must go [SWIPE_DISTANCE], or be flicked, and agree with the
     * way it travelled; the pages do not wrap.
     */
    fun swiped(current: Int, count: Int, distance: Float, velocity: Float, rtl: Boolean = false): Int =
        TabSwipes.turned(current, count, distance, velocity, rtl)
}

/**
 * Whether Home's bars are on screen as the reader scrolls (home_navigation_visibility.dart):
 * tied to the finger, each pixel scrolled down slides them a pixel further
 * away, each pixel up a pixel back. When the scroll ends, bars caught halfway
 * settle: past [hideAfter] down they finish leaving, past [showAfter] up they
 * finish coming back, and less springs back. Both are pixels; so is travel.
 */
class BarHiding(private val hideAfter: Float, private val showAfter: Float) {
    /** How far away the bars are: 0 on screen, 1 gone. */
    var hidden: Float = 0f
        private set

    private var movingDown = false

    /** The content moved [delta] pixels (toward its end positive), with the bars [travel] pixels tall. */
    fun scrolled(delta: Float, travel: Float) {
        if (delta == 0f) return
        movingDown = delta > 0
        hidden = followed(hidden, delta, travel)
    }

    /** At the top, on a page change, on a tap of the bar: on screen at once. */
    fun shown() {
        hidden = 0f
    }

    /** Where the bars come to rest now the scroll ended: where they are, unless caught halfway. */
    fun resting(travel: Float): Float = when {
        hidden <= 0f || hidden >= 1f -> hidden
        restsHidden(hidden * travel, travel, movingDown) -> 1f
        else -> 0f
    }

    /** The bars set to [value] by an animation toward where they rest. */
    fun moved(value: Float) {
        hidden = value.coerceIn(0f, 1f)
    }

    /** Whether bars [hiddenBy] pixels out of [travel] rest away. */
    fun restsHidden(hiddenBy: Float, travel: Float, movingDown: Boolean): Boolean =
        if (movingDown) hiddenBy >= hideAfter else travel - hiddenBy < showAfter

    companion object {
        /** XTA's hideAfter and showAfter, in dp. */
        const val HIDE_AFTER_DP = 24f
        const val SHOW_AFTER_DP = 12f

        /** Where bars [hidden] away are after the content moved [delta] pixels, with [travel] to go. */
        fun followed(hidden: Float, delta: Float, travel: Float): Float = if (travel <= 0f) hidden else (hidden + delta / travel).coerceIn(0f, 1f)
    }
}
