package com.aimdi.xtanative.store.search

import com.aimdi.xtanative.store.settings.SearchPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.strictJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Recent searches per network (`x`, `mastodon`, `reddit`…), newest first, as
 * XTA keeps them: at most [PER_SCOPE] a network, each at most [MAX_LENGTH]
 * characters, and a search typed again in other capitals replaces the old one.
 */
data class RecentSearchHistory(val byScope: Map<String, List<String>> = emptyMap()) {
    operator fun get(scope: String): List<String> = byScope[scope].orEmpty()

    /** [typed], trimmed, first in [scope]; nothing changes for an empty or overlong search. */
    fun remember(scope: String, typed: String): RecentSearchHistory {
        val query = typed.trim()
        if (query.isEmpty() || query.length > MAX_LENGTH) return this
        return withScope(scope, (listOf(query) + this[scope].filterNot { it.lowercase() == query.lowercase() }).take(PER_SCOPE))
    }

    fun remove(scope: String, query: String): RecentSearchHistory = withScope(scope, this[scope].filterNot { it == query })

    /** [scope] emptied; the others stay. */
    fun clear(scope: String): RecentSearchHistory = withScope(scope, emptyList())

    /** The JSON XTA keeps under its preference. */
    fun encode(): String = JsonObject(byScope.mapValues { (_, queries) -> JsonArray(queries.map(::JsonPrimitive)) }).toString()

    private fun withScope(scope: String, queries: List<String>) = copy(byScope = byScope + (scope to queries))

    companion object {
        const val PER_SCOPE = 10
        const val MAX_LENGTH = 200

        /** Networks read back; what is stored past them is ignored, as XTA ignores it. */
        const val MAX_SCOPES = 24

        /** A stored history longer than this is not read at all. */
        const val MAX_STORED = 100_000

        /** The history in [raw], read as XTA reads it; anything damaged reads as no history. */
        fun parse(raw: String?): RecentSearchHistory {
            val root = raw?.takeIf { it.length <= MAX_STORED }?.let(::strictJson) as? JsonObject ?: return RecentSearchHistory()
            val scopes = root.entries.take(MAX_SCOPES).mapNotNull { (scope, value) ->
                (value as? JsonArray)?.let { list ->
                    scope to list.mapNotNull { (it as? JsonPrimitive)?.takeIf { item -> item.isString }?.content }
                        .filter { it.isNotEmpty() && it.length <= MAX_LENGTH }
                        .take(PER_SCOPE)
                }
            }
            return RecentSearchHistory(scopes.toMap())
        }
    }
}

/**
 * The recent searches of every network, kept where XTA keeps them: in its
 * settings, so they travel in a backup's settings section.
 */
class RecentSearches(private val settings: SettingsStore) {
    /** The whole history, then each change. */
    val history: Flow<RecentSearchHistory> = settings.observe(SearchPrefs.recent).map(RecentSearchHistory::parse)

    /** [scope]'s searches, newest first, then each change. */
    fun of(scope: String): Flow<List<String>> = history.map { it[scope] }.distinctUntilChanged()

    suspend fun remember(scope: String, query: String) = change { it.remember(scope, query) }

    suspend fun remove(scope: String, query: String) = change { it.remove(scope, query) }

    suspend fun clear(scope: String) = change { it.clear(scope) }

    private suspend fun change(apply: (RecentSearchHistory) -> RecentSearchHistory) {
        settings.update { current ->
            val before = RecentSearchHistory.parse(current[SearchPrefs.recent])
            val after = apply(before)
            if (after == before) current else current.with(SearchPrefs.recent, after.encode())
        }
    }
}
