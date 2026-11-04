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
     * for the first), so both are in view. The refresh line and the
     * unreachable notice, when shown, come before that header.
     */
    fun afterInstall(position: Int, refreshing: Boolean, unreachable: Boolean): Int = listOf(refreshing, unreachable).count { it } + position
}
