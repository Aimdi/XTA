package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruMetatags
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.BooruTagOperator
import com.aimdi.xtanative.networks.booru.BooruTagSuggestion
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The tag field and the search it ran (booru_search_store.dart's BooruSearchState). */
internal data class BooruSearchState(
    /** Finished tags, shown as chips. */
    val tags: List<String> = emptyList(),
    /** The tag still being typed. */
    val input: String = "",
    val suggestions: List<BooruTagSuggestion> = emptyList(),
    /** Kinds learned from suggestions, to colour the chips. */
    val categories: Map<String, BooruTagCategory> = emptyMap(),
    /** The query whose results are loaded; null before the first search. */
    val query: String? = null,
    /** Whether the field is open over the results. */
    val editing: Boolean = true,
) {
    val showsResults: Boolean get() = query != null && !editing

    /** Everything in the field, the tag still being typed included. */
    val text: String get() = BooruQuery.text(BooruQuery.append(tags, BooruQuery.tokens(input)))
}

/** One search's results: its posts, and the tags the host relates to it (null until it answered). */
internal class BooruResults(val query: String, val tags: List<String>, val feed: BooruFeed, val related: StateFlow<List<String>?>)

/**
 * The tag field, its suggestions and the searches it runs. Every search that
 * runs is remembered by [remember], whichever way it was started; [results]
 * makes the posts and related tags of a search, which run in [scope]. Tags are
 * suggested by [suggest] (the host's, after [DEBOUNCE_MILLIS]) and
 * [metatags] (the engine's, at once).
 */
internal class BooruSearch(
    private val scope: CoroutineScope,
    private val suggest: suspend (String) -> List<BooruTagSuggestion>,
    private val metatags: (String) -> List<BooruTagSuggestion>,
    private val remember: (String) -> Unit,
    private val results: (query: String, tags: List<String>) -> BooruResults,
    initialQuery: String = "",
) {
    private val mutable = MutableStateFlow(BooruSearchState(tags = BooruQuery.tokens(initialQuery), editing = initialQuery.isBlank()))
    val state: StateFlow<BooruSearchState> = mutable.asStateFlow()

    private val shown = MutableStateFlow<BooruResults?>(null)

    /** The latest search's results; null before the first. */
    val current: StateFlow<BooruResults?> = shown.asStateFlow()

    private var suggesting: Job? = null

    /** Takes the field's text: finished tags become chips. Returns what stays in the field. */
    fun type(text: String): String {
        val split = BooruQuery.splitInput(text)
        mutable.update { it.copy(tags = BooruQuery.append(it.tags, split.done), input = split.rest, editing = true) }
        suggestFor(split.rest)
        return split.rest
    }

    fun addTags(tokens: Iterable<String>) = mutable.update { it.copy(tags = BooruQuery.append(it.tags, tokens)) }

    /** Empties the field. */
    fun clear() {
        suggesting?.cancel()
        mutable.update { it.copy(tags = emptyList(), input = "", suggestions = emptyList(), editing = true) }
    }

    fun removeTag(tag: String) = mutable.update { state -> state.copy(tags = state.tags.filterNot { it == tag }) }

    /** Backspace in an empty field: false when there was no chip to remove. */
    fun removeLast(): Boolean {
        val now = mutable.value
        if (now.input.isNotEmpty() || now.tags.isEmpty()) return false
        mutable.update { it.copy(tags = it.tags.dropLast(1)) }
        return true
    }

    /** Moves [tag] back into the field to be edited. Returns the field's text. */
    fun reopen(tag: String): String {
        mutable.update { state ->
            val kept = BooruQuery.append(state.tags, BooruQuery.tokens(state.input))
            state.copy(tags = kept.filterNot { it == tag }, input = tag, editing = true)
        }
        suggestFor(tag)
        return tag
    }

    /**
     * Adds a suggestion as a chip in place of the typed text, keeping a `-` or
     * `~` typed before it. A metatag's name stays in the field instead, to be
     * given its value. Returns what stays in the field.
     */
    fun pick(suggestion: BooruTagSuggestion): String {
        suggesting?.cancel()
        val token = BooruQuery.tokenFor(mutable.value.input, suggestion.name)
        if (BooruMetatags.suggestsName(suggestion)) {
            mutable.update { it.copy(input = token, editing = true) }
            suggestFor(token)
            return token
        }
        mutable.update { state ->
            val kinds = suggestion.category?.let { state.categories + (suggestion.name to it) } ?: state.categories
            state.copy(tags = BooruQuery.append(state.tags, listOf(token)), input = "", suggestions = emptyList(), categories = kinds)
        }
        return ""
    }

    /** [token] at the end of what is typed, as a metatag tapped under the field starts one. Returns the field's text. */
    fun start(token: String): String = type("${mutable.value.input} $token".trimStart())

    /** [operator] put in front of what is typed, or taken off. Returns the field's text. */
    fun toggleOperator(operator: BooruTagOperator): String = type(BooruQuery.toggleOperator(mutable.value.input, operator))

    fun edit() {
        if (!mutable.value.editing) mutable.update { it.copy(editing = true) }
    }

    /** Back to the results of the last search, dropping edits not searched: false when there are none to go back to. */
    fun closeEditor(): Boolean {
        val query = mutable.value.query
        if (query == null || !mutable.value.editing) return false
        suggesting?.cancel()
        mutable.update { it.copy(tags = BooruQuery.tokens(query), input = "", suggestions = emptyList(), editing = false) }
        return true
    }

    /** Replaces the field with [query] and runs it. */
    fun run(query: String) {
        mutable.update { it.copy(tags = BooruQuery.append(emptyList(), BooruQuery.tokens(query)), input = "") }
        search()
    }

    /** Adds [token] to the current search and runs it again. */
    fun refine(token: String) {
        addTags(listOf(token))
        search()
    }

    fun search() {
        val tags = BooruQuery.append(mutable.value.tags, BooruQuery.tokens(mutable.value.input))
        if (tags.isEmpty()) return
        val query = BooruQuery.text(tags)
        suggesting?.cancel()
        // The previous results may still be loading: closed, they ask for no more pages.
        shown.value?.feed?.close()
        val next = results(query, tags)
        shown.value = next
        mutable.update { it.copy(tags = tags, input = "", suggestions = emptyList(), query = query, editing = false) }
        remember(query)
        next.feed.refresh()
    }

    /** Stops what the search runs. */
    fun close() {
        suggesting?.cancel()
        shown.value?.feed?.close()
    }

    /**
     * The host's tags for what is typed, then the metatags of the engine that
     * match it. Those show at once; the host's suggestions for the previous
     * text stay until its answer for this one arrives.
     */
    private fun suggestFor(input: String) {
        suggesting?.cancel()
        val engine = metatags(input)
        val prefix = BooruQuery.suggestionPrefix(input)
        if (prefix == null || engine.isNotEmpty()) show(engine)
        if (prefix == null) return
        suggesting = scope.launch {
            delay(DEBOUNCE_MILLIS)
            val found = try {
                suggest(prefix)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emptyList()
            }
            if (mutable.value.input == input) mutable.update { it.copy(suggestions = found + engine) }
        }
    }

    private fun show(suggestions: List<BooruTagSuggestion>) {
        if (suggestions.isNotEmpty() || mutable.value.suggestions.isNotEmpty()) mutable.update { it.copy(suggestions = suggestions) }
    }

    companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}
