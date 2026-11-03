package com.aimdi.xtanative.core.feed

/**
 * The timelines on Home's strip (XTA's feed strip): Following and For you,
 * then the networks the reader pinned there, by network id, in pin order.
 * [usable] says a network is on and can sit on the strip.
 */
object FeedStrip {
    const val FOLLOWING = "following"

    /** For you: X's own home timeline, XTA's `x` network. */
    const val FOR_YOU = "x"

    /** The pins Home can show right now, each once. */
    fun visible(pins: List<String>, usable: (String) -> Boolean): List<String> = pins.distinct().filter(usable)

    /** The strip's tabs: Following and For you always first. */
    fun tabs(pins: List<String>, usable: (String) -> Boolean): List<String> =
        listOf(FOLLOWING, FOR_YOU) + visible(pins, usable).filter { it != FOR_YOU && it != FOLLOWING }

    /** Networks that could be added: on, and not on the strip yet. */
    fun candidates(networks: List<String>, pins: List<String>, usable: (String) -> Boolean): List<String> {
        val shown = visible(pins, usable).toSet()
        return networks.filter { usable(it) && it !in shown }
    }

    /** [pins] with [id] added at the end, once. */
    fun pinned(pins: List<String>, id: String): List<String> = if (id in pins) pins else pins + id

    /** [pins] without [id], as when the network is removed. */
    fun forgotten(pins: List<String>, id: String): List<String> = pins.filter { it != id }

    /** Moves one pin; [to] is its slot once taken out, as a reorderable list reports it. */
    fun reorder(pins: List<String>, from: Int, to: Int): List<String> {
        if (from == to || from !in pins.indices) return pins
        val next = pins.toMutableList()
        val id = next.removeAt(from)
        next.add(to.coerceIn(0, next.size), id)
        return next
    }

    /**
     * What an install shows once pins are hand-picked: nothing on a first
     * launch; otherwise what Home showed before, the saved pins (or every
     * enabled network when none were saved) and the networks that hid their
     * own tab and lived on Home instead.
     */
    fun migrated(saved: List<String>?, firstLaunch: Boolean, enabled: List<String>, hiddenTab: List<String>): List<String> =
        if (firstLaunch) emptyList() else ((saved ?: enabled) + hiddenTab).distinct()
}
