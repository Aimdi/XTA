package com.aimdi.xtanative.ui.pluginstore

import com.aimdi.xtanative.pluginhost.PluginStatus

/**
 * The store's list after an install. The plugin's row moves up under
 * Installed, where it shows Open; a list that keeps the row it had at its
 * top would leave it above the screen, so the list follows it.
 */
internal object StoreRows {
    /** The first plugin in [now] that [before] lacked; none on the store's first look, when [before] is null. */
    fun newlyInstalled(before: List<String>?, now: List<String>): String? = before?.let { seen -> now.firstOrNull { it !in seen } }

    /**
     * The row the list brings to its top once the plugin at [position] under
     * Installed is in: the row above the plugin's own (Installed's header,
     * for the first), so both are in view. The search field, the refresh
     * line and the unreachable notice, when shown, come before that header.
     */
    /**
     * XTA's own order of its plugins (plugin_registry.dart's builtInPlugins),
     * which its store lists them in: the catalogue is written in another, so
     * the store puts each section back in this one.
     */
    val REGISTRY: List<String> = listOf(
        "threads", "bluesky", "mastodon", "tiktok", "instagram", "reddit", "hackernews", "substack", "rss",
        "websources", "pixiv", "booru", "ehviewer", "stocks", "karakeep", "deepmarks", "immich",
    )

    /** [plugins] in XTA's order; one XTA never had comes after them, in the order it was given. */
    fun <T> ordered(plugins: List<T>, id: (T) -> String): List<T> =
        plugins.withIndex().sortedWith(compareBy({ rank(id(it.value)) }, { it.index })).map { it.value }

    private fun rank(id: String): Int = REGISTRY.indexOf(id).let { if (it < 0) REGISTRY.size else it }

    /**
     * The installed plugins as the store lays them out: one that has
     * something to say or do ([needsRow]: an update, a failure, a download
     * under way) keeps a row of its own, first; the others stand two to a
     * line as boxes. One plugin alone to box is a row across the column,
     * rather than a box beside a gap with its menu mid-page.
     */
    fun <T> slots(plugins: List<T>, needsRow: (T) -> Boolean): List<StoreSlot<T>> {
        val (rows, boxes) = plugins.partition(needsRow)
        if (boxes.size == 1) return (rows + boxes).map { StoreSlot(listOf(it), boxes = false) }
        return rows.map { StoreSlot(listOf(it), boxes = false) } + boxes.chunked(BOXES_PER_LINE).map { StoreSlot(it, boxes = true) }
    }

    /** How many installed plugins stand on one line as boxes. */
    const val BOXES_PER_LINE = 2

    /** Under this width a row's buttons go under its name (kPluginStoreStackBreakpoint). */
    const val STACK_BELOW = 420f

    /** From this text scale on they do too. */
    const val STACK_FROM_SCALE = 1.3f

    /** Whether a row [width] dp wide, at text scale [fontScale], puts its buttons under its name. */
    fun stacks(width: Float, fontScale: Float): Boolean = width < STACK_BELOW || fontScale >= STACK_FROM_SCALE

    /** Whether a plugin on offer puts its one button under its name: only with large text, whatever the width. */
    fun offerStacks(fontScale: Float): Boolean = fontScale >= STACK_FROM_SCALE

    /**
     * Whether a plugin on offer in [status] installs on a click of its row,
     * as its Install button does: not while it downloads or is put in place,
     * so a second click does nothing, nor when it needs a newer app.
     */
    fun offerInstalls(status: PluginStatus): Boolean = when (status) {
        is PluginStatus.Downloading, PluginStatus.Installing, is PluginStatus.NeedsNewerApp -> false
        else -> true
    }

    fun afterInstall(position: Int, refreshing: Boolean, unreachable: Boolean, searchField: Boolean = false): Int =
        listOf(searchField, refreshing, unreachable).count { it } + position
}

/** One line of the installed list: a plugin's own row, or up to [StoreRows.BOXES_PER_LINE] boxes. */
internal data class StoreSlot<T>(val plugins: List<T>, val boxes: Boolean)
