package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.XtaPrefs

/** One page Settings › Home offers for the bottom bar, and whether it is on it. */
data class HomePage(val id: String, val selected: Boolean)

/**
 * Settings › Home (home_model.dart): the bottom bar's pages, in the
 * reader's order, the ones on the bar ticked. The pages are Home's own four,
 * each network that is on and shows a tab, and each group (`group-<id>`).
 */
object HomePages {
    val DEFAULTS = listOf("feed", "subscriptions", "trending", "saved")
    const val GROUP_PREFIX = "group-"

    /** At least this many pages stay on the bar. */
    const val MINIMUM = 2

    /** What Home's pages are now and what loading them writes back. */
    data class Loaded(val pages: List<HomePage>, val saved: List<String>, val seeded: List<String>)

    /** Every page there could be: Home's four, the networks with a tab, then the groups. */
    fun available(settings: Settings, groupIds: List<String>): List<String> {
        val networks = XtaPrefs.networks.filter { network -> network.showTab?.let { settings[it] } == true && settings[network.enabled] }
        return DEFAULTS + networks.map { it.id } + groupIds.map { GROUP_PREFIX + it }
    }

    /**
     * The pages in the order the reader saved, then the rest. A network not
     * yet offered to the bar is put on it once, and remembered, so removing
     * it sticks; groups never are. A bar left with nothing ticked gets Home's
     * four back.
     */
    fun load(settings: Settings, groupIds: List<String>): Loaded {
        val saved = settings[HomePrefs.pages]
        val available = available(settings, groupIds)
        val seeded = settings[HomePrefs.seededNetworkTabs]
        val chosen = saved.filter { it in available }.distinct().map { HomePage(it, true) }
        val offered = available.filter { it !in saved }
        val newlySeeded = offered.filter { id -> NetworkSections.of(id) != null && id !in seeded }
        val rest = offered.map { HomePage(it, it in newlySeeded) }
        val pages = (chosen + rest).let { pages ->
            if (pages.none { it.selected }) pages.map { it.copy(selected = it.id in DEFAULTS) } else pages
        }
        return Loaded(pages, selectedIds(pages), seeded + newlySeeded)
    }

    /** [pages] with [id] ticked or not; null when that would leave fewer than [MINIMUM] on the bar. */
    fun toggled(pages: List<HomePage>, id: String, selected: Boolean): List<HomePage>? {
        if (!selected && pages.count { it.selected } <= MINIMUM && pages.any { it.id == id && it.selected }) return null
        return pages.map { if (it.id == id) it.copy(selected = selected) else it }
    }

    /** [pages] with the page at [from] moved to [to]. */
    fun moved(pages: List<HomePage>, from: Int, to: Int): List<HomePage> {
        if (from !in pages.indices || to !in pages.indices || from == to) return pages
        val next = pages.toMutableList()
        next.add(to, next.removeAt(from))
        return next
    }

    /** What the bar holds, in order. */
    fun selectedIds(pages: List<HomePage>): List<String> = pages.filter { it.selected }.map { it.id }

    /** The group a page stands for, or null. */
    fun groupOf(id: String): String? = id.removePrefix(GROUP_PREFIX).takeIf { id.startsWith(GROUP_PREFIX) }
}
