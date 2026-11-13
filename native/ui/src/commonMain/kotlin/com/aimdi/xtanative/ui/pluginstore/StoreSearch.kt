package com.aimdi.xtanative.ui.pluginstore

/**
 * The store's "Search plugins" (_plugin_store.dart, plugin_brand.dart's
 * pluginMatchesStoreQuery): a plugin matches by its id, its id without the
 * query's spaces, its name's initials, its name, its description or its
 * category, ignoring case; an empty query keeps every plugin.
 */
internal object StoreSearch {
    fun matches(query: String, id: String, title: String, description: String, category: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val compact = q.replace(Regex("\\s+"), "")
        val ids = id.lowercase()
        val initials = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1).lowercase() }
        return ids.contains(q) || ids.contains(compact) || initials == q ||
            title.lowercase().contains(q) || description.lowercase().contains(q) || category.lowercase().contains(q)
    }

    /**
     * Whether the store shows "Group microblogging services" (_plugin_store.dart's
     * showGrouping): while nothing is typed, or when its own words, [rowWords],
     * hold the query, ignoring case.
     */
    fun showsGrouping(query: String, rowWords: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || rowWords.lowercase().contains(q)
    }

    /** Whether the store says "No results": a query was typed and neither section, nor the [grouping] row, has anything left. */
    fun noResults(query: String, installed: Int, offered: Int, grouping: Boolean = false): Boolean =
        !grouping && query.isNotBlank() && installed == 0 && offered == 0
}
