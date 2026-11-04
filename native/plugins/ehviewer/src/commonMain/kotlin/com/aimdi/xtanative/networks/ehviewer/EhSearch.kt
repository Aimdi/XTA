package com.aimdi.xtanative.networks.ehviewer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** A search's filters, the search whose results are shown (null before the first) and the tags suggested for the term being typed. */
data class EhSearchState(
    val filters: EhSearchFilters,
    val query: String? = null,
    val suggestions: List<EhTag> = emptyList(),
)

/**
 * A search as it is typed and run: the site's tag suggestions for the term
 * being typed, the filters, and the search they run. A changed filter runs
 * the shown search again under it, without the caller waiting for it.
 *
 * Every search is handed to [remember], where the search history is kept.
 * Suggestions are asked for [debounceMillis] after the last keystroke. The
 * work runs on [scope]'s dispatcher and ends with it, or with [close].
 */
class EhSearch(
    private val client: EhClient,
    scope: CoroutineScope,
    private val remember: suspend (String) -> Unit = {},
    private val debounceMillis: Long = 300,
) {
    private val work = EhWork(scope)
    private val mutableState = MutableStateFlow(EhSearchState(EhSearchFilters(client.current.categories)))
    val state: StateFlow<EhSearchState> = mutableState.asStateFlow()

    /** The galleries the shown search found, read page by page; null before the first search. */
    @Volatile
    var results: EhFeed? = null
        private set

    @Volatile
    private var typed = ""

    @Volatile
    private var suggested: EhSuggestionTerm? = null

    @Volatile
    private var suggesting: Job? = null

    @Volatile
    private var closed = false

    /** The query as typed so far: suggestions follow it. */
    fun type(text: String) {
        typed = text
        suggesting?.cancel()
        val terms = EhQuery.suggestionTerms(text)
        if (terms.isEmpty()) {
            mutableState.update { if (it.suggestions.isEmpty()) it else it.copy(suggestions = emptyList()) }
            return
        }
        suggesting = work.scope.launch {
            delay(debounceMillis)
            suggest(text, terms)
        }
    }

    /** The longest term the site has tags for wins; an answer for text typed over since is dropped. */
    private suspend fun suggest(text: String, terms: List<EhSuggestionTerm>) {
        for (term in terms) {
            val tags = client.suggestTags(term.prefix)
            if (closed || typed != text) return
            if (tags.isNotEmpty()) {
                suggested = term
                mutableState.update { it.copy(suggestions = tags) }
                return
            }
        }
        mutableState.update { it.copy(suggestions = emptyList()) }
    }

    /** The query with [tag] in place of the term it was suggested for, ready for the next term. */
    fun pick(tag: EhTag): String {
        suggesting?.cancel()
        val term = suggested ?: EhSuggestionTerm(EhQuery.lastTermStart(typed), "")
        typed = EhQuery.insertSuggestion(typed, term, tag)
        suggested = null
        mutableState.update { it.copy(suggestions = emptyList()) }
        return typed
    }

    /** One category on or off; at least one stays on, since the site reads none as all. */
    fun toggleCategory(category: EhCategory) {
        val categories = state.value.filters.categories.let { if (category in it) it - category else it + category }
        if (categories.isNotEmpty()) filter { it.copy(categories = categories) }
    }

    /** A minimum rating; choosing the one already chosen clears it. */
    fun setMinRating(stars: Int) = filter { it.copy(minRating = if (stars == it.minRating) 0 else stars) }

    fun setLanguage(language: EhSearchLanguage) = filter { it.copy(language = language) }

    /** A changed filter runs the shown search again under it. */
    private fun filter(change: (EhSearchFilters) -> EhSearchFilters) {
        val next = mutableState.updateAndGet { it.copy(filters = change(it.filters)) }
        next.query?.let { query -> work.scope.launch { search(query) } }
    }

    /** Runs [text] under the filters as they are now, in a list of its own: a search still loading never lands in a newer one. */
    suspend fun search(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        suggesting?.cancel()
        typed = text
        val filters = state.value.filters
        val feed = EhFeed { next -> client.search(query, filters, next) }
        results = feed
        mutableState.update { it.copy(query = query, suggestions = emptyList()) }
        remember(query)
        if (!closed) feed.refresh()
    }

    /** Stops the suggestions and searches still running. */
    fun close() {
        closed = true
        work.close()
    }
}
