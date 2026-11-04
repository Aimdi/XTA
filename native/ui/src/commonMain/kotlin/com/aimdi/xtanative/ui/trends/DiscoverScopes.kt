package com.aimdi.xtanative.ui.trends

import com.aimdi.xtanative.ui.search.SEARCH_SCOPE_X

/** What Discover's body shows: X's trends, or a plugin's search before (its note, or its landing) and after something is typed. */
enum class DiscoverBody { X_TRENDS, PLUGIN_EMPTY, PLUGIN_LANDING, PLUGIN_SEARCH }

/**
 * Which network Discover searches (search_scope.dart, _search_scope.dart):
 * X, or one of the installed plugins that search. A scope is X or a plugin's id.
 */
object DiscoverScopes {
    /** [selected] while it is X or a plugin that still searches ([searchable], by id); X once that plugin is gone. */
    fun effective(selected: String, searchable: List<String>): String =
        if (selected == SEARCH_SCOPE_X || selected in searchable) selected else SEARCH_SCOPE_X

    /** The chips, by scope: X, then each plugin that searches; none while no plugin does, so Discover with X alone looks as it did. */
    fun chips(searchable: List<String>): List<String> = if (searchable.isEmpty()) emptyList() else listOf(SEARCH_SCOPE_X) + searchable.distinct()

    /**
     * What the body shows in [scope] for the [query] the bar settled on: with
     * nothing typed, the plugin's landing where it [lands] there (XTA's
     * Mastodon, discover_plugin_search.dart), else what it searches.
     */
    fun body(scope: String, query: String, lands: Boolean = false): DiscoverBody = when {
        scope == SEARCH_SCOPE_X -> DiscoverBody.X_TRENDS
        query.isNotBlank() -> DiscoverBody.PLUGIN_SEARCH
        lands -> DiscoverBody.PLUGIN_LANDING
        else -> DiscoverBody.PLUGIN_EMPTY
    }

    /** Whether the bar's search key opens X's search; in a plugin's scope its results show below the bar instead. */
    fun opensXSearch(scope: String): Boolean = scope == SEARCH_SCOPE_X
}
