package com.aimdi.xtanative.ui.pluginstore

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

    /** Under this width a row's buttons go under its name (kPluginStoreStackBreakpoint). */
    const val STACK_BELOW = 420f

    /** From this text scale on they do too. */
    const val STACK_FROM_SCALE = 1.3f

    /** Whether a row [width] dp wide, at text scale [fontScale], puts its buttons under its name. */
    fun stacks(width: Float, fontScale: Float): Boolean = width < STACK_BELOW || fontScale >= STACK_FROM_SCALE

    fun afterInstall(position: Int, refreshing: Boolean, unreachable: Boolean, searchField: Boolean = false): Int =
        listOf(searchField, refreshing, unreachable).count { it } + position
}
