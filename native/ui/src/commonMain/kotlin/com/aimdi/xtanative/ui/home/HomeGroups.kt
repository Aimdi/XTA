package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedStrip

/** The groups the account menu lists (XTA's drawer groups). */
object DrawerGroups {
    /**
     * Every group once the app has a screen for a group; until then only
     * [withFeeds], the groups with a tab of their own, since a tap could open
     * nothing else.
     */
    fun listed(all: List<FollowGroup>, withFeeds: List<FollowGroup>, groupScreen: Boolean): List<FollowGroup> =
        FollowGroup.ordered(if (groupScreen) all else withFeeds)

    /** The groups whose name holds [query], the pinned ones first, each part in the order given. */
    fun matching(groups: List<FollowGroup>, query: String): List<FollowGroup> {
        val needle = query.trim().lowercase()
        val matches = groups.filter { needle in it.name.lowercase() }
        return matches.filter { it.pinned } + matches.filterNot { it.pinned }
    }
}

/** A network a plugin offers for Home's strip: XTA's plugin id, and its name in the reader's language. */
data class StripNetwork(val id: String, val title: String)

/** The strip's sheet (XTA's feed strip add sheet): the networks on the strip, and those that could join it. */
object StripPins {
    private fun pinnable(available: List<StripNetwork>) = available.filter { it.id != FeedStrip.FOLLOWING && it.id != FeedStrip.FOR_YOU }

    /** The pinned networks that can show, in pin order. */
    fun pinned(pins: List<String>, available: List<StripNetwork>): List<StripNetwork> {
        val byId = pinnable(available).associateBy { it.id }
        return FeedStrip.visible(pins) { it in byId }.mapNotNull(byId::get)
    }

    /** The networks that could be added, in the plugins' order. */
    fun candidates(pins: List<String>, available: List<StripNetwork>): List<StripNetwork> {
        val byId = pinnable(available).associateBy { it.id }
        return FeedStrip.candidates(byId.keys.toList(), pins) { true }.mapNotNull(byId::get)
    }

    /**
     * The pinned network at [from] moved to [to] (its slot once taken out), as
     * XTA reorders them: the strip keeps the networks it shows, in their new order.
     */
    fun moved(pins: List<String>, available: List<StripNetwork>, from: Int, to: Int): List<String> =
        FeedStrip.reorder(pinned(pins, available).map { it.id }, from, to)
}
