package com.aimdi.xtanative.ui.plugins

/**
 * The searches a plugin's reader ran, newest first, as XTA keeps them
 * (plugin_search_history.dart): at most [CAP], one entry per [identity] of a
 * query (by default its own text). The list itself is the plugin's
 * preference, such as `plugin.<id>.search_history`.
 */
object PluginSearchHistory {
    const val CAP = 20

    /** [query], trimmed, first, in place of the older entries it is the same search as; a blank query changes nothing. */
    fun remembered(history: List<String>, query: String, identity: (String) -> String = { it }): List<String> {
        val value = query.trim()
        if (value.isEmpty()) return history
        val id = identity(value)
        return (listOf(value) + history.filter { identity(it) != id }).take(CAP)
    }

    /** [history] without [query]. */
    fun forgotten(history: List<String>, query: String): List<String> = history.filterNot { it == query }
}
